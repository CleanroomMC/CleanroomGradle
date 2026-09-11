/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformTest {

    @ParameterizedTest
    @CsvSource(
            {
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
                    "FREE_BSD, X64,    natives-freebsd",
                    "LINUX,   X86,     natives-linux",
                    "MAC_OS,  X86,     natives-macos",
                    "MAC_OS,  ARM32,   natives-macos",
                    "FREE_BSD, ARM64,  natives-freebsd"
            }
    )
    void lwjglNativesClassifier(Platform.OperatingSystem os, Platform.Architecture arch, String classifier) {
        assertThat(new Platform(os, arch).lwjglNativesClassifier()).isEqualTo(classifier);
    }

    @Test
    void everyResolvedClassifierIsPublished() {
        for (var os : Platform.OperatingSystem.values()) {
            for (var arch : Platform.Architecture.values()) {
                var classifier = new Platform(os, arch).lwjglNativesClassifier();
                assertThat(LwjglNatives.CLASSIFIERS.contains(classifier))
                        .as(() -> os + "/" + arch + " resolved to unpublished classifier " + classifier)
                        .isTrue();
            }
        }
    }

    @Test
    void joinLibraryPath() {
        var extra = new File("natives");
        var added = Platform.fixCommandLine(extra.getAbsolutePath());
        assertThat(Platform.joinLibraryPath("", extra)).isEqualTo(added);
        assertThat(Platform.joinLibraryPath(null, extra)).isEqualTo(added);

        var joined = Platform.joinLibraryPath("/usr/lib", extra);
        assertThat(joined).contains(File.pathSeparator);
        assertThat(joined).endsWith(added);
    }

    @Test
    void architectureFlags() {
        assertThat(Platform.Architecture.ARM64.isArm() && Platform.Architecture.ARM64.is64Bit()).isTrue();
        assertThat(Platform.Architecture.ARM32.isArm() && !Platform.Architecture.ARM32.is64Bit()).isTrue();
        assertThat(!Platform.Architecture.X64.isArm() && Platform.Architecture.X64.is64Bit()).isTrue();
        assertThat(!Platform.Architecture.X86.isArm() && !Platform.Architecture.X86.is64Bit()).isTrue();
        assertThat(Platform.Architecture.PPC64LE.is64Bit() && Platform.Architecture.RISCV64.is64Bit()).isTrue();
    }

}
