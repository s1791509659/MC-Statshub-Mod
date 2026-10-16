package dev.statsweb.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Minimal big-endian NBT reader that converts a player {@code <uuid>.dat} file into a
 * Gson tree.
 *
 * <p>The output is deliberately a <em>faithful</em> JSON representation of the NBT payload
 * (numbers / strings / arrays / objects), because the shipped web page normalises that
 * tree with the very same code it uses for the locally imported {@code .dat} files —
 * exactly one set of parsing rules, no duplicated game logic on the Java side.
 *
 * <p>Like the browser reader, {@code TAG_Byte_Array} yields signed values, {@code TAG_Long}
 * yields a plain number and {@code TAG_Int_Array} / {@code TAG_Long_Array} yield arrays.
 */
public final class Nbt {

    private static final int TAG_END = 0;
    private static final int COMPOUND = 10;

    private final byte[] buf;
    private int pos;

    private Nbt(byte[] buf) {
        this.buf = buf;
    }

    /**
     * Reads a {@code .dat} file (gzip-compressed or plain) and returns the root compound,
     * or {@code null} when the file is not a usable NBT document.
     */
    public static JsonObject readFile(Path file, int maxBytes, StatswebLogger log) {
        try {
            byte[] raw = Files.readAllBytes(file);
            if (raw.length > maxBytes) return null;
            if (raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
                raw = gunzip(raw, maxBytes);
                if (raw == null) return null;
            }
            JsonObject root = parse(raw);
            return root;
        } catch (Exception e) {
            log.debug("[statsweb] failed to read playerdata {}", file, e);
            return null;
        }
    }

    private static byte[] gunzip(byte[] in, int maxBytes) throws IOException {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(in));
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(in.length * 8, 1 << 20))) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = gz.read(chunk)) > 0) {
                out.write(chunk, 0, n);
                if (out.size() > maxBytes) return null;
            }
            return out.toByteArray();
        }
    }

    /** Parses a plain (already decompressed) NBT document; returns the root compound or null. */
    public static JsonObject parse(byte[] data) {
        try {
            Nbt r = new Nbt(data);
            int type = r.u1();
            if (type != COMPOUND) return null;
            r.str(); // root name (usually an empty string)
            JsonElement root = r.payload(COMPOUND, 0);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int u1() { return buf[pos++] & 0xFF; }

    private int i1() { return buf[pos++]; }

    private int i2() { int v = (short) (((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF)); pos += 2; return v; }

    private int u2() { int v = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF); pos += 2; return v; }

    private int i4() {
        int v = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
        pos += 4;
        return v;
    }

    private long i8() {
        long v = 0;
        for (int k = 0; k < 8; k++) v = (v << 8) | (buf[pos + k] & 0xFF);
        pos += 8;
        return v;
    }

    private float f4() { return Float.intBitsToFloat(i4()); }

    private double f8() { return Double.longBitsToDouble(i8()); }

    private String str() {
        int n = u2();
        String s = new String(buf, pos, n, StandardCharsets.UTF_8);
        pos += n;
        return s;
    }

    private JsonElement payload(int type, int depth) {
        if (depth > 64) throw new IllegalStateException("NBT too deep");
        switch (type) {
            case 1: return new JsonPrimitive(i1());
            case 2: return new JsonPrimitive(i2());
            case 3: return new JsonPrimitive(i4());
            case 4: return new JsonPrimitive(i8());
            case 5: return new JsonPrimitive(f4());
            case 6: return new JsonPrimitive(f8());
            case 7: {
                int n = i4();
                JsonArray a = new JsonArray(n);
                for (int k = 0; k < n; k++) a.add(i1());
                return a;
            }
            case 8: return new JsonPrimitive(str());
            case 9: {
                int et = u1();
                int n = i4();
                JsonArray a = new JsonArray(n);
                for (int k = 0; k < n; k++) a.add(payload(et, depth + 1));
                return a;
            }
            case COMPOUND: {
                JsonObject o = new JsonObject();
                for (;;) {
                    int t = u1();
                    if (t == TAG_END) break;
                    String name = str();
                    o.add(name, payload(t, depth + 1));
                }
                return o;
            }
            case 11: {
                int n = i4();
                JsonArray a = new JsonArray(n);
                for (int k = 0; k < n; k++) a.add(i4());
                return a;
            }
            case 12: {
                int n = i4();
                JsonArray a = new JsonArray(n);
                for (int k = 0; k < n; k++) a.add(i8());
                return a;
            }
            default:
                throw new IllegalStateException("unknown NBT tag " + type);
        }
    }
}
