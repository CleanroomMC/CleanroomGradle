package com.cleanroommc.gradle.util;

public record Architecture(String name) {

    public static final Architecture CURRENT = new Architecture(System.getProperty("os.arch"));

    public boolean is64Bit() {
        return name.contains("64") || name.startsWith("armv8");
    }

    public boolean isArm() {
        return name.startsWith("arm") || name.startsWith("aarch64");
    }

}
