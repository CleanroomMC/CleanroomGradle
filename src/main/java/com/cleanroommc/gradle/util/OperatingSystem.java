package com.cleanroommc.gradle.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;

/**
 * Thanks to Fabric Loom
 */
public class OperatingSystem {

    public static final String WINDOWS = "windows";
    public static final String MAC_OS = "osx";
    public static final String LINUX = "linux";

    public static final String CURRENT_OS = getOS();

    private static String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("mac")) {
            return MAC_OS;
        } else {
            return LINUX;
        }
    }

    public static boolean is64Bit() {
        return System.getProperty("sun.arch.data.model").contains("64");
    }

    public static boolean isCIBuild() {
        String loomProperty = System.getProperty("fabric.loom.ci");
        if (loomProperty != null) {
            return loomProperty.equalsIgnoreCase("true");
        }
        // CI seems to be set by most popular CI services
        return System.getenv("CI") != null;
    }

    // Requires Unix, or Windows 10 17063 or later. See: https://devblogs.microsoft.com/commandline/af_unix-comes-to-windows/
    public static boolean isUnixDomainSocketsSupported() {
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
