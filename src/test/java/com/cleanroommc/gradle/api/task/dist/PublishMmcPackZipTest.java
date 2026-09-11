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

import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.GradleException;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PublishMmcPackZipTest {

    @TempDir
    Path directory;

    @Test
    void executesAsAValidatedCacheableGradleTask() throws IOException {
        var universal = file("cleanroom-2.0.0-universal.jar", "cleanroom");
        var foundation = file("foundation-2.0.0.jar", "foundation");
        var lwjgl = file("lwjgl-3.4.2.jar", "lwjgl");
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'mmc-task-test'\n");
        Files.writeString(
                directory.resolve("build.gradle"),
                """
                import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip
                import com.cleanroommc.gradle.api.util.dist.LibraryArtifact
                
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom.mode = 'vanilla'
                
                def foundation = objects.newInstance(LibraryArtifact)
                foundation.coordinate = 'top.outlands:foundation:2.0.0'
                foundation.file = file('%s')
                foundation.repositoryUrl = 'https://repo.maven.apache.org/maven2/'
                
                def lwjgl = objects.newInstance(LibraryArtifact)
                lwjgl.coordinate = 'org.lwjgl:lwjgl:3.4.2'
                lwjgl.file = file('%s')
                lwjgl.repositoryUrl = 'https://repo.maven.apache.org/maven2/'
                
                tasks.register('publishFixture', PublishMmcPackZip) {
                    instanceName = 'Cleanroom'
                    cleanroomVersion = '2.0.0'
                    mainClass = 'top.outlands.foundation.boot.Foundation'
                    tweakers.add('net.minecraftforge.fml.common.launcher.FMLTweaker')
                    compatibleJavaMajors.add(25)
                    universalCoordinate = 'com.cleanroommc:cleanroom:2.0.0:universal'
                    universalUrl = 'https://example.invalid/cleanroom.jar'
                    universalJar = file('%s')
                    libraries.add(foundation)
                    libraries.add(lwjgl)
                    archiveFile = layout.buildDirectory.file('cleanroom-mmc.zip')
                    installerArchiveFile = layout.buildDirectory.file('cleanroom-mmc-installer.zip')
                }
                """.formatted(
                        escape(foundation),
                        escape(lwjgl),
                        escape(universal)
                )
        );

        var first = runner("publishFixture", "--configuration-cache").build();
        assertThat(first.task(":publishFixture").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.isRegularFile(directory.resolve("build/cleanroom-mmc.zip"))).isTrue();
        assertThat(Files.exists(directory.resolve("build/cleanroom-mmc-overlay.zip"))).isFalse();
        assertThat(first.getOutput()).contains("Configuration cache entry stored");

        var second = runner("publishFixture", "--configuration-cache").build();
        assertThat(second.task(":publishFixture").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(second.getOutput()).contains("Reusing configuration cache");
    }

    @Test
    void publishesMinimalImportWithHashedDownloads() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = project.getTasks().create("publishMmcPackZip", PublishMmcPackZip.class);

        var universal = file("cleanroom-1.0.0-universal.jar", "cleanroom");
        var foundation = file("foundation-1.2.3.jar", "foundation");
        var inherited = file("guava-21.0.jar", "minecraft already supplies this");
        var lwjgl = file("lwjgl-3.4.2.jar", "lwjgl");
        var windows = file("lwjgl-3.4.2-natives-windows.jar", "windows native");
        var windowsArm = file("lwjgl-3.4.2-natives-windows-arm64.jar", "windows arm native");
        var macArm = file("lwjgl-3.4.2-natives-macos-arm64.jar", "mac arm native");

        task.getInstanceName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0");
        task.getMinecraftVersion().set("1.12.2-custom");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getTweakers().set(List.of("net.minecraftforge.fml.common.launcher.FMLTweaker"));
        task.getCompatibleJavaMajors().add(25);
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0:universal");
        task.getUniversalUrl().set("https://maven.cleanroommc.com/com/cleanroommc/cleanroom/1.0.0/cleanroom-1.0.0-universal.jar");
        task.getUniversalJar().fileValue(universal.toFile());
        task.getLibraries().add(library(project, "top.outlands:foundation:1.2.3", foundation, "https://packages.cleanroommc.com/releases/"));
        task.getLibraries().add(library(project, "com.google.guava:guava:21.0", inherited));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2", lwjgl));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-windows", windows));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-windows-arm64", windowsArm));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-macos-arm64", macArm));
        task.getInheritedLibraries().addAll("com.google.guava:guava:21.0", "com.ibm.icu:icu4j-core-mojang:51.2", "com.mojang:patchy:1.3.9");
        task.getMinecraftExcludeRules().addAll("com.ibm.icu:icu4j-core-mojang", "com.mojang:*", "example:unrelated");
        task.getArchiveFile().fileValue(directory.resolve("cleanroom-mmc.zip").toFile());
        task.getInstallerArchiveFile().fileValue(directory.resolve("cleanroom-mmc-installer.zip").toFile());

        task.publish();

        try (var zip = new ZipFile(task.getArchiveFile().get().getAsFile())) {
            assertThat(entries(zip)).isEqualTo(
                    Set.of(
                            "instance.cfg",
                            "mmc-pack.json",
                            "patches/org.lwjgl.json",
                            "patches/net.minecraftforge.json",
                            "libraries/icu4j-core-mojang-999999.0-empty.jar",
                            "libraries/patchy-999999.0-empty.jar"
                    )
            );
            assertThat(text(zip, "instance.cfg")).isEqualTo("InstanceType=OneSix\nname=Cleanroom 1.0.0\niconKey=default\n");

            var pack = json(zip, "mmc-pack.json");
            assertThat(pack.keySet()).isEqualTo(Set.of("formatVersion", "components"));
            assertThat(pack.get("formatVersion").getAsInt()).isEqualTo(1);
            var components = pack.getAsJsonArray("components");
            assertThat(components.size()).isEqualTo(3);
            assertThat(components.get(0).getAsJsonObject().keySet()).isEqualTo(Set.of("uid", "version", "important"));
            assertThat(components.get(0).getAsJsonObject().get("uid").getAsString()).isEqualTo("net.minecraft");
            assertThat(components.get(0).getAsJsonObject().get("version").getAsString()).isEqualTo("1.12.2-custom");
            assertThat(components.get(1).getAsJsonObject().keySet()).isEqualTo(Set.of("uid", "version"));
            assertThat(components.get(1).getAsJsonObject().get("uid").getAsString()).isEqualTo("org.lwjgl");
            assertThat(components.get(1).getAsJsonObject().get("version").getAsString()).isEqualTo("3.4.2");
            assertThat(components.get(2).getAsJsonObject().keySet()).isEqualTo(Set.of("uid", "version"));
            assertThat(components.get(2).getAsJsonObject().get("uid").getAsString()).isEqualTo("net.minecraftforge");

            var patch = json(zip, "patches/net.minecraftforge.json");
            assertThat(patch.keySet()).isEqualTo(
                    Set.of("formatVersion", "name", "uid", "version", "requires", "mainClass", "compatibleJavaMajors", "+tweakers", "libraries")
            );
            assertThat(patch.has("order")).isFalse();
            assertThat(patch.has("releaseTime")).isFalse();
            assertThat(patch.has("+jvmArgs")).isFalse();
            assertThat(patch.getAsJsonArray("compatibleJavaMajors").asList().stream().map(element -> element.getAsInt()).toList()).isEqualTo(List.of(25));
            var requirements = patch.getAsJsonArray("requires");
            assertThat(requirements.size()).isEqualTo(2);
            assertThat(requirements.get(0).getAsJsonObject().get("uid").getAsString()).isEqualTo("net.minecraft");
            assertThat(requirements.get(0).getAsJsonObject().get("equals").getAsString()).isEqualTo("1.12.2-custom");
            assertThat(requirements.get(1).getAsJsonObject().get("uid").getAsString()).isEqualTo("org.lwjgl");
            assertThat(requirements.get(1).getAsJsonObject().get("equals").getAsString()).isEqualTo("3.4.2");

            var libraries = patch.getAsJsonArray("libraries");
            assertThat(libraries.size()).as(libraries.toString()).isEqualTo(4);
            assertThat(
                    libraries.asList().stream().noneMatch(element -> element.getAsJsonObject().get("name").getAsString().equals("com.google.guava:guava:21.0"))
            ).isTrue();
            assertThat(libraries.asList().stream().noneMatch(element -> element.getAsJsonObject().get("name").getAsString().startsWith("org.lwjgl:"))).isTrue();

            var universalLibrary = library(libraries, "com.cleanroommc:cleanroom:1.0.0:universal", false);
            assertDownload(universalLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"), universal, task.getUniversalUrl().get());

            var foundationLibrary = library(libraries, "top.outlands:foundation:1.2.3", false);
            assertDownload(
                    foundationLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"),
                    foundation,
                    "https://packages.cleanroommc.com/releases/top/outlands/foundation/1.2.3/foundation-1.2.3.jar"
            );

            assertBlocked(libraries, "com.ibm.icu:icu4j-core-mojang:999999.0-empty");
            assertBlocked(libraries, "com.mojang:patchy:999999.0-empty");
            assertEmptyJar(bytes(zip, "libraries/icu4j-core-mojang-999999.0-empty.jar"));
            assertEmptyJar(bytes(zip, "libraries/patchy-999999.0-empty.jar"));

            var lwjglPatch = json(zip, "patches/org.lwjgl.json");
            assertThat(lwjglPatch.keySet()).isEqualTo(Set.of("formatVersion", "name", "uid", "version", "libraries"));
            assertThat(lwjglPatch.get("uid").getAsString()).isEqualTo("org.lwjgl");
            assertThat(lwjglPatch.get("version").getAsString()).isEqualTo("3.4.2");
            var lwjglLibraries = lwjglPatch.getAsJsonArray("libraries");
            assertThat(lwjglLibraries.size()).as(lwjglLibraries.toString()).isEqualTo(2);
            assertThat(
                    lwjglLibraries.asList().stream().noneMatch(element -> element.getAsJsonObject().get("name").getAsString().startsWith("org.lwjgl.lwjgl:"))
            ).isTrue();

            var lwjglLibrary = library(lwjglLibraries, "org.lwjgl:lwjgl:3.4.2", false);
            assertDownload(
                    lwjglLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"),
                    lwjgl,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.4.2/lwjgl-3.4.2.jar"
            );

            var lwjglNatives = library(lwjglLibraries, "org.lwjgl:lwjgl:3.4.2", true);
            var nativeMap = lwjglNatives.getAsJsonObject("natives");
            assertThat(nativeMap.get("windows").getAsString()).isEqualTo("natives-windows");
            assertThat(nativeMap.get("windows-arm64").getAsString()).isEqualTo("natives-windows-arm64");
            assertThat(nativeMap.get("osx-arm64").getAsString()).isEqualTo("natives-macos-arm64");
            var classifiers = lwjglNatives.getAsJsonObject("downloads").getAsJsonObject("classifiers");
            assertDownload(
                    classifiers.getAsJsonObject("natives-windows"),
                    windows,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.4.2/lwjgl-3.4.2-natives-windows.jar"
            );
            assertDownload(
                    classifiers.getAsJsonObject("natives-windows-arm64"),
                    windowsArm,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.4.2/lwjgl-3.4.2-natives-windows-arm64.jar"
            );
            assertDownload(
                    classifiers.getAsJsonObject("natives-macos-arm64"),
                    macArm,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.4.2/lwjgl-3.4.2-natives-macos-arm64.jar"
            );
        }

        assertThat(Files.exists(directory.resolve("cleanroom-mmc-overlay.zip"))).isFalse();
    }

    @Test
    void embedsTheUniversalJarForALocalBuild() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = project.getTasks().create("publishMmcPackZip", PublishMmcPackZip.class);

        var universal = file("cleanroom-1.0.0+local.4-universal.jar", "local cleanroom");
        var foundation = file("foundation-1.2.3.jar", "foundation");
        var mcttf = file("mcttf-0.1.0-beta+local.0.jar", "local mcttf");
        var lwjgl = file("lwjgl-3.4.2.jar", "lwjgl");

        task.getInstanceName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0+local.4");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0+local.4:universal");
        task.getUniversalUrl().set("https://maven.cleanroommc.com/never/downloaded.jar");
        task.getUniversalJar().fileValue(universal.toFile());
        task.getInheritedLibraries().addAll("com.ibm.icu:icu4j-core-mojang:51.2", "com.mojang:patchy:1.3.9");
        task.getMinecraftExcludeRules().addAll("com.ibm.icu:icu4j-core-mojang", "*:patchy");
        task.getLibraries().add(library(project, "top.outlands:foundation:1.2.3", foundation));
        task.getLibraries().add(library(project, "com.cleanroommc:mcttf:0.1.0-beta+local.0", mcttf, directory.resolve("m2").toUri().toString()));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2", lwjgl));
        task.getArchiveFile().fileValue(directory.resolve("cleanroom-local.zip").toFile());
        task.getInstallerArchiveFile().fileValue(directory.resolve("cleanroom-local-installer.zip").toFile());

        task.publish();

        try (var zip = new ZipFile(task.getArchiveFile().get().getAsFile())) {
            var embedded = "libraries/cleanroom-1.0.0+local.4-universal.jar";
            var localDependency = "libraries/mcttf-0.1.0-beta+local.0.jar";
            assertThat(entries(zip)).isEqualTo(
                    Set.of(
                            "instance.cfg",
                            "mmc-pack.json",
                            "patches/org.lwjgl.json",
                            "patches/net.minecraftforge.json",
                            "libraries/icu4j-core-mojang-999999.0-empty.jar",
                            "libraries/patchy-999999.0-empty.jar",
                            embedded,
                            localDependency
                    )
            );
            assertThat(text(zip, embedded)).isEqualTo("local cleanroom");
            assertThat(text(zip, localDependency)).isEqualTo("local mcttf");

            var libraries = json(zip, "patches/net.minecraftforge.json").getAsJsonArray("libraries");
            var universalLibrary = library(libraries, "com.cleanroommc:cleanroom:1.0.0+local.4:universal", false);
            assertThat(universalLibrary.get("MMC-hint").getAsString()).isEqualTo("local");
            var download = universalLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact");
            assertThat(download.keySet()).isEqualTo(Set.of("path", "sha1", "size"));
            assertThat(download.get("path").getAsString()).isEqualTo("com/cleanroommc/cleanroom/1.0.0+local.4/cleanroom-1.0.0+local.4-universal.jar");
            assertThat(download.get("sha1").getAsString()).isEqualTo(DigestUtils.sha1Hex(Files.readAllBytes(universal)));
            assertThat(download.get("size").getAsLong()).isEqualTo(Files.size(universal));

            var mcttfLibrary = library(libraries, "com.cleanroommc:mcttf:0.1.0-beta+local.0", false);
            assertThat(mcttfLibrary.get("MMC-hint").getAsString()).isEqualTo("local");
            assertThat(mcttfLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact").keySet()).isEqualTo(Set.of("path", "sha1", "size"));

            var foundationLibrary = library(libraries, "top.outlands:foundation:1.2.3", false);
            assertThat(foundationLibrary.has("MMC-hint")).isFalse();
            assertDownload(
                    foundationLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"),
                    foundation,
                    "https://repo.maven.apache.org/maven2/top/outlands/foundation/1.2.3/foundation-1.2.3.jar"
            );
        }

        // The installer ships the universal jar in its own maven layout, so its copy of the pack drops it
        try (var zip = new ZipFile(task.getInstallerArchiveFile().get().getAsFile())) {
            assertThat(entries(zip)).isEqualTo(
                    Set.of(
                            "instance.cfg",
                            "mmc-pack.json",
                            "patches/org.lwjgl.json",
                            "patches/net.minecraftforge.json",
                            "libraries/icu4j-core-mojang-999999.0-empty.jar",
                            "libraries/patchy-999999.0-empty.jar",
                            "libraries/mcttf-0.1.0-beta+local.0.jar"
                    )
            );
            assertThat(text(zip, "libraries/mcttf-0.1.0-beta+local.0.jar")).isEqualTo("local mcttf");
        }
    }

    @Test
    void rejectsMixedLwjglComponentVersions() throws IOException {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = project.getTasks().create("publishMmcPackZip", PublishMmcPackZip.class);
        var universal = file("cleanroom-1.0.0-universal.jar", "cleanroom");

        task.getInstanceName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0:universal");
        task.getUniversalUrl().set("https://maven.cleanroommc.com/cleanroom.jar");
        task.getUniversalJar().fileValue(universal.toFile());
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2", file("lwjgl-3.4.2.jar", "lwjgl")));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl-stb:3.4.0", file("lwjgl-stb-3.4.0.jar", "stb")));
        task.getArchiveFile().fileValue(directory.resolve("cleanroom-mmc.zip").toFile());
        task.getInstallerArchiveFile().fileValue(directory.resolve("cleanroom-mmc-installer.zip").toFile());

        var failure = catchThrowableOfType(task::publish, GradleException.class);
        assertThat(failure).hasMessageContaining("requires exactly one LWJGL version");
    }

    private Path file(String name, String contents) throws IOException {
        return Files.writeString(directory.resolve(name), contents, StandardCharsets.UTF_8);
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withArguments(arguments).withPluginClasspath();
    }

    private static String escape(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\").replace("'", "\\'");
    }

    private static LibraryArtifact library(org.gradle.api.Project project, String coordinate, Path file) {
        return library(project, coordinate, file, "https://repo.maven.apache.org/maven2/");
    }

    private static LibraryArtifact library(org.gradle.api.Project project, String coordinate, Path file, String repositoryUrl) {
        var library = project.getObjects().newInstance(LibraryArtifact.class);
        library.getCoordinate().set(coordinate);
        library.getFile().fileValue(file.toFile());
        library.getRepositoryUrl().set(repositoryUrl);
        return library;
    }

    private static Set<String> entries(ZipFile zip) {
        var entries = new HashSet<String>();
        zip.stream().forEach(entry -> entries.add(entry.getName()));
        return entries;
    }

    private static String text(ZipFile zip, String path) throws IOException {
        return new String(bytes(zip, path), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(ZipFile zip, String path) throws IOException {
        try (var stream = zip.getInputStream(zip.getEntry(path))) {
            return stream.readAllBytes();
        }
    }

    private static JsonObject json(ZipFile zip, String path) throws IOException {
        return JsonParser.parseString(text(zip, path)).getAsJsonObject();
    }

    private static JsonObject library(com.google.gson.JsonArray libraries, String coordinate, boolean natives) {
        var matches = new ArrayList<JsonObject>();
        for (var element : libraries) {
            var library = element.getAsJsonObject();
            if (library.get("name").getAsString().equals(coordinate) && library.has("natives") == natives) {
                matches.add(library);
            }
        }
        assertThat(matches.size()).as("library " + coordinate + " (natives=" + natives + ")").isEqualTo(1);
        return matches.getFirst();
    }

    private static void assertBlocked(com.google.gson.JsonArray libraries, String coordinate) {
        var blocked = library(libraries, coordinate, false);
        assertThat(blocked.keySet()).isEqualTo(Set.of("name", "MMC-hint"));
        assertThat(blocked.get("MMC-hint").getAsString()).isEqualTo("local");
    }

    private static void assertEmptyJar(byte[] contents) throws IOException {
        assertThat(contents.length).isEqualTo(22);
        try (var jar = new ZipInputStream(new ByteArrayInputStream(contents))) {
            assertThat(jar.getNextEntry()).isNull();
        }
    }

    private static void assertDownload(JsonObject download, Path file, String url) throws IOException {
        assertThat(download).isNotNull();
        assertThat(download.keySet()).isEqualTo(Set.of("url", "sha1", "size"));
        assertThat(download.get("url").getAsString()).isEqualTo(url);
        assertThat(download.get("sha1").getAsString()).isEqualTo(DigestUtils.sha1Hex(Files.readAllBytes(file)));
        assertThat(download.get("size").getAsLong()).isEqualTo(Files.size(file));
    }

}
