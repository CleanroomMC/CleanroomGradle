package com.cleanroommc.gradle.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTest {

    @ParameterizedTest
    @CsvSource({
            "WINDOWS, X64,     natives-windows",
            "WINDOWS, X86,     natives-windows-x86",
            "WINDOWS, ARM64,   natives-windows-arm64",
            "MAC_OS,  X64,     natives-macos",
            "MAC_OS,  ARM64,   natives-macos-arm64",
            "LINUX,   X64,     natives-linux",
            "LINUX,   ARM32,   natives-linux-arm32",
            "LINUX,   ARM64,   natives-linux-arm64",
            "LINUX,   PPC64LE, natives-linux-ppc64le",
            "LINUX,   RISCV64, natives-linux-riscv64",
            "FREE_BSD, X64,    natives-freebsd"
    })
    void resolveEachClassifierForEachPlatform(Platform.OperatingSystem os, Platform.Architecture arch, String classifier) {
        assertEquals(classifier, new Platform(os, arch).lwjglNativesClassifier());
    }

    @ParameterizedTest
    @CsvSource({
            // LWJGL publishes no 32-bit Linux or macOS natives, so those hosts get the 64-bit build
            "LINUX,  X86,     natives-linux",
            "MAC_OS, X86,     natives-macos",
            "MAC_OS, ARM32,   natives-macos",
            // Nor a non-x64 FreeBSD build
            "FREE_BSD, ARM64, natives-freebsd"
    })
    void fallbackWhenPlatformDoesNotHaveSpecifiedNative(Platform.OperatingSystem os, Platform.Architecture arch, String classifier) {
        assertEquals(classifier, new Platform(os, arch).lwjglNativesClassifier());
    }

    @Test
    void everyResolvedClassifierIsOneThatIsPublished() {
        for (var os : Platform.OperatingSystem.values()) {
            for (var arch : Platform.Architecture.values()) {
                var classifier = new Platform(os, arch).lwjglNativesClassifier();
                assertTrue(LwjglNatives.CLASSIFIERS.contains(classifier), () -> os + "/" + arch + " resolved to unpublished classifier " + classifier);
            }
        }
    }

    @Test
    void consistencyCheck() {
        assertTrue(Platform.Architecture.ARM64.isArm() && Platform.Architecture.ARM64.is64Bit());
        assertTrue(Platform.Architecture.ARM32.isArm() && !Platform.Architecture.ARM32.is64Bit());
        assertTrue(!Platform.Architecture.X64.isArm() && Platform.Architecture.X64.is64Bit());
        assertTrue(!Platform.Architecture.X86.isArm() && !Platform.Architecture.X86.is64Bit());
        assertTrue(Platform.Architecture.PPC64LE.is64Bit() && Platform.Architecture.RISCV64.is64Bit());
    }

}
