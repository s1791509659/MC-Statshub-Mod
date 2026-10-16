package dev.statsweb.paper;

import dev.statsweb.core.StatswebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

/**
 * Paper / Spigot shell for the Stats Web core.
 *
 * <p>It uses nothing but the {@link JavaPlugin} life cycle and {@code java.util.logging},
 * both of which exist on every Bukkit platform, so the exact same jar runs on Paper,
 * Spigot, Purpur and their forks. No Paper-only API is touched — the Paper API is a
 * compile-only dependency used for the base class alone.
 *
 * <p>The core engine (scanning, HTTP server, web assets) is shared with the Fabric build,
 * which is why the web dashboard behaves identically on both.
 */
public class StatswebPaperPlugin extends JavaPlugin {

    private StatswebCore core;

    @Override
    public void onEnable() {
        core = new StatswebCore(pluginVersion(), new JulStatswebLogger(getLogger()));
        boolean started = core.start(resolveServerRoot(), getDataFolder().toPath());
        if (!started) {
            getLogger().warning("[statsweb] web server is not running — see the messages above.");
        }
        // Servers stop through many paths (stop, crash, /reload of the whole server), so the
        // HTTP port is also released from a shutdown hook.
        Runtime.getRuntime().addShutdownHook(new Thread(core::stop, "statsweb-shutdown"));
    }

    /**
     * {@code Plugin#getDescription()} is deprecated on Paper (1.20.6 replaced it with
     * {@code getPluginMeta()}) but it is the only accessor that also exists on Spigot, so the
     * deprecated call is deliberate and stays the single code path.
     */
    @SuppressWarnings("deprecation")
    private String pluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.stop();
            core = null;
            getLogger().info("[statsweb] web server stopped.");
        }
    }

    /**
     * The game directory is the server root, two levels above the plugin data folder
     * ({@code <root>/plugins/StatsWeb}). {@code server.properties} is read from there by the
     * core to find the level name, so the world folders are located correctly.
     */
    private Path resolveServerRoot() {
        File dataFolder = getDataFolder();
        File pluginsDir = dataFolder.getParentFile();
        File root = pluginsDir == null ? null : pluginsDir.getParentFile();
        // Unusual layouts (custom plugin folders) fall back to the working directory,
        // which is the server root for every launcher.
        return (root == null ? new File(".") : root).getAbsoluteFile().toPath();
    }
}
