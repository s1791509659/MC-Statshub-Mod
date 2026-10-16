package dev.statsweb.core;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/** Config loaded from config/statsweb.json, created with defaults on first run. */
public final class StatswebConfig {
    public final boolean enabled;
    public final int port;
    public final int refreshSeconds;
    public final String levelName;
    /** Candidate stats directories (26.1+: &lt;world&gt;/players/stats, legacy: &lt;world&gt;/stats). */
    public final List<Path> statsDirs;
    /** Candidate advancement directories (26.1+: &lt;world&gt;/players/advancements, legacy: &lt;world&gt;/advancements). */
    public final List<Path> advDirs;
    /** Candidate playerdata directories (26.1+: &lt;world&gt;/players/data, legacy: &lt;world&gt;/playerdata). */
    public final List<Path> dataDirs;
    /** Candidate usercache.json locations (server root first, then world folder). */
    public final List<Path> usercachePaths;

    private StatswebConfig(boolean enabled, int port, int refreshSeconds, String levelName,
                           List<Path> statsDirs, List<Path> advDirs, List<Path> dataDirs,
                           List<Path> usercachePaths) {
        this.enabled = enabled;
        this.port = port;
        this.refreshSeconds = refreshSeconds;
        this.levelName = levelName;
        this.statsDirs = statsDirs;
        this.advDirs = advDirs;
        this.dataDirs = dataDirs;
        this.usercachePaths = usercachePaths;
    }

    public static StatswebConfig load(Path configFile, Path gameDir, StatswebLogger log) throws IOException {
        boolean enabled = true;
        int port = 8080;
        int refresh = 60;
        String levelName = null; // null → read from server.properties
        String overrideDir = null; // optional manual stats dir override

        if (Files.exists(configFile)) {
            try {
                JsonObject o = Json.GSON.fromJson(Files.readString(configFile, StandardCharsets.UTF_8), JsonObject.class);
                if (o != null) {
                    if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
                    if (o.has("port")) port = clamp(o.get("port").getAsInt(), 1, 65535);
                    if (o.has("refreshSeconds")) refresh = Math.max(10, o.get("refreshSeconds").getAsInt());
                    if (o.has("levelName")) levelName = o.get("levelName").getAsString();
                    if (o.has("statsDir")) overrideDir = o.get("statsDir").getAsString();
                }
            } catch (Exception e) {
                log.warn("[statsweb] could not parse config, using defaults", e);
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
                o.addProperty("_comment", "levelName: leave empty to auto-read from server.properties. statsDir: leave empty to auto-detect — the mod probes the modern layout (<world>/players/stats|advancements|data, MC 26.1+) and the legacy one (<world>/stats|advancements|playerdata, MC 1.18-1.21) at once; set a path to override.");
                Files.writeString(configFile, Json.GSON.toJson(o), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[statsweb] could not write default config", e);
            }
        }

        if (levelName == null || levelName.isBlank()) levelName = readLevelName(gameDir);
        Path world = gameDir.resolve(levelName);

        List<Path> statsDirs = new ArrayList<>();
        List<Path> advDirs = new ArrayList<>();
        List<Path> dataDirs = new ArrayList<>();
        Set<Path> seen = new HashSet<>();

        // Optional manual override: "statsDir" (absolute, or relative to the server root).
        Path override = null;
        if (overrideDir != null && !overrideDir.isBlank()) {
            Path p = Path.of(overrideDir);
            if (!p.isAbsolute()) p = gameDir.resolve(p);
            override = p.normalize();
            addUnique(statsDirs, seen, override);
            Path parent = override.getParent();
            if (parent != null) {
                // A manual statsDir means a custom/export layout — probe its siblings too.
                addUnique(advDirs, seen, findDir(parent, "advancements"));
                addUnique(dataDirs, seen, findDir(parent, "data"));
                addUnique(dataDirs, seen, findDir(parent, "playerdata"));
            }
        }

        // Minecraft has changed the per-player storage layout once so far, and every world is
        // probed for both, so one jar works on every version:
        //   * MC 1.18 – 1.21.x : <world>/stats,         <world>/advancements,         <world>/playerdata
        //   * MC 26.1+         : <world>/players/stats, <world>/players/advancements, <world>/players/data
        // The lookup deliberately ignores case (Linux servers may use "Stats"/"Advancements").
        Path playersRoot = findDir(world, "players");
        addUnique(statsDirs, seen, findDir(playersRoot, "stats"));
        addUnique(advDirs, seen, findDir(playersRoot, "advancements"));
        addUnique(dataDirs, seen, findDir(playersRoot, "data"));
        addUnique(statsDirs, seen, findDir(world, "stats"));
        addUnique(advDirs, seen, findDir(world, "advancements"));
        addUnique(dataDirs, seen, findDir(world, "playerdata"));
        // NB: <world>/data is deliberately NOT a candidate — that is the *world* data folder
        // (scoreboard.dat, world_clocks.dat, map_*.dat, raids.dat …) and never holds players.
        // Last resort: a wrong level-name, or data sitting at the server root.
        addUnique(statsDirs, seen, findDir(gameDir, "stats"));
        addUnique(advDirs, seen, findDir(gameDir, "advancements"));
        addUnique(dataDirs, seen, findDir(gameDir, "playerdata"));

        // usercache.json lives in the server root on dedicated servers; some setups put it in the world folder.
        List<Path> usercachePaths = List.of(gameDir.resolve("usercache.json"), world.resolve("usercache.json"));

        return new StatswebConfig(enabled, port, refresh, levelName, statsDirs, advDirs, dataDirs, usercachePaths);
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

    /** Adds {@code dir} once (normalised comparison, original order preserved). */
    private static void addUnique(List<Path> list, Set<Path> seen, Path dir) {
        if (dir == null) return;
        if (seen.add(dir.toAbsolutePath().normalize())) list.add(dir);
    }

    /**
     * Resolves {@code <root>/<name>}, falling back to a case-insensitive match among the
     * children of {@code root} — Linux servers happily use "Stats" or "Advancements",
     * and the canonical name from Windows-sized worlds would otherwise never resolve.
     * Returns the canonical (possibly non-existent) path when nothing matches, so the
     * startup log can still report it as "missing".
     */
    private static Path findDir(Path root, String name) {
        Path direct = root.resolve(name);
        if (Files.isDirectory(direct)) return direct;
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(direct);
        } catch (Exception ignored) {
            return direct;
        }
    }
}
