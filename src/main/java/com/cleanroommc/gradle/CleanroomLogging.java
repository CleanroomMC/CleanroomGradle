package com.cleanroommc.gradle;

import org.gradle.api.logging.Logger;

public class CleanroomLogging {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_RED = "\u001B[31m";

    public static void title(Logger logger, String log, Object... objects) {
        logger.lifecycle("\n" + ANSI_CYAN + log + ANSI_RESET, objects);
    }

    public static void task(Logger logger, String log, Object... objects) {
        logger.lifecycle(ANSI_CYAN + log + ANSI_RESET, objects);
    }

    public static void step(Logger logger, String log, Object... objects) {
        logger.lifecycle(ANSI_GREEN + log + ANSI_RESET, objects);
    }

    public static void warn(Logger logger, String log, Object... objects) {
        logger.warn(ANSI_YELLOW + log + ANSI_RESET, objects);
    }

    public static void warn(Logger logger, String log, Throwable t) {
        logger.warn(ANSI_YELLOW + log + ANSI_RESET, t);
    }

    public static void error(Logger logger, String log, Object... objects) {
        logger.error(ANSI_RED + log + ANSI_RESET, objects);
    }

}
