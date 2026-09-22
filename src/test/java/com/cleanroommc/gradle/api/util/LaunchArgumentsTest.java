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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LaunchArgumentsTest {

    private static final Platform LINUX = new Platform(Platform.OperatingSystem.LINUX, Platform.Architecture.X64);
    private static final Platform WINDOWS = new Platform(Platform.OperatingSystem.WINDOWS, Platform.Architecture.X64);

    @Test
    void legacyTemplateIsSplitAndSubstituted() {
        var meta = meta(null, "--username ${auth_player_name}  --tweakClass");

        assertThat(new LaunchArguments(meta, Map.of("auth_player_name", "Steve"), LINUX, warning -> { }).gameArguments()).containsExactly(
                "--username",
                "Steve",
                "--tweakClass"
        );
    }

    @Test
    void rulesKeepTheLastMatchAndFeaturesVeto() {
        var windowsOnly = new VersionMeta.Argument(List.of(rule("allow", "windows", null), rule("disallow", "osx", null)), List.of("--windows"));
        var demoOnly = new VersionMeta.Argument(List.of(rule("allow", null, Map.of("is_demo_user", true))), List.of("--demo"));
        var meta = meta(new VersionMeta.Arguments(List.of(windowsOnly, demoOnly), List.of()), null);

        assertThat(new LaunchArguments(meta, Map.of(), WINDOWS, warning -> { }).gameArguments()).containsExactly("--windows");
        assertThat(new LaunchArguments(meta, Map.of(), LINUX, warning -> { }).gameArguments()).isEmpty();
    }

    @Test
    void jvmArgumentsDropWhatTheRunSuppliesItself() {
        var jvm = List.of(
                argument("-Djava.library.path=${natives_directory}"),
                argument("-cp"),
                argument("${classpath}"),
                argument("--class-path"),
                argument("-Dminecraft.launcher.brand=${launcher_name}"),
                argument("-Xmx2G")
        );
        var meta = meta(new VersionMeta.Arguments(List.of(), jvm), null);

        assertThat(new LaunchArguments(meta, Map.of("launcher_name", "cleanroom"), LINUX, warning -> { }).jvmArguments()).containsExactly(
                "-Dminecraft.launcher.brand=cleanroom",
                "-Xmx2G"
        );
    }

    @Test
    void unknownPlaceholderWarnsOnceAndBecomesEmpty() {
        var meta = meta(new VersionMeta.Arguments(List.of(new VersionMeta.Argument(null, List.of("--a=${missing}", "--b=${missing}"))), List.of()), null);
        var warnings = new ArrayList<String>();

        assertThat(new LaunchArguments(meta, Map.of(), LINUX, warnings::add).gameArguments()).containsExactly("--a=", "--b=");
        assertThat(warnings).singleElement().asString().contains("${missing}");
    }

    private static VersionMeta meta(VersionMeta.Arguments arguments, String minecraftArguments) {
        return new VersionMeta(
                arguments,
                null,
                "1.12",
                0,
                Map.of(),
                "1.12.2",
                null,
                List.of(),
                null,
                "net.minecraft.client.main.Main",
                minecraftArguments,
                0,
                "2017-01-01T00:00:00+00:00",
                "2017-01-01T00:00:00+00:00",
                "release"
        );
    }

    private static VersionMeta.Argument argument(String value) {
        return new VersionMeta.Argument(null, List.of(value));
    }

    private static VersionMeta.ArgRule rule(String action, String os, Map<String, Boolean> features) {
        return new VersionMeta.ArgRule(action, os == null ? null : new VersionMeta.OS(os), features);
    }

}
