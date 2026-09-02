package com.cleanroommc.gradle.api.util;

import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;

public final class Platform {

    public static final Platform CURRENT = new Platform();

    public static String fixCommandLine(String cmdlineArg) {
        return CURRENT.getOperatingSystem().isWindows() ? cmdlineArg.replace("\"", "\\\"") : cmdlineArg;
    }

    /**
     * Every distinct native classifier produced by {@link #lwjglNativesClassifier()}.
     */
    public static List<String> lwjglNativesClassifiers() {
        return nativePlatforms().stream().map(Platform::lwjglNativesClassifier).toList();
    }

    public static List<Platform> nativePlatforms() {
        var platforms = new LinkedHashMap<String, Platform>();
        for (var os : List.of(OperatingSystem.WINDOWS, OperatingSystem.LINUX,
                OperatingSystem.MAC_OS, OperatingSystem.FREE_BSD)) {
            for (var architecture : Architecture.values()) {
                var platform = new Platform(os, architecture);
                platforms.putIfAbsent(platform.lwjglNativesClassifier(), platform);
            }
        }
        return List.copyOf(platforms.values());
    }

    /**
     * Joins an existing {@code java.library.path} with a natives directory using the host separator.
     */
    public static String joinLibraryPath(String existing, File extra) {
        var added = fixCommandLine(extra.getAbsolutePath());
        if (existing == null || existing.isBlank()) {
            return added;
        }
        return existing + File.pathSeparator + added;
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

    public Platform canonicalNativePlatform() {
        var classifier = lwjglNativesClassifier();
        return nativePlatforms().stream()
                .filter(platform -> platform.lwjglNativesClassifier().equals(classifier))
                .findFirst()
                .orElseThrow();
    }

    public String operatingSystemFamily() {
        return switch (this.operatingSystem) {
            case WINDOWS -> OperatingSystemFamily.WINDOWS;
            case MAC_OS -> OperatingSystemFamily.MACOS;
            case LINUX -> OperatingSystemFamily.LINUX;
            case FREE_BSD -> "freebsd";
        };
    }

    public String machineArchitecture() {
        return switch (this.architecture) {
            case X64 -> MachineArchitecture.X86_64;
            case X86 -> MachineArchitecture.X86;
            case ARM64 -> MachineArchitecture.ARM64;
            case ARM32 -> "arm32";
            case PPC64LE -> "ppc64le";
            case RISCV64 -> "riscv64";
        };
    }

    public String lwjglNativesClassifier() {
        return switch (this.operatingSystem) {
            case WINDOWS -> switch (this.architecture) {
                case X86 -> "natives-windows-x86";
                case ARM64 -> "natives-windows-arm64";
                default -> "natives-windows";
            };
            case MAC_OS -> this.architecture == Architecture.ARM64 ? "natives-macos-arm64" : "natives-macos";
            case LINUX -> switch (this.architecture) {
                case ARM32 -> "natives-linux-arm32";
                case ARM64 -> "natives-linux-arm64";
                case PPC64LE -> "natives-linux-ppc64le";
                case RISCV64 -> "natives-linux-riscv64";
                default -> "natives-linux";
            };
            // LWJGL builds FreeBSD natives for x64 only
            case FREE_BSD -> "natives-freebsd";
        };
    }

    public enum OperatingSystem {

        WINDOWS,
        MAC_OS,
        LINUX,
        FREE_BSD;

        private static OperatingSystem determine() {
            var osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                return OperatingSystem.WINDOWS;
            } else if (osName.contains("mac")) {
                return OperatingSystem.MAC_OS;
            } else if (osName.contains("freebsd")) {
                return OperatingSystem.FREE_BSD;
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

        public boolean isFreeBSD() {
            return this == FREE_BSD;
        }

    }

    public enum Architecture {

        X64(true, false),
        X86(false, false),
        ARM64(true, true),
        ARM32(false, true),
        PPC64LE(true, false),
        RISCV64(true, false);

        private static Architecture determine() {
            var arch = System.getProperty("os.arch").toLowerCase();
            return switch (arch) {
                case "x86", "i386", "i486", "i586", "i686" -> X86;
                case "aarch64", "arm64" -> ARM64;
                case "ppc64le" -> PPC64LE;
                case "riscv64" -> RISCV64;
                // armv8 is 64-bit arm reported under the 32-bit name, every other armv* is genuinely 32-bit
                default -> {
                    if (arch.startsWith("armv8") || arch.startsWith("aarch64")) {
                        yield ARM64;
                    }
                    yield arch.startsWith("arm") ? ARM32 : X64;
                }
            };
        }

        private final boolean is64Bit, isArm;

        Architecture(boolean is64Bit, boolean isArm) {
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
