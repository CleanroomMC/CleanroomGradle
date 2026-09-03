package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchArgumentsTest {

    private static final Platform LINUX = new Platform(
            Platform.OperatingSystem.LINUX, Platform.Architecture.X64);

    @Test
    void legacyTemplateIsSplitAndSubstituted() {
        var meta = meta(null, "--username ${auth_player_name} --version ${version_name}  --tweakClass");
        var warnings = new ArrayList<String>();
        var rendered = new LaunchArguments(meta, Map.of("auth_player_name", "Steve", "version_name", "1.12.2"),
                LINUX, warnings::add).gameArguments();
        assertEquals(List.of("--username", "Steve", "--version", "1.12.2", "--tweakClass"), rendered);
        assertTrue(warnings.isEmpty());
        assertTrue(new LaunchArguments(meta, Map.of(), LINUX, warnings::add).hasGameArguments());
    }

    @Test
    void emptyMetaHasNoGameArguments() {
        var meta = meta(null, null);
        assertEquals(List.of(), new LaunchArguments(meta, Map.of(), LINUX, ignored -> { }).gameArguments());
        assertEquals(List.of(), new LaunchArguments(meta, Map.of(), LINUX, ignored -> { }).jvmArguments());
        assertFalse(new LaunchArguments(meta, Map.of(), LINUX, ignored -> { }).hasGameArguments());
    }

    @Test
    void modernRulesKeepLastMatchingRule() {
        var windowsOnly = argument(
                List.of(rule("allow", "windows", null), rule("disallow", "osx", null)),
                List.of("--demo"));
        var meta = meta(new VersionMeta.Arguments(List.of(windowsOnly), List.of()), null);
        var windows = new Platform(Platform.OperatingSystem.WINDOWS, Platform.Architecture.X64);
        assertEquals(List.of("--demo"),
                new LaunchArguments(meta, Map.of(), windows, ignored -> { }).gameArguments());
        assertEquals(List.of(),
                new LaunchArguments(meta, Map.of(), LINUX, ignored -> { }).gameArguments());
    }

    @Test
    void featuresVetoAnOtherwiseMatchingRule() {
        var gated = argument(List.of(rule("allow", null, Map.of("is_demo_user", true))), List.of("--gated"));
        var meta = meta(new VersionMeta.Arguments(List.of(gated), List.of()), null);
        assertEquals(List.of(),
                new LaunchArguments(meta, Map.of(), LINUX, ignored -> { }).gameArguments());
    }

    @Test
    void jvmSkipsClasspathAndNativesButKeepsBranding() {
        var jvm = List.of(
                argument(null, List.of("-Djava.library.path=${natives_directory}")),
                argument(null, List.of("-cp")),
                argument(null, List.of("${classpath}")),
                argument(null, List.of("-Dminecraft.launcher.brand=${launcher_name}")),
                argument(null, List.of("--class-path")),
                argument(null, List.of("-Xmx2G")));
        var meta = meta(new VersionMeta.Arguments(List.of(), jvm), null);
        var rendered = new LaunchArguments(meta, Map.of("launcher_name", "cleanroom"),
                LINUX, ignored -> { }).jvmArguments();
        assertEquals(List.of("-Dminecraft.launcher.brand=cleanroom", "-Xmx2G"), rendered);
    }

    @Test
    void unknownPlaceholderWarnsOnceAndBecomesEmpty() {
        var meta = meta(new VersionMeta.Arguments(
                List.of(argument(null, List.of("--a=${missing}", "--b=${missing}"))), List.of()), null);
        var warnings = new ArrayList<String>();
        var rendered = new LaunchArguments(meta, Map.of(), LINUX, warnings::add).gameArguments();
        assertEquals(List.of("--a=", "--b="), rendered);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("${missing}"));
    }

    private static VersionMeta meta(VersionMeta.Arguments arguments, String minecraftArguments) {
        return new VersionMeta(arguments, null, "1.12", 0, Map.of(), "1.12.2", null, List.of(), null,
                "net.minecraft.client.main.Main", minecraftArguments, 0, "2017-01-01T00:00:00+00:00",
                "2017-01-01T00:00:00+00:00", "release");
    }

    private static VersionMeta.Argument argument(List<VersionMeta.ArgRule> rules, List<String> values) {
        return new VersionMeta.Argument(rules, values);
    }

    private static VersionMeta.ArgRule rule(String action, String os, Map<String, Boolean> features) {
        return new VersionMeta.ArgRule(action, os == null ? null : new VersionMeta.OS(os), features);
    }

}
