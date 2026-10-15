package dev.statsweb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Statsweb — embeds a tiny HTTP server inside the dedicated Minecraft server
 * and serves the Stats Analyzer web UI with live data from world/stats.
 *
 * Server-side only ("environment": "server" in fabric.mod.json).
 * Uses no Minecraft classes at all, only the Fabric Loader API, so it is
 * naturally resilient across game versions.
 */
public class StatswebMod implements ModInitializer {
    public static final String MOD_ID = "statsweb";
    public static final String VERSION = "1.0.1";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private StatswebConfig config;
    private StatsScanner scanner;
    private ApiServer apiServer;

    @Override
    public void onInitialize() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path configDir = FabricLoader.getInstance().getConfigDir();

            config = StatswebConfig.load(configDir.resolve("statsweb.json"), gameDir);
            if (!config.enabled) {
                LOGGER.info("[statsweb] disabled in config (config/statsweb.json), not starting web server.");
                return;
            }

            scanner = new StatsScanner(config);
            scanner.scanOnce(); // initial snapshot so the API is ready immediately

            apiServer = new ApiServer(config, scanner);
            apiServer.start();

            java.util.concurrent.ScheduledExecutorService rescan = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "statsweb-rescan");
                t.setDaemon(true);
                return t;
            });
            rescan.scheduleWithFixedDelay(scanner::scanOnce, config.refreshSeconds, config.refreshSeconds, java.util.concurrent.TimeUnit.SECONDS);

            LOGGER.info("[statsweb] v{} started — web UI at http://<server-ip>:{}", VERSION, config.port);
            for (Path d : config.statsDirs) {
                LOGGER.info("[statsweb] stats dir candidate: {}{}", d, Files.isDirectory(d) ? " (found)" : " (missing)");
            }
            StatsScanner.Snapshot snap = scanner.snapshot();
            LOGGER.info("[statsweb] initial scan: {} players, {} files scanned", snap.players.size(), snap.filesScanned);
        } catch (Exception e) {
            // Never break the Minecraft server because of the mod.
            LOGGER.error("[statsweb] failed to start — mod stays inactive", e);
        }
    }

    /** Called by the API server when pages are requested; returns a non-null snapshot (possibly empty). */
    public static JsonObject emptySnapshot(long now) {
        JsonObject o = new JsonObject();
        o.addProperty("mod", MOD_ID);
        o.addProperty("version", VERSION);
        o.addProperty("generatedAt", now);
        o.add("players", new JsonArray());
        o.add("usercache", new JsonArray());
        return o;
    }
}
