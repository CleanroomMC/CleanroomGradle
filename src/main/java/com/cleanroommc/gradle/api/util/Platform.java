package com.cleanroommc.gradle.api.util;

public final class Platform {

    public static final Platform CURRENT = new Platform();

    public static String fixCommandLine(String cmdlineArg) {
        return CURRENT.getOperatingSystem().isWindows() ? cmdlineArg.replace("\"", "\\\"") : cmdlineArg;
    }

    private final OperatingSystem operatingSystem;
    private final Architecture architecture;

    private Platform() {
        this.operatingSystem = OperatingSystem.determine();
        this.architecture = Architecture.determine();
    }

    Platform(OperatingSystem operatingSystem, Architecture architecture) {
        this.operatingSystem = operatingSystem;
        this.architecture = architecture;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public enum OperatingSystem {

        WINDOWS,
        MAC_OS,
        LINUX;

        private static OperatingSystem determine() {
            var osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                return OperatingSystem.WINDOWS;
            } else if (osName.contains("mac")) {
                return OperatingSystem.MAC_OS;
            }
            // Or unknown
            return OperatingSystem.LINUX;
        }

        public boolean isWindows() {
            return this == WINDOWS;
        }

        public boolean isMacOS() {
            return this == MAC_OS;
        }

        public boolean isLinux() {
            return this == LINUX;
        }

    }

    public static final class Architecture {

        private static Architecture determine() {
            final String arch = System.getProperty("os.arch");
            var is64Bit = arch.contains("64") || arch.startsWith("armv8");
            var isArm = arch.startsWith("arm") || arch.startsWith("aarch64");
            return new Architecture(is64Bit, isArm);
        }

        static Architecture of(boolean is64Bit, boolean isArm) {
            return new Architecture(is64Bit, isArm);
        }

        private final boolean is64Bit, isArm;

        private Architecture(boolean is64Bit, boolean isArm) {
            this.is64Bit = is64Bit;
            this.isArm = isArm;
        }

        public boolean is64Bit() {
            return is64Bit;
        }

        public boolean isArm() {
            return isArm;
        }

    }

}

