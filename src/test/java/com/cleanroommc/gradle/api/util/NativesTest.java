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

import com.cleanroommc.platformutils.Architecture;
import com.cleanroommc.platformutils.OperatingSystem;
import com.cleanroommc.platformutils.Platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NativesTest {

    @ParameterizedTest
    @CsvSource(
        {
            "WINDOWS,  X64,     windows-x64",
            "WINDOWS,  X86,     windows-x86",
            "WINDOWS,  ARM64,   windows-arm64",
            "WINDOWS,  ARM32,   windows-x64",
            "MAC_OS,   X64,     macos-x64",
            "MAC_OS,   ARM64,   macos-arm64",
            "MAC_OS,   ARM32,   macos-x64",
            "LINUX,    X64,     linux-x64",
            "LINUX,    X86,     linux-x64",
            "LINUX,    ARM32,   linux-arm32",
            "LINUX,    ARM64,   linux-arm64",
            "LINUX,    PPC64LE, linux-ppc64le",
            "LINUX,    RISCV64, linux-riscv64",
            "FREE_BSD, ARM64,   freebsd-x64",
            "OPEN_BSD, X64,     linux-x64",
            "ANDROID,  ARM64,   linux-arm64",
            "LINUX,    S390X,   linux-x64",
            "UNKNOWN,  UNKNOWN, linux-x64"
        }
    )
    void canonicalNativePlatform(OperatingSystem os, Architecture arch, String id) {
        assertThat(Natives.canonical(Platform.of(os, arch)).id()).isEqualTo(id);
    }

    @Test
    void everyPlatformRunsOnAPublishedOne() {
        for (var os : OperatingSystem.values()) {
            for (var arch : Architecture.values()) {
                assertThat(Natives.NATIVE_PLATFORMS)
                    .as(os + "/" + arch)
                    .contains(Natives.canonical(Platform.of(os, arch)));
            }
        }
    }

}
