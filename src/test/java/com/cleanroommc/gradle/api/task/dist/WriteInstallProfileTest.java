/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WriteInstallProfileTest {

    @TempDir
    Path directory;

    @Test
    void writesResolvedGraphWithPlatformNativeMetadata() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);

        var universal = file("cleanroom-1.0.0-universal.jar", "cleanroom");
        var guava = file("guava-33.6.0-jre.jar", "guava");
        var lwjgl = file("lwjgl-3.4.2.jar", "lwjgl");
        var lwjglLinux = file("lwjgl-3.4.2-natives-linux.jar", "lwjgl linux native");
        var lwjglLinuxArm = file("lwjgl-3.4.2-natives-linux-arm64.jar", "lwjgl linux arm native");
        var lwjglWindows = file("lwjgl-3.4.2-natives-windows.jar", "lwjgl windows native");
        var nettyLinux = file("netty-transport-native-epoll-4.2.16.Final-linux-x86_64.jar", "netty linux native");
        var nettyArm = file("netty-transport-native-epoll-4.2.16.Final-linux-aarch_64.jar", "netty arm native");
        var text2speech = file("text2speech-1.10.3-natives-linux.jar", "text2speech linux native");
        var text2speechWindows = file("text2speech-1.10.3-natives-windows.jar", "text2speech windows native");
        var jinputLinux = file("jinput-platform-2.0.5-natives-linux.jar", "jinput linux native");
        var jinputWindows = file("jinput-platform-2.0.5-natives-windows.jar", "jinput windows native");
        var soundsystem = file("soundsystem-20120107.jar", "soundsystem");

        task.getUniversalJar().fileValue(universal.toFile());
        task.getLibraries().add(library(project, "com.google.guava:guava:33.6.0-jre", guava, "https://authority.example/releases/"));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2", lwjgl));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-linux", lwjglLinux));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-linux-arm64", lwjglLinuxArm));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-windows", lwjglWindows));
        task.getLibraries().add(library(project, "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64", nettyLinux));
        task.getLibraries().add(library(project, "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64", nettyArm));
        task.getLibraries().add(library(project, "com.paulscode:soundsystem:20120107", soundsystem));
        task.getNativeLibraries().add(library(project, "com.mojang:text2speech:1.10.3:natives-linux", text2speech));
        task.getNativeLibraries().add(library(project, "com.mojang:text2speech:1.10.3:natives-windows", text2speechWindows));
        task.getNativeLibraries().add(library(project, "net.java.jinput:jinput-platform:2.0.5:natives-linux", jinputLinux));
        task.getNativeLibraries().add(library(project, "net.java.jinput:jinput-platform:2.0.5:natives-windows", jinputWindows));
        task.getVersionMeta()
                .set(
                        versionMetaWithId(
                                "1.12.2-custom",
                                "--username ${auth_player_name} --versionType ${version_type}",
                                "com.google.guava:guava:21.0",
                                "com.mojang:patchy:1.3.9",
                                "com.paulscode:soundsystem:20120107"
                        )
                );
        task.getManifestUrls()
                .put("com.paulscode:soundsystem:20120107", "https://libraries.minecraft.net/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar");
        task.getManifestUrls()
                .put(
                        "com.mojang:text2speech:1.10.3:natives-linux",
                        "https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar"
                );

        task.write();

        assertThat(json(task.getInstallProfile().get().getAsFile().toPath()).get("minecraft").getAsString()).isEqualTo("1.12.2-custom");
        var version = json(task.getVersionJson().get().getAsFile().toPath());
        var libraries = version.getAsJsonArray("libraries");
        var names = names(libraries);
        assertThat(names).as(names.toString()).doesNotContain("com.google.guava:guava:21.0");
        assertThat(names).as(names.toString()).doesNotContain("com.mojang:patchy:1.3.9");
        assertThat(names.stream().noneMatch(name -> name.startsWith("org.lwjgl:") && name.contains(":natives-"))).as(names.toString()).isTrue();
        assertThat(names).isEqualTo(
                List.of(
                        "com.cleanroommc:cleanroom:1.0.0:universal",
                        "com.google.guava:guava:33.6.0-jre",
                        "com.paulscode:soundsystem:20120107",
                        "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64",
                        "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64",
                        "org.lwjgl:lwjgl:3.4.2",
                        "com.mojang:text2speech:1.10.3",
                        "com.mojang:text2speech:1.10.3",
                        "net.java.jinput:jinput-platform:2.0.5",
                        "net.java.jinput:jinput-platform:2.0.5",
                        "org.lwjgl:lwjgl:3.4.2",
                        "org.lwjgl:lwjgl:3.4.2",
                        "org.lwjgl:lwjgl:3.4.2"
                )
        );

        // Mojang hosts libraries no public Maven carries, so the manifest's url wins for what it names
        assertThat(
                library(libraries, "com.paulscode:soundsystem:20120107").getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString()
        ).isEqualTo("https://libraries.minecraft.net/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar");
        // A library this build moved off Minecraft's version keeps its actual resolved repository
        assertThat(
                library(libraries, "com.google.guava:guava:33.6.0-jre").getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString()
        ).isEqualTo("https://authority.example/releases/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar");
        assertThat(json(task.getInstallProfile().get().getAsFile().toPath()).getAsJsonObject("repositories").get("com.google.guava").getAsString()).isEqualTo(
                "https://authority.example/releases/"
        );

        // The loader's own jar travels inside the installer
        var embedded = library(libraries, "com.cleanroommc:cleanroom:1.0.0:universal").getAsJsonObject("downloads").getAsJsonObject("artifact");
        assertThat(embedded.get("url").getAsString()).isEqualTo("");

        // LWJGL natives are extracted by launchers and are therefore recognizable as client-only by installers
        var lwjglNative = library(libraries, "org.lwjgl:lwjgl:3.4.2", true, "linux", "x64");
        assertThat(lwjglNative.get("side").getAsString()).isEqualTo("client");
        assertThat(lwjglNative.getAsJsonObject("natives").get("linux").getAsString()).isEqualTo("natives-linux");
        assertThat(lwjglNative.getAsJsonObject("downloads").has("artifact")).isFalse();
        assertThat(lwjglNative.getAsJsonObject("downloads").getAsJsonObject("classifiers").keySet()).isEqualTo(Set.of("natives-linux"));
        var lwjglArmNative = library(libraries, "org.lwjgl:lwjgl:3.4.2", true, "linux", "arm64");
        assertThat(lwjglArmNative.getAsJsonObject("natives").get("linux").getAsString()).isEqualTo("natives-linux-arm64");
        library(libraries, "org.lwjgl:lwjgl:3.4.2", true, "windows", "x64");

        // Netty classifiers contain Java classes, so they remain on the classpath behind exact platform rules
        var nettyNative = library(libraries, "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64", false, "linux", "arm64");
        assertThat(nettyNative.getAsJsonObject("downloads").getAsJsonObject("artifact")).isNotNull();

        // Minecraft's own natives are extracted instead, which needs the classifier/platform shape
        var narrator = library(libraries, "com.mojang:text2speech:1.10.3", true, "linux", "x64");
        assertThat(narrator.getAsJsonObject("natives").get("linux").getAsString()).isEqualTo("natives-linux");
        var narratorClassifiers = narrator.getAsJsonObject("downloads").getAsJsonObject("classifiers");
        assertThat(narratorClassifiers.keySet()).isEqualTo(Set.of("natives-linux"));
        assertThat(narratorClassifiers.getAsJsonObject("natives-linux").get("url").getAsString()).isEqualTo(
                "https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar"
        );
        assertThat(narrator.getAsJsonObject("extract").getAsJsonArray("exclude").asList().stream().map(element -> element.getAsString()).toList()).isEqualTo(
                List.of("META-INF/")
        );

        var jinput = library(libraries, "net.java.jinput:jinput-platform:2.0.5", true, "windows", "x64");
        assertThat(jinput.getAsJsonObject("natives").keySet()).isEqualTo(Set.of("windows"));
        assertThat(jinput.getAsJsonObject("downloads").has("artifact")).isFalse();
        assertThat(jinput.getAsJsonObject("downloads").getAsJsonObject("classifiers").keySet()).isEqualTo(Set.of("natives-windows"));
    }

    @Test
    void dropsLwjgl2ThatVanillaWouldHaveContributed() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);

        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2", file("lwjgl-3.4.2.jar", "lwjgl3")));
        task.getLibraries().add(library(project, "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209", file("lwjgl-2.9.4-nightly-20150209.jar", "lwjgl2")));
        task.getNativeLibraries()
                .add(
                        library(
                                project,
                                "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209:natives-linux",
                                file("lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar", "lwjgl2 native")
                        )
                );
        task.getNativeLibraries()
                .add(library(project, "com.mojang:text2speech:1.10.3:natives-linux", file("text2speech-1.10.3-natives-linux.jar", "narrator")));
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var names = names(json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries"));
        assertThat(names).as(names.toString()).contains("org.lwjgl:lwjgl:3.4.2");
        assertThat(names).as(names.toString()).contains("com.mojang:text2speech:1.10.3");
        assertThat(names.stream().noneMatch(name -> name.startsWith("org.lwjgl.lwjgl:"))).as(names.toString()).isTrue();
    }

    @Test
    void keepsExactArtifactUrlsWhenAGroupSpansRepositories() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getLibraries().add(library(project, "example.shared:first:1.0", file("first-1.0.jar", "first"), "https://first.example/releases/"));
        task.getLibraries().add(library(project, "example.shared:second:1.0", file("second-1.0.jar", "second"), "https://second.example/releases/"));
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var versionLibraries = json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries");
        assertThat(
                library(versionLibraries, "example.shared:first:1.0").getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString()
        ).isEqualTo("https://first.example/releases/example/shared/first/1.0/first-1.0.jar");
        assertThat(
                library(versionLibraries, "example.shared:second:1.0").getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString()
        ).isEqualTo("https://second.example/releases/example/shared/second/1.0/second-1.0.jar");
        assertThat(json(task.getInstallProfile().get().getAsFile().toPath()).getAsJsonObject("repositories").has("example.shared")).isFalse();
    }

    @Test
    void embedsLibrariesResolvedFromMavenLocal() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        var mcttf = file("mcttf-0.1.0-beta+local.0.jar", "local mcttf");
        var coordinate = "example.local:mcttf:0.1.0-beta+local.0";
        task.getLibraries().add(library(project, coordinate, mcttf, directory.resolve("m2").toUri().toString()));
        task.getManifestUrls().put(coordinate, "https://example.invalid/mcttf.jar");
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var versionLibraries = json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries");
        var versionDownload = library(versionLibraries, coordinate).getAsJsonObject("downloads").getAsJsonObject("artifact");
        assertThat(versionDownload.get("url").getAsString()).isEqualTo("");
        assertThat(versionDownload.get("path").getAsString()).isEqualTo("example/local/mcttf/0.1.0-beta+local.0/mcttf-0.1.0-beta+local.0.jar");

        var profile = json(task.getInstallProfile().get().getAsFile().toPath());
        assertThat(
                library(profile.getAsJsonArray("libraries"), coordinate).getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString()
        ).isEqualTo("");
        assertThat(profile.getAsJsonObject("repositories").has("example.local")).isFalse();
        assertThat(
                Files.readString(
                        task.getEmbeddedLibraries().get().getAsFile().toPath().resolve("example/local/mcttf/0.1.0-beta+local.0/mcttf-0.1.0-beta+local.0.jar")
                )
        ).isEqualTo("local mcttf");
    }

    @Test
    void namesEveryArgumentOnce() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getTweakers().add("net.minecraftforge.fml.common.launcher.FMLTweaker");
        task.getVersionMeta()
                .set(
                        versionMeta(
                                "--username ${auth_player_name} --versionType ${version_type} " + "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker"
                        )
                );

        task.write();

        var arguments = json(task.getVersionJson().get().getAsFile().toPath()).get("minecraftArguments").getAsString();
        assertThat(arguments).isEqualTo(
                "--username ${auth_player_name} " + "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker " + "--versionType Cleanroom"
        );
        assertThat(count(arguments, "--versionType")).isEqualTo(1);
        assertThat(count(arguments, "--tweakClass")).isEqualTo(1);
        assertThat(arguments).doesNotContain("${version_type}");
    }

    private WriteInstallProfile task(Project project) {
        var task = project.getTasks().create("writeInstallProfile", WriteInstallProfile.class);
        task.getProfileName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0");
        task.getVersionId().set("Cleanroom-1.0.0");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getServerMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getMinimumJava().set(25);
        task.getRecommendedJava().set(25);
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0:universal");
        task.getUniversalUrl().set("https://maven.cleanroommc.com/com/cleanroommc/cleanroom/1.0.0/cleanroom-1.0.0-universal.jar");
        task.getLibraryExcludeRules().add("org.lwjgl.lwjgl:*");
        task.getReleaseTime().set("1970-01-01T00:00:00+0000");
        task.getInstallProfile().fileValue(directory.resolve("install_profile.json").toFile());
        task.getVersionJson().fileValue(directory.resolve("version.json").toFile());
        task.getEmbeddedLibraries().set(directory.resolve("maven-local").toFile());
        return task;
    }

    private static VersionMeta versionMeta(String minecraftArguments, String... libraries) {
        return versionMetaWithId("1.12.2", minecraftArguments, libraries);
    }

    private static VersionMeta versionMetaWithId(String minecraftVersion, String minecraftArguments, String... libraries) {
        var entries = new ArrayList<VersionMeta.Library>();
        for (var name : libraries) {
            entries.add(
                    new VersionMeta.Library(
                            new VersionMeta.Downloads(new VersionMeta.Download("ignored", "sha1", 1, "https://example.invalid/ignored.jar"), Map.of()),
                            name,
                            null,
                            null,
                            null
                    )
            );
        }
        return new VersionMeta(
                null,
                new VersionMeta.AssetIndex("1.12", 1, null, "sha1", 1, "https://example.invalid/1.12.json"),
                "1.12",
                0,
                Map.of("client", new VersionMeta.Download(null, "sha1", 1, "https://example.invalid/client.jar")),
                minecraftVersion,
                new VersionMeta.JavaVersion("jre-legacy", 8),
                entries,
                null,
                "net.minecraft.client.main.Main",
                minecraftArguments,
                18,
                "2017-09-18T08:39:46+00:00",
                "2017-09-18T08:39:46+00:00",
                "release"
        );
    }

    private static LibraryArtifact library(Project project, String coordinate, Path file) {
        return library(project, coordinate, file, "https://repo.maven.apache.org/maven2/");
    }

    private static LibraryArtifact library(Project project, String coordinate, Path file, String repositoryUrl) {
        var library = project.getObjects().newInstance(LibraryArtifact.class);
        library.getCoordinate().set(coordinate);
        library.getFile().fileValue(file.toFile());
        library.getRepositoryUrl().set(repositoryUrl);
        return library;
    }

    private static JsonObject library(JsonArray libraries, String name) {
        for (var element : libraries) {
            if (element.getAsJsonObject().get("name").getAsString().equals(name)) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError("No library named " + name + " in " + libraries);
    }

    private static JsonObject library(JsonArray libraries, String name, boolean natives, String os, String arch) {
        for (var element : libraries) {
            var library = element.getAsJsonObject();
            if (!library.get("name").getAsString().equals(name) || library.has("natives") != natives) {
                continue;
            }
            var ruleOs = library.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonObject("os");
            if (ruleOs.get("name").getAsString().equals(os) && ruleOs.get("arch").getAsString().equals(arch)) {
                return library;
            }
        }
        throw new AssertionError("No library named " + name + " for " + os + "/" + arch + " in " + libraries);
    }

    private static List<String> names(JsonArray libraries) {
        return libraries.asList().stream().map(element -> element.getAsJsonObject().get("name").getAsString()).toList();
    }

    private static int count(String arguments, String option) {
        return (int) List.of(arguments.split(" ")).stream().filter(option::equals).count();
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private Path file(String name, String contents) throws IOException {
        return Files.writeString(directory.resolve(name), contents, StandardCharsets.UTF_8);
    }

}
