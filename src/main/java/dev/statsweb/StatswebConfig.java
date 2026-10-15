package dev.statsweb;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Config loaded from config/statsweb.json, created with defaults on first run. */
public final class StatswebConfig {
    public final boolean enabled;
    public final int port;
    public final int refreshSeconds;
    public final String levelName;
    /** Candidate stats directories (26.1+: &lt;world&gt;/players/stats, legacy: &lt;world&gt;/stats). */
    public final List<Path> statsDirs;
    /** Candidate usercache.json locations (server root first, then world folder). */
    public final List<Path> usercachePaths;

    private StatswebConfig(boolean enabled, int port, int refreshSeconds, String levelName, List<Path> statsDirs, List<Path> usercachePaths) {
        this.enabled = enabled;
        this.port = port;
        this.refreshSeconds = refreshSeconds;
        this.levelName = levelName;
        this.statsDirs = statsDirs;
        this.usercachePaths = usercachePaths;
    }

    public static StatswebConfig load(Path configFile, Path gameDir) throws IOException {
        boolean enabled = true;
        int port = 8080;
        int refresh = 60;
        String levelName = null; // null → read from server.properties
        String overrideDir = null; // optional manual stats dir override

        if (Files.exists(configFile)) {
            try {
                JsonObject o = StatswebMod.GSON.fromJson(Files.readString(configFile, StandardCharsets.UTF_8), JsonObject.class);
                if (o != null) {
                    if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
                    if (o.has("port")) port = clamp(o.get("port").getAsInt(), 1, 65535);
                    if (o.has("refreshSeconds")) refresh = Math.max(10, o.get("refreshSeconds").getAsInt());
                    if (o.has("levelName")) levelName = o.get("levelName").getAsString();
                    if (o.has("statsDir")) overrideDir = o.get("statsDir").getAsString();
                }
            } catch (Exception e) {
                StatswebMod.LOGGER.warn("[statsweb] could not parse config, using defaults", e);
            }
        } else {
            try {
                Files.createDirectories(configFile.getParent());
                JsonObject o = new JsonObject();
                o.addProperty("enabled", enabled);
                o.addProperty("port", port);
                o.addProperty("refreshSeconds", refresh);
                o.addProperty("levelName", "");
                o.addProperty("statsDir", "");
                o.addProperty("_comment", "levelName: leave empty to auto-read from server.properties. statsDir: leave empty to auto-detect (26.1+ players/stats or legacy stats); set a path to override.");
                Files.writeString(configFile, StatswebMod.GSON.toJson(o), StandardCharsets.UTF_8);
            } catch (Exception e) {
                StatswebMod.LOGGER.warn("[statsweb] could not write default config", e);
            }
        }

        if (levelName == null || levelName.isBlank()) levelName = readLevelName(gameDir);
        Path world = gameDir.resolve(levelName);

        // Optional manual override: "statsDir" (absolute, or relative to the server root).
        List<Path> statsDirs = new ArrayList<>();
        if (overrideDir != null && !overrideDir.isBlank()) {
            Path p = Path.of(overrideDir);
            if (!p.isAbsolute()) p = gameDir.resolve(p);
            statsDirs.add(p.normalize());
        } else {
            // MC 26.1+ moved player storage into a "players" subfolder (stats -> players/stats).
            statsDirs.add(world.resolve("players").resolve("stats"));
            // Legacy layout (<= 1.21.x / pre-26.1): <world>/stats.
            statsDirs.add(world.resolve("stats"));
        }

        // usercache.json lives in the server root on dedicated servers; some setups put it in the world folder.
        List<Path> usercachePaths = List.of(gameDir.resolve("usercache.json"), world.resolve("usercache.json"));

        return new StatswebConfig(enabled, port, refresh, levelName, statsDirs, usercachePaths);
    }

    /** Reads level-name from server.properties (default "world"). */
    private static String readLevelName(Path gameDir) {
        Path props = gameDir.resolve("server.properties");
        if (Files.exists(props)) {
            try {
                Properties p = new Properties();
                try (var in = Files.newInputStream(props)) {
                    p.load(in);
                }
                String lv = p.getProperty("level-name");
                if (lv != null && !lv.isBlank()) return lv.trim();
            } catch (Exception ignored) {
            }
        }
        return "world";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
