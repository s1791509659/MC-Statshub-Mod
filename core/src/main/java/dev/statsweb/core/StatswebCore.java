package dev.statsweb.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Platform-independent entry point shared by every platform shell.
 *
 * <p>It owns the whole life cycle: read {@code config/statsweb.json}, scan the world
 * folders once for an immediately usable snapshot, start the embedded HTTP server,
 * then rescan on a fixed delay. It never throws — a failure leaves the game server
 * untouched, which is the one hard requirement for a server-side companion mod.
 *
 * <p>Typical shell (Fabric, Paper, …):
 * <pre>{@code
 * StatswebCore core = new StatswebCore(version, new Slf4jStatswebLogger(LOG));
 * core.start(gameDir, configDir);
 * }</pre>
 */
public final class StatswebCore {

    public static final String MOD_ID = "statsweb";

    private final String version;
    private final StatswebLogger log;

    private StatswebConfig config;
    private StatsScanner scanner;
    private ApiServer apiServer;
    private ScheduledExecutorService rescan;

    public StatswebCore(String version, StatswebLogger log) {
        this.version = version;
        this.log = log;
    }

    public String version() {
        return version;
    }

    /**
     * Starts scanning + the embedded HTTP server.
     *
     * @return true when the web server is up, false when disabled by config or on failure
     */
    public boolean start(Path gameDir, Path configDir) {
        try {
            config = StatswebConfig.load(configDir.resolve("statsweb.json"), gameDir, log);
            if (!config.enabled) {
                log.info("[statsweb] disabled in config (config/statsweb.json), not starting web server.");
                return false;
            }

            scanner = new StatsScanner(config, log, version);
            scanner.scanOnce(); // initial snapshot so the API is ready immediately

            apiServer = new ApiServer(config, scanner, log, version);
            apiServer.start();

            rescan = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "statsweb-rescan");
                t.setDaemon(true);
                return t;
            });
            rescan.scheduleWithFixedDelay(scanner::scanOnce, config.refreshSeconds, config.refreshSeconds, TimeUnit.SECONDS);

            log.info("[statsweb] v{} started — web UI at http://<server-ip>:{}", version, config.port);
            // Directory candidates differ per Minecraft version — log every probe so an
            // operator can see which layout was picked up.
            log.info("[statsweb] stats dirs:       {}", summarize(config.statsDirs));
            log.info("[statsweb] advancement dirs: {}", summarize(config.advDirs));
            log.info("[statsweb] playerdata dirs:  {}", summarize(config.dataDirs));
            StatsScanner.Snapshot snap = scanner.snapshot();
            log.info("[statsweb] initial scan: {} players, {} files scanned", snap.players.size(), snap.filesScanned);
            return true;
        } catch (Exception e) {
            // Never break the game server because of this mod.
            log.error("[statsweb] failed to start — mod stays inactive", e);
            return false;
        }
    }

    /** Stops the rescan task and the HTTP server. Safe to call at any time. */
    public void stop() {
        if (rescan != null) {
            rescan.shutdownNow();
            rescan = null;
        }
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
    }

    /** Renders directory candidates as {@code path (found|missing)} for the startup log. */
    private static String summarize(List<Path> dirs) {
        StringBuilder sb = new StringBuilder();
        for (Path d : dirs) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d).append(Files.isDirectory(d) ? " (found)" : " (missing)");
        }
        return sb.toString();
    }
}
