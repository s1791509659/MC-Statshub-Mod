package dev.statsweb.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Shared Gson instance (HTML escaping disabled so the served JSON stays readable).
 *
 * <p>Gson itself is provided by the host platform (Minecraft and Paper both bundle it),
 * hence it is a compile-only dependency of the core.
 */
public final class Json {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }
}
