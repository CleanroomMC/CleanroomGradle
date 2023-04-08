package com.cleanroommc.gradle.util;

public final class EnvironmentUtil {

    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }

    public static String getJVMVersion() {
        return System.getProperty("java.vm.version");
    }

    public static String getJavaVendor() {
        return System.getProperty("java.vendor");
    }

    public static boolean isIdeaSyncing() {
        return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
    }

    private EnvironmentUtil() { }

}
