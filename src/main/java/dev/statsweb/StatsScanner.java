package dev.statsweb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans world/&lt;level&gt;/stats/*.json + usercache.json and builds a snapshot.
 *
 * This is a Java port of the analyzer's battle-tested JS parser: it accepts stats
 * exports from any server layout, UUID or player-name file names, BOMs, numeric
 * strings, modded namespaces, wrapped structures, and skips non-stat JSON.
 */
public final class StatsScanner {

    /** One player in the snapshot. */
    public static final class Player {
        public final String key;          // uuid or "name:<player>"
        public String name;               // resolved display name (may be null)
        public final Map<String, Map<String, Long>> stats = new HashMap<>(); // cat -> item -> sum
        Player(String key) { this.key = key; }
    }

    /** Immutable-ish snapshot swapped atomically. */
    public static final class Snapshot {
        public final long generatedAt = System.currentTimeMillis();
        public final Map<String, Player> players = new LinkedHashMap<>();
        public final List<String[]> usercache = new ArrayList<>(); // [uuid, name]
        public int filesScanned;
        public int skipped;
        public int badJson;
    }

    // LinkedHashMap (java.util) keeps player ordering stable

    private static final Pattern UUID_DASHED = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern UUID_BARE = Pattern.compile("(?i)[0-9a-f]{32}");
    private static final Set<String> STANDARD_CATS = Set.of(
            "mined", "crafted", "used", "broken", "picked_up", "dropped",
            "killed", "killed_by", "custom", "placed", "entity_killed_by");

    private final StatswebConfig config;
    private volatile Snapshot snapshot = new Snapshot();

