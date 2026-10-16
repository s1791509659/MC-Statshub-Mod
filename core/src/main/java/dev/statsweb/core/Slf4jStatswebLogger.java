package dev.statsweb.core;

import org.slf4j.Logger;

/**
 * {@link StatswebLogger} backed by slf4j. Both Fabric (via MC) and Paper/Spigot ship
 * slf4j, so this adapter is reused by every platform shell — and it is the only place
 * in the core that touches an external logging API.
 */
public final class Slf4jStatswebLogger implements StatswebLogger {

    private final Logger log;

    public Slf4jStatswebLogger(Logger log) {
        this.log = log;
    }

    @Override
    public void info(String message, Object... args) {
        log.info(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        log.warn(message, args);
    }

    @Override
    public void error(String message, Object... args) {
        log.error(message, args);
    }

    @Override
    public void debug(String message, Object... args) {
        log.debug(message, args);
    }
}
