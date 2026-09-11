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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VersionMetaTest {

    private static final Platform LINUX_X64 = new Platform(Platform.OperatingSystem.LINUX, Platform.Architecture.X64);
    private static final Platform WINDOWS_X64 = new Platform(Platform.OperatingSystem.WINDOWS, Platform.Architecture.X64);
    private static final Platform LINUX_ARM64 = new Platform(Platform.OperatingSystem.LINUX, Platform.Architecture.ARM64);

    @Test
    void ruleLessLibraryIsValidEverywhere() {
        assertThat(library(null, null).isValidForOS(LINUX_X64)).isTrue();
        assertThat(library(null, null).isValidForOS(WINDOWS_X64)).isTrue();
    }

    @Test
    void lastMatchingRuleWins() {
        var rules = List.of(new VersionMeta.Rule("allow", new VersionMeta.OS("windows")), new VersionMeta.Rule("disallow", new VersionMeta.OS("windows")));
        assertThat(new VersionMeta.Library(null, "g:a:1", null, rules, null).isValidForOS(WINDOWS_X64)).isFalse();
        assertThat(new VersionMeta.Library(null, "g:a:1", null, rules, null).isValidForOS(LINUX_X64)).isFalse();
    }

    @Test
    void osNameIsMatchedCaseInsensitivelyAndFreeBsdMapsToLinux() {
        var freeBsd = new Platform(Platform.OperatingSystem.FREE_BSD, Platform.Architecture.X64);
        var rules = List.of(new VersionMeta.Rule("allow", new VersionMeta.OS("LiNuX")));
        assertThat(new VersionMeta.Library(null, "g:a:1", null, rules, null).isValidForOS(LINUX_X64)).isTrue();
        assertThat(new VersionMeta.Library(null, "g:a:1", null, rules, null).isValidForOS(freeBsd)).isTrue();
        assertThat(new VersionMeta.Library(null, "g:a:1", null, rules, null).isValidForOS(WINDOWS_X64)).isFalse();
    }

    @Test
    void nativeClassifierResolvesArchTemplateAndArm64Fallback() {
        var templated = new VersionMeta.Library(
                new VersionMeta.Downloads(null, Map.of("natives-linux-64", download("linux-64"), "natives-linux-32", download("linux-32"))),
                "g:a:1",
                Map.of("linux", "natives-linux-${arch}"),
                null,
                null
        );
        assertThat(templated.classifierForOS(LINUX_X64).path()).isEqualTo("linux-64");

        var withArm64 = new VersionMeta.Library(
                new VersionMeta.Downloads(null, Map.of("natives-linux", download("linux"), "natives-linux-arm64", download("linux-arm64"))),
                "g:a:1",
                Map.of("linux", "natives-linux"),
                null,
                null
        );
        assertThat(withArm64.classifierForOS(LINUX_ARM64).path()).isEqualTo("linux-arm64");
        assertThat(withArm64.classifierForOS(LINUX_X64).path()).isEqualTo("linux");

        assertThat(templated.hasNatives()).isTrue();
        assertThat(templated.hasNativesForOS(LINUX_X64)).isTrue();
        assertThat(library(null, null).hasNativesForOS(LINUX_X64)).isFalse();
    }

    @Test
    void argRuleFeaturesVetoAndOsMatching() {
        assertThat(new VersionMeta.ArgRule("allow", null, Map.of("is_demo_user", true)).matches(LINUX_X64)).isFalse();
        assertThat(new VersionMeta.ArgRule("allow", null, Map.of("is_demo_user", false)).matches(LINUX_X64)).isTrue();
        assertThat(new VersionMeta.ArgRule("allow", new VersionMeta.OS("linux"), null).matches(LINUX_X64)).isTrue();
        assertThat(new VersionMeta.ArgRule("allow", new VersionMeta.OS("windows"), null).matches(LINUX_X64)).isFalse();
        assertThat(new VersionMeta.ArgRule("disallow", null, null).isAllowed()).isFalse();
        assertThat(new VersionMeta.ArgRule("allow", null, null).isAllowed()).isTrue();
    }

    @Test
    void argumentDeserializerHandlesPrimitiveSingleAndArrayValues() {
        Gson gson = new GsonBuilder().registerTypeAdapter(VersionMeta.Argument.class, new VersionMeta.ArgumentDeserializer()).create();
        assertThat(gson.fromJson("\"--demo\"", VersionMeta.Argument.class).values()).isEqualTo(List.of("--demo"));
        assertThat(gson.fromJson("{\"value\": \"--demo\"}", VersionMeta.Argument.class).values()).isEqualTo(List.of("--demo"));
        assertThat(gson.fromJson("{\"rules\": [], \"value\": [\"--a\", \"--b\"]}", VersionMeta.Argument.class).values()).isEqualTo(List.of("--a", "--b"));
    }

    @Test
    void javaMajorVersionTimeAndNativesHelpers() {
        assertThat(meta(null).javaMajor()).isEqualTo(8);
        assertThat(meta(new VersionMeta.JavaVersion("jre", 21)).javaMajor()).isEqualTo(21);
        assertThat(meta(null).isVersionOrNewer("2016-01-01T00:00:00+00:00")).isTrue();
        assertThat(meta(null).isVersionOrNewer("9999-01-01T00:00:00+00:00")).isFalse();
        assertThat(meta(null).hasNativesToExtract()).isFalse();
        assertThat(meta(null).assetIndexSha1()).isNull();
    }

    private static VersionMeta.Library library(List<VersionMeta.Rule> rules, Map<String, String> natives) {
        return new VersionMeta.Library(new VersionMeta.Downloads(download("artifact"), Map.of()), "g:a:1", natives, rules, null);
    }

    private static VersionMeta.Download download(String path) {
        return new VersionMeta.Download(path, "sha1", 1, "https://example.invalid/" + path);
    }

    private static VersionMeta meta(VersionMeta.JavaVersion javaVersion) {
        return new VersionMeta(
                null,
                null,
                "1.12",
                0,
                Map.of(),
                "1.12.2",
                javaVersion,
                List.of(),
                null,
                "Main",
                null,
                0,
                "2017-09-18T08:39:46+00:00",
                "2017-09-18T08:39:46+00:00",
                "release"
        );
    }

}
