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

import com.cleanroommc.gradle.api.schema.VersionMeta;

import com.google.gson.GsonBuilder;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VersionMetaTest {

    private static final Platform LINUX_X64 = new Platform(Platform.OperatingSystem.LINUX, Platform.Architecture.X64);
    private static final Platform LINUX_ARM64 = new Platform(Platform.OperatingSystem.LINUX, Platform.Architecture.ARM64);
    private static final Platform WINDOWS_X64 = new Platform(Platform.OperatingSystem.WINDOWS, Platform.Architecture.X64);
    private static final Platform FREE_BSD_X64 = new Platform(Platform.OperatingSystem.FREE_BSD, Platform.Architecture.X64);

    @Test
    void libraryRulesKeepTheLastMatch() {
        var linuxOnly = library(List.of(new VersionMeta.Rule("allow", new VersionMeta.OS("LiNuX"))));
        var nowhere = library(
                List.of(new VersionMeta.Rule("allow", new VersionMeta.OS("windows")), new VersionMeta.Rule("disallow", new VersionMeta.OS("windows")))
        );

        assertThat(library(null).isValidForOS(WINDOWS_X64)).isTrue();
        assertThat(linuxOnly.isValidForOS(LINUX_X64)).isTrue();
        assertThat(linuxOnly.isValidForOS(FREE_BSD_X64)).as("FreeBSD runs Linux libraries").isTrue();
        assertThat(linuxOnly.isValidForOS(WINDOWS_X64)).isFalse();
        assertThat(nowhere.isValidForOS(WINDOWS_X64)).isFalse();
    }

    @Test
    void nativeClassifierResolvesArchTemplateAndArm64Variant() {
        var templated = natives(Map.of("linux", "natives-linux-${arch}"), "natives-linux-64", "natives-linux-32");
        var withArm64 = natives(Map.of("linux", "natives-linux"), "natives-linux", "natives-linux-arm64");

        assertThat(templated.classifierForOS(LINUX_X64).path()).isEqualTo("natives-linux-64");
        assertThat(withArm64.classifierForOS(LINUX_X64).path()).isEqualTo("natives-linux");
        assertThat(withArm64.classifierForOS(LINUX_ARM64).path()).isEqualTo("natives-linux-arm64");
    }

    @Test
    void argumentAcceptsEveryJsonShape() {
        var gson = new GsonBuilder().registerTypeAdapter(VersionMeta.Argument.class, new VersionMeta.ArgumentDeserializer()).create();

        assertThat(gson.fromJson("\"--demo\"", VersionMeta.Argument.class).values()).containsExactly("--demo");
        assertThat(gson.fromJson("{\"value\": \"--demo\"}", VersionMeta.Argument.class).values()).containsExactly("--demo");
        assertThat(gson.fromJson("{\"rules\": [], \"value\": [\"--a\", \"--b\"]}", VersionMeta.Argument.class).values()).containsExactly("--a", "--b");
    }

    private static VersionMeta.Library library(List<VersionMeta.Rule> rules) {
        return new VersionMeta.Library(new VersionMeta.Downloads(download("artifact"), Map.of()), "g:a:1", null, rules, null);
    }

    private static VersionMeta.Library natives(Map<String, String> natives, String... classifiers) {
        var downloads = new HashMap<String, VersionMeta.Download>();
        for (var classifier : classifiers) {
            downloads.put(classifier, download(classifier));
        }
        return new VersionMeta.Library(new VersionMeta.Downloads(null, downloads), "g:a:1", natives, null, null);
    }

    private static VersionMeta.Download download(String path) {
        return new VersionMeta.Download(path, "sha1", 1, "https://example.invalid/" + path);
    }

}
