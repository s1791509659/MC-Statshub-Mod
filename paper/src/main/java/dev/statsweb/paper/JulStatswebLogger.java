package dev.statsweb.paper;

import dev.statsweb.core.StatswebLogger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code java.util.logging} adapter — the one logging API that ships with Bukkit, Spigot
 * and Paper alike, so this plugin needs no slf4j on the server classpath.
 *
 * <p>Messages are written slf4j-style ({@code "{}"} placeholders) because that is what the
 * core produces; a trailing {@link Throwable} argument is passed on as the log record's
 * cause so the full stack trace lands in the server log.
 */
public final class JulStatswebLogger implements StatswebLogger {

    private final Logger logger;

    public JulStatswebLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        log(Level.WARNING, message, args);
    }

    @Override
    public void error(String message, Object... args) {
        log(Level.SEVERE, message, args);
    }

    @Override
    public void debug(String message, Object... args) {
        log(Level.FINE, message, args);
    }

    private void log(Level level, String message, Object... args) {
        Throwable cause = null;
        if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
            cause = (Throwable) args[args.length - 1];
            args = Arrays.copyOf(args, args.length - 1);
        }
        String text = format(message, args);
        if (cause == null) {
            logger.log(level, text);
        } else {
            logger.log(level, text, cause);
        }
    }

    /** Minimal slf4j-style {@code {}} substitution, plus any extra arguments appended. */
    private static String format(String message, Object[] args) {
        if (message == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 32);
        int next = 0;
        int cursor = 0;
        while (next < args.length) {
            int at = message.indexOf("{}", cursor);
            if (at < 0) {
                break;
            }
            out.append(message, cursor, at).append(describe(args[next++]));
            cursor = at + 2;
        }
        out.append(message, cursor, message.length());
        for (int i = next; i < args.length; i++) {
            out.append(' ').append(describe(args[i]));
        }
        return out.toString();
    }

    private static String describe(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Throwable) {
            StringWriter sw = new StringWriter();
            ((Throwable) arg).printStackTrace(new PrintWriter(sw));
            return sw.toString();
        }
        return String.valueOf(arg);
    }
}
