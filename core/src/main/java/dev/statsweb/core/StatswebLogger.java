package dev.statsweb.core;

/**
 * Platform-independent logging facade.
 *
 * <p>The core deliberately knows nothing about Minecraft or any mod loader, so each
 * platform shell (Fabric, Paper, …) injects its own logger. {@code Slf4jStatswebLogger}
 * covers every platform that ships slf4j, which is all of them today; this interface
 * exists so a platform can plug in something else without touching the core.
 */
public interface StatswebLogger {

    void info(String message, Object... args);

    /** {@code args} may end with a {@link Throwable}, exactly like slf4j. */
    void warn(String message, Object... args);

    /** {@code args} may end with a {@link Throwable}, exactly like slf4j. */
    void error(String message, Object... args);

    /** {@code args} may end with a {@link Throwable}, exactly like slf4j. */
    void debug(String message, Object... args);
}
