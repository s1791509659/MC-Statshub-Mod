package dev.statsweb;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Minimal embedded HTTP server (JDK built-in, zero third-party dependencies).
 *
 * Routes:
 *   GET /              → Chinese analyzer page (jar resource /web/index.html)
 *   GET /en, /en/      → English analyzer page (jar resource /web/en/index.html)
 *   GET /api/version   → {"mod":"statsweb", ...} so pages can detect server mode
 *   GET /api/data      → full stats snapshot JSON
 */
public final class ApiServer {
    private final StatswebConfig config;
    private final StatsScanner scanner;
    private HttpServer server;

    public ApiServer(StatswebConfig config, StatsScanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", config.port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "statsweb-http");
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/", this::handlePage);
        server.createContext("/api/data", ex -> safe(ex, this::handleData));
        server.createContext("/api/version", ex -> safe(ex, ex2 -> {
            JsonObject o = new JsonObject();
            o.addProperty("mod", StatswebMod.MOD_ID);
            o.addProperty("version", StatswebMod.VERSION);
            o.addProperty("generatedAt", scanner.snapshot().generatedAt);
            sendJson(ex2, 200, o.toString());
        }));

        server.start();
    }

    private void safe(HttpExchange ex, ThrowingConsumer<HttpExchange> handler) throws IOException {
        try {
            handler.accept(ex);
        } catch (Exception e) {
            StatswebMod.LOGGER.error("[statsweb] error handling {}", ex.getRequestURI(), e);
            try {
                send(ex, 500, "text/plain; charset=utf-8", "internal error");
            } catch (Exception ignored) {
            }
        }
    }

    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    private void handlePage(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String resource;
            if (path.equals("/") || path.equals("/index.html") || path.equals("/zh")) {
                resource = "/web/index.html";
            } else if (path.equals("/en") || path.equals("/en/") || path.equals("/en/index.html")
                    || path.equals("/en_us") || path.equals("/en_us/") || path.equals("/en_us/index.html")) {
                resource = "/web/en/index.html";
            } else {
                send(ex, 404, "text/plain; charset=utf-8", "not found — try / or /en");
                return;
            }
            byte[] body = readResource(resource);
            if (body == null) {
                send(ex, 500, "text/plain; charset=utf-8", "page missing from jar");
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            send(ex, 200, body);
        } catch (Exception e) {
            StatswebMod.LOGGER.error("[statsweb] page error", e);
            send(ex, 500, "text/plain; charset=utf-8", "internal error");
        }
    }

    private void handleData(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "text/plain; charset=utf-8", "GET only");
            return;
        }
        sendJson(ex, 200, scanner.toJson(scanner.snapshot()).toString());
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        send(ex, code, json.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, String contentType, String body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        send(ex, code, body.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, byte[] body) throws IOException {
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        } catch (IOException ignored) {
            // client disconnected mid-response (proxies do this) — nothing to do
        }
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = ApiServer.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return in.readAllBytes();
        }
    }
}
