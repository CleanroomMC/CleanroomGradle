package com.cleanroommc.gradle;


import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class CleanroomLogger {

    private static final Logger logger = Logging.getLogger("Cleanroom");

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";

    public static void logTitle(String log, Object... objects) {
        logger.lifecycle("\n" + ANSI_CYAN + log + ANSI_RESET + "\n", objects);
    }

    public static void log(String log, Object... objects) {
        logger.lifecycle(ANSI_CYAN + log + ANSI_RESET, objects);
    }

    public static void log2(String log, Object... objects) {
        logger.lifecycle(ANSI_GREEN + log + ANSI_RESET, objects);
    }

    public static void warn(String log, Object... objects) {
        logger.warn(ANSI_YELLOW + log + ANSI_RESET, objects);
    }

    public static void warn(String log, Throwable t) {
        logger.warn(ANSI_YELLOW + log + ANSI_RESET, t);
    }

}