    public StatsScanner(StatswebConfig config) {
        this.config = config;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    /** Rescans files and swaps the snapshot atomically. Never throws. */
    public void scanOnce() {
        try {
            snapshot = scan();
        } catch (Exception e) {
            StatswebMod.LOGGER.warn("[statsweb] scan failed, keeping previous snapshot", e);
        }
    }

    private Snapshot scan() throws IOException {
        Snapshot snap = new Snapshot();

        // 1) usercache files first (names for uuid-keyed files)
        for (Path uc : config.usercachePaths) {
            if (!Files.isRegularFile(uc)) continue;
            try {
                JsonElement el = parseLenient(Files.readString(uc, StandardCharsets.UTF_8));
                if (el != null && el.isJsonArray()) {
                    for (JsonElement e : el.getAsJsonArray()) {
                        if (!e.isJsonObject()) continue;
                        JsonObject o = e.getAsJsonObject();
                        String uuid = firstString(o, "uuid", "id", "UUID");
                        String name = firstString(o, "name", "username", "Name");
                        if (uuid != null && name != null && !name.isBlank()) {
                            String norm = normalizeUuid(uuid);
                            if (norm != null) {
                                snap.usercache.add(new String[]{norm, name});
                                NameIndex.put(name.toLowerCase(), norm);
                                Player p = snap.players.computeIfAbsent(norm, Player::new);
                                if (p.name == null) p.name = name;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                StatswebMod.LOGGER.warn("[statsweb] failed to read {}", uc, e);
            }
        }

        // 2) stats files, any depth, across all candidate dirs (26.1+ players/stats + legacy stats)
        for (Path dir : config.statsDirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                        .sorted()
                        .toList();
                for (Path f : files) {
                    String fn = f.getFileName().toString();
                    if (fn.equalsIgnoreCase("usercache.json")) continue;
                    snap.filesScanned++;
                    try {
                        JsonElement el = parseLenient(Files.readString(f, StandardCharsets.UTF_8));
                        if (el == null || !el.isJsonObject()) { snap.badJson++; continue; }
                        JsonObject obj = el.getAsJsonObject();

                        List<Object[]> pairs = extractStatsMap(obj); // [cat, item, value]
                        if (pairs == null) { snap.skipped++; continue; }

                        String key = keyFor(obj, f);
                        Player p = snap.players.computeIfAbsent(key, Player::new);
                        if (p.name == null) {
                            String n = firstString(obj, "name", "username", "playerName");
                            if (n != null && !n.isBlank()) p.name = n;
                        }
                        for (Object[] pr : pairs) {
                            String cat = (String) pr[0], item = (String) pr[1];
                            long v = (Long) pr[2];
                            p.stats.computeIfAbsent(cat, k -> new HashMap<>()).merge(item, v, Long::sum);
                        }
                    } catch (Exception e) {
                        snap.badJson++;
                        StatswebMod.LOGGER.debug("[statsweb] failed to parse {}", f, e);
                    }
                }
            } catch (Exception e) {
                StatswebMod.LOGGER.warn("[statsweb] failed to walk {}", dir, e);
            }
        }

        // 3) resolve names for name-keyed players
        for (Player p : snap.players.values()) {
            if (p.name == null && p.key.startsWith("name:")) p.name = p.key.substring(5);
        }
        // name → uuid map from usercache already merged above.
        return snap;
    }

    /** Player key: dashed/bare UUID from file name, else JSON uuid field, else "name:&lt;stem&gt;". */
    private static String keyFor(JsonObject obj, Path file) {
        String stem = file.getFileName().toString();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) stem = stem.substring(0, dot);

        String k = extractUuid(stem);
        if (k == null && obj != null) {
            k = extractUuid(firstString(obj, "uuid", "UUID", "playerUuid", "playerUUID"));
        }
        if (k != null) return k;

        String mapped = NameIndex.get(stem.toLowerCase());
        if (mapped != null) return mapped;

        // stem itself might be a bare UUID handled above; otherwise it's a player name
        String bare = stem.replace("-", "");
        if (UUID_BARE.matcher(bare).matches()) {
            return bare.substring(0, 8) + "-" + bare.substring(8, 12) + "-" + bare.substring(12, 16)
                    + "-" + bare.substring(16, 20) + "-" + bare.substring(20);
        }
        return "name:" + stem;
    }

    /** name → uuid lookup filled while processing usercache. */
    static final class NameIndex {
        static final Map<String, String> MAP = new HashMap<>();
        static String get(String k) { return MAP.get(k); }
        static void put(String k, String v) { MAP.put(k, v); }
    }

    private static String extractUuid(String s) {
        if (s == null) return null;
        Matcher m = UUID_DASHED.matcher(s);
        if (m.find()) return m.group().toLowerCase();
        Matcher b = UUID_BARE.matcher(s.replace("-", ""));
        if (b.find()) {
            String bare = b.group().toLowerCase();
            return bare.substring(0, 8) + "-" + bare.substring(8, 12) + "-" + bare.substring(12, 16)
                    + "-" + bare.substring(16, 20) + "-" + bare.substring(20);
        }
        return null;
    }

    private static String normalizeUuid(String uuid) {
        return extractUuid(uuid);
    }

    /**
     * Extracts [cat, item, value] triples from a stats JSON object.
     * Returns null when the object does not look like stats data.
     */
    static List<Object[]> extractStatsMap(JsonObject obj) {
        if (obj == null) return null;

        JsonElement statsEl = obj.get("stats");
        JsonObject stats;
        if (statsEl != null && statsEl.isJsonObject()) {
            stats = statsEl.getAsJsonObject();
        } else if (looksLikeStats(obj)) {
            stats = obj; // the object itself is the stats mapping
        } else {
            return null;
        }

        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> catEntry : stats.entrySet()) {
            if (!catEntry.getValue().isJsonObject()) continue;
            String cat = stripNamespace(catEntry.getKey());
            for (Map.Entry<String, JsonElement> it : catEntry.getValue().getAsJsonObject().entrySet()) {
                long v = asLong(it.getValue());
                if (v == Long.MIN_VALUE) continue;
                out.add(new Object[]{cat, stripNamespace(it.getKey()), v});
            }
        }
        return out;
    }

    /** Heuristic: an object whose members are objects of numeric-ish values, like the JS parser. */
    static boolean looksLikeStats(JsonObject obj) {
        if (obj.size() == 0 || obj.size() > 80) return false;
        int hit = 0;
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject v = e.getValue().getAsJsonObject();
            if (v.size() == 0 || v.size() > 5000) continue;
            boolean allNum = true;
            for (Map.Entry<String, JsonElement> iv : v.entrySet()) {
                if (asLong(iv.getValue()) == Long.MIN_VALUE) { allNum = false; break; }
            }
            if (allNum) hit++;
        }
        boolean hasStandard = false;
        for (String k : obj.keySet()) {
            if (STANDARD_CATS.contains(stripNamespace(k))) { hasStandard = true; break; }
        }
        return hasStandard || hit >= 3;
    }

    private static long asLong(JsonElement e) {
        try {
            if (e.isJsonPrimitive()) {
                if (e.getAsJsonPrimitive().isNumber()) return e.getAsLong();
                String s = e.getAsString().trim();
                return Long.parseLong(s);
            }
        } catch (NumberFormatException ignored) {
        }
        return Long.MIN_VALUE;
    }

    private static String stripNamespace(String id) {
        int i = id.indexOf(':');
        if (i >= 0 && id.regionMatches(0, "minecraft", 0, i)) return id.substring(i + 1);
        return id; // modded namespaces are preserved (matches the web analyzer)
    }

    private static String firstString(JsonObject o, String... keys) {
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive()) return e.getAsString();
        }
        return null;
    }

    private static JsonElement parseLenient(String content) {
        String s = content;
        if (s.startsWith("\uFEFF")) s = s.substring(1); // strip BOM
        s = s.trim();
        return StatswebMod.GSON.fromJson(s, JsonElement.class);
    }

    /** Builds the /api/data JSON document from the current snapshot. */
    public JsonObject toJson(Snapshot snap) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", StatswebMod.MOD_ID);
        root.addProperty("version", StatswebMod.VERSION);
        root.addProperty("generatedAt", snap.generatedAt);
        root.addProperty("filesScanned", snap.filesScanned);
        root.addProperty("skipped", snap.skipped);
        root.addProperty("badJson", snap.badJson);

        JsonArray players = new JsonArray();
        for (Player p : snap.players.values()) {
            JsonObject po = new JsonObject();
            po.addProperty("uuid", p.key);
            if (p.name != null) po.addProperty("name", p.name);
            JsonObject cats = new JsonObject();
            for (Map.Entry<String, Map<String, Long>> c : p.stats.entrySet()) {
                JsonObject items = new JsonObject();
                for (Map.Entry<String, Long> i : c.getValue().entrySet()) {
                    items.addProperty(i.getKey(), i.getValue());
                }
                cats.add(c.getKey(), items);
            }
            po.add("stats", cats);
            players.add(po);
        }
        root.add("players", players);

        JsonArray uc = new JsonArray();
        for (String[] u : snap.usercache) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", u[0]);
            o.addProperty("name", u[1]);
            uc.add(o);
        }
        root.add("usercache", uc);
        return root;
    }
}
