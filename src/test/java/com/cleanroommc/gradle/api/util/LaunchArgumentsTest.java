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

import com.cleanroommc.launcherutils.rule.Rule;
import com.cleanroommc.launcherutils.version.Argument;
import com.cleanroommc.launcherutils.version.VersionJson;
import com.cleanroommc.platformutils.Architecture;
import com.cleanroommc.platformutils.OperatingSystem;
import com.cleanroommc.platformutils.Platform;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LaunchArgumentsTest {

    private static final Platform LINUX = Platform.of(OperatingSystem.LINUX, Architecture.X64);
    private static final Platform WINDOWS = Platform.of(OperatingSystem.WINDOWS, Architecture.X64);

    @Test
    void legacyTemplateIsSplitAndSubstituted() {
        var meta = new VersionJson().minecraftArguments("--username ${auth_player_name}  --tweakClass");

        assertThat(
            new LaunchArguments(meta, Map.of("auth_player_name", "Steve"), LINUX, warning -> { }).gameArguments()
        ).containsExactly("--username", "Steve", "--tweakClass");
    }

    @Test
    void rulesKeepTheLastMatchAndFeaturesVeto() {
        var meta = new VersionJson()
            .gameArgument(Argument.of("--windows").rule(Rule.allow().os("windows")).rule(Rule.disallow().os("osx")))
            .gameArgument(Argument.of("--demo").rule(Rule.allow().feature("is_demo_user", true)));

        assertThat(new LaunchArguments(meta, Map.of(), WINDOWS, warning -> { }).gameArguments()).containsExactly(
            "--windows"
        );
        assertThat(new LaunchArguments(meta, Map.of(), LINUX, warning -> { }).gameArguments()).isEmpty();
    }

    @Test
    void jvmArgumentsDropWhatTheRunSuppliesItself() {
        var meta = new VersionJson();
        for (var value : new String[] {
            "-Djava.library.path=${natives_directory}",
            "-cp",
            "${classpath}",
            "--class-path",
            "-Dminecraft.launcher.brand=${launcher_name}",
            "-Xmx2G"
        }) {
            meta.jvmArgument(Argument.of(value));
        }

        assertThat(
            new LaunchArguments(meta, Map.of("launcher_name", "cleanroom"), LINUX, warning -> { }).jvmArguments()
        ).containsExactly("-Dminecraft.launcher.brand=cleanroom", "-Xmx2G");
    }

    @Test
    void unknownPlaceholderWarnsOnceAndBecomesEmpty() {
        var meta = new VersionJson().gameArgument(Argument.of("--a=${missing}", "--b=${missing}"));
        var warnings = new ArrayList<String>();

        assertThat(new LaunchArguments(meta, Map.of(), LINUX, warnings::add).gameArguments()).containsExactly(
            "--a=", "--b="
        );
        assertThat(warnings).singleElement().asString().contains("${missing}");
    }

}
