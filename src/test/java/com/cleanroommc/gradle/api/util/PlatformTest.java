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

import static org.assertj.core.api.Assertions.assertThat;

class PlatformTest {

    @ParameterizedTest
    @CsvSource(
            {
                    "WINDOWS,  X64,     natives-windows",
                    "WINDOWS,  X86,     natives-windows-x86",
                    "WINDOWS,  ARM64,   natives-windows-arm64",
                    "MAC_OS,   X64,     natives-macos",
                    "MAC_OS,   ARM64,   natives-macos-arm64",
                    "LINUX,    X64,     natives-linux",
                    "LINUX,    ARM32,   natives-linux-arm32",
                    "LINUX,    ARM64,   natives-linux-arm64",
                    "LINUX,    PPC64LE, natives-linux-ppc64le",
                    "LINUX,    RISCV64, natives-linux-riscv64",
                    "FREE_BSD, X64,     natives-freebsd",
                    "LINUX,    X86,     natives-linux",
                    "MAC_OS,   ARM32,   natives-macos"
            }
    )
    void lwjglNativesClassifier(Platform.OperatingSystem os, Platform.Architecture arch, String classifier) {
        assertThat(new Platform(os, arch).lwjglNativesClassifier()).isEqualTo(classifier);
    }

    @Test
    void everyResolvedClassifierIsPublished() {
        for (var os : Platform.OperatingSystem.values()) {
            for (var arch : Platform.Architecture.values()) {
                assertThat(LwjglNatives.CLASSIFIERS).as(os + "/" + arch).contains(new Platform(os, arch).lwjglNativesClassifier());
            }
        }
    }

}
