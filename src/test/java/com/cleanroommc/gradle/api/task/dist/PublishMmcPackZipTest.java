package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishMmcPackZipTest {

    @TempDir
    Path directory;

    @Test
    void executesAsAValidatedCacheableGradleTask() throws IOException {
        var universal = file("cleanroom-2.0.0-universal.jar", "cleanroom");
        var foundation = file("foundation-2.0.0.jar", "foundation");
        Files.writeString(directory.resolve("settings.gradle"), "rootProject.name = 'mmc-task-test'\n");
        Files.writeString(directory.resolve("build.gradle"), """
                import com.cleanroommc.gradle.api.ext.ProjectMode
                import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip
                import com.cleanroommc.gradle.api.util.dist.LibraryArtifact

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom.mode = ProjectMode.VANILLA

                def foundation = objects.newInstance(LibraryArtifact)
                foundation.coordinate = 'top.outlands:foundation:2.0.0'
                foundation.file = file('%s')

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
                    repositoryUrls.put('*', 'https://repo.maven.apache.org/maven2/')
                    archiveFile = layout.buildDirectory.file('cleanroom-mmc.zip')
                }
                """.formatted(escape(foundation), escape(universal)));

        var first = runner("publishFixture", "--configuration-cache").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":publishFixture").getOutcome());
        assertTrue(Files.isRegularFile(directory.resolve("build/cleanroom-mmc.zip")));
        assertFalse(Files.exists(directory.resolve("build/cleanroom-mmc-overlay.zip")));
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));

        var second = runner("publishFixture", "--configuration-cache").build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":publishFixture").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
    }

    @Test
    void publishesMinimalImportWithHashedDownloads() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = project.getTasks().create("publishMmcPackZip", PublishMmcPackZip.class);

        var universal = file("cleanroom-1.0.0-universal.jar", "cleanroom");
        var foundation = file("foundation-1.2.3.jar", "foundation");
        var inherited = file("guava-21.0.jar", "minecraft already supplies this");
        var lwjgl = file("lwjgl-3.3.6.jar", "lwjgl");
        var windows = file("lwjgl-3.3.6-natives-windows.jar", "windows native");
        var windowsArm = file("lwjgl-3.3.6-natives-windows-arm64.jar", "windows arm native");
        var macArm = file("lwjgl-3.3.6-natives-macos-arm64.jar", "mac arm native");

        task.getInstanceName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getTweakers().set(List.of("net.minecraftforge.fml.common.launcher.FMLTweaker"));
        task.getCompatibleJavaMajors().add(25);
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0:universal");
        task.getUniversalUrl().set("https://maven.cleanroommc.com/com/cleanroommc/cleanroom/1.0.0/cleanroom-1.0.0-universal.jar");
        task.getUniversalJar().fileValue(universal.toFile());
        task.getLibraries().add(library(project, "top.outlands:foundation:1.2.3", foundation));
        task.getLibraries().add(library(project, "com.google.guava:guava:21.0", inherited));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6", lwjgl));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6:natives-windows", windows));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6:natives-windows-arm64", windowsArm));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6:natives-macos-arm64", macArm));
        task.getInheritedLibraries().add("com.google.guava:guava:21.0");
        task.getRepositoryUrls().put("*", "https://repo.maven.apache.org/maven2/");
        task.getRepositoryUrls().put("top.outlands", "https://maven.cleanroommc.com/");
        task.getArchiveFile().fileValue(directory.resolve("cleanroom-mmc.zip").toFile());

        task.publish();

        try (var zip = new ZipFile(task.getArchiveFile().get().getAsFile())) {
            assertEquals(Set.of("instance.cfg", "mmc-pack.json", "patches/net.minecraftforge.json"), entries(zip));
            assertEquals("InstanceType=OneSix\nname=Cleanroom\niconKey=default\n", text(zip, "instance.cfg"));

            var pack = json(zip, "mmc-pack.json");
            assertEquals(Set.of("formatVersion", "components"), pack.keySet());
            assertEquals(1, pack.get("formatVersion").getAsInt());
            var components = pack.getAsJsonArray("components");
            assertEquals(2, components.size());
            assertEquals(Set.of("uid", "version", "important"), components.get(0).getAsJsonObject().keySet());
            assertEquals("net.minecraft", components.get(0).getAsJsonObject().get("uid").getAsString());
            assertEquals(Set.of("uid", "version"), components.get(1).getAsJsonObject().keySet());
            assertEquals("net.minecraftforge", components.get(1).getAsJsonObject().get("uid").getAsString());

            var patch = json(zip, "patches/net.minecraftforge.json");
            assertEquals(Set.of("formatVersion", "name", "uid", "version", "requires", "mainClass",
                            "compatibleJavaMajors", "+tweakers", "libraries"),
                    patch.keySet());
            assertFalse(patch.has("order"));
            assertFalse(patch.has("releaseTime"));
            assertFalse(patch.has("+jvmArgs"));
            assertEquals(List.of(25), patch.getAsJsonArray("compatibleJavaMajors").asList().stream()
                    .map(element -> element.getAsInt()).toList());

            var libraries = patch.getAsJsonArray("libraries");
            assertEquals(4, libraries.size(), libraries.toString());
            assertTrue(libraries.asList().stream().noneMatch(element -> element.getAsJsonObject().has("MMC-hint")));
            assertTrue(libraries.asList().stream().noneMatch(element ->
                    element.getAsJsonObject().get("name").getAsString().equals("com.google.guava:guava:21.0")));

            var universalLibrary = library(libraries, "com.cleanroommc:cleanroom:1.0.0:universal", false);
            assertDownload(universalLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"),
                    universal, task.getUniversalUrl().get());

            var foundationLibrary = library(libraries, "top.outlands:foundation:1.2.3", false);
            assertDownload(foundationLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"), foundation,
                    "https://maven.cleanroommc.com/top/outlands/foundation/1.2.3/foundation-1.2.3.jar");

            var lwjglLibrary = library(libraries, "org.lwjgl:lwjgl:3.3.6", false);
            assertDownload(lwjglLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"), lwjgl,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6.jar");

            var lwjglNatives = library(libraries, "org.lwjgl:lwjgl:3.3.6", true);
            var nativeMap = lwjglNatives.getAsJsonObject("natives");
            assertEquals("natives-windows", nativeMap.get("windows").getAsString());
            assertEquals("natives-windows-arm64", nativeMap.get("windows-arm64").getAsString());
            assertEquals("natives-macos-arm64", nativeMap.get("osx-arm64").getAsString());
            var classifiers = lwjglNatives.getAsJsonObject("downloads").getAsJsonObject("classifiers");
            assertDownload(classifiers.getAsJsonObject("natives-windows"), windows,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6-natives-windows.jar");
            assertDownload(classifiers.getAsJsonObject("natives-windows-arm64"), windowsArm,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6-natives-windows-arm64.jar");
            assertDownload(classifiers.getAsJsonObject("natives-macos-arm64"), macArm,
                    "https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6-natives-macos-arm64.jar");
        }

        assertFalse(Files.exists(directory.resolve("cleanroom-mmc-overlay.zip")));
    }

    @Test
    void embedsTheUniversalJarForALocalBuild() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = project.getTasks().create("publishMmcPackZip", PublishMmcPackZip.class);

        var universal = file("cleanroom-1.0.0+build.4-universal.jar", "local cleanroom");
        var foundation = file("foundation-1.2.3.jar", "foundation");

        task.getInstanceName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0+build.4");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0+build.4:universal");
        task.getUniversalUrl().set("https://maven.cleanroommc.com/never/downloaded.jar");
        task.getUniversalJar().fileValue(universal.toFile());
        task.getEmbedUniversalJar().set(true);
        task.getLibraries().add(library(project, "top.outlands:foundation:1.2.3", foundation));
        task.getRepositoryUrls().put("*", "https://repo.maven.apache.org/maven2/");
        task.getArchiveFile().fileValue(directory.resolve("cleanroom-local.zip").toFile());

        task.publish();

        try (var zip = new ZipFile(task.getArchiveFile().get().getAsFile())) {
            var embedded = "libraries/cleanroom-1.0.0+build.4-universal.jar";
            assertEquals(Set.of("instance.cfg", "mmc-pack.json", "patches/net.minecraftforge.json", embedded),
                    entries(zip));
            assertEquals("local cleanroom", text(zip, embedded));

            var libraries = json(zip, "patches/net.minecraftforge.json").getAsJsonArray("libraries");
            var universalLibrary = library(libraries, "com.cleanroommc:cleanroom:1.0.0+build.4:universal", false);
            assertEquals("local", universalLibrary.get("MMC-hint").getAsString());
            var download = universalLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact");
            assertEquals(Set.of("path", "sha1", "size"), download.keySet());
            assertEquals("com/cleanroommc/cleanroom/1.0.0+build.4/cleanroom-1.0.0+build.4-universal.jar",
                    download.get("path").getAsString());
            assertEquals(DigestUtils.sha1Hex(Files.readAllBytes(universal)), download.get("sha1").getAsString());
            assertEquals(Files.size(universal), download.get("size").getAsLong());

            var foundationLibrary = library(libraries, "top.outlands:foundation:1.2.3", false);
            assertFalse(foundationLibrary.has("MMC-hint"));
            assertDownload(foundationLibrary.getAsJsonObject("downloads").getAsJsonObject("artifact"), foundation,
                    "https://repo.maven.apache.org/maven2/top/outlands/foundation/1.2.3/foundation-1.2.3.jar");
        }
    }

    private Path file(String name, String contents) throws IOException {
        return Files.writeString(directory.resolve(name), contents, StandardCharsets.UTF_8);
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withArguments(arguments)
                .withPluginClasspath();
    }

    private static String escape(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\").replace("'", "\\'");
    }

    private static LibraryArtifact library(org.gradle.api.Project project, String coordinate, Path file) {
        var library = project.getObjects().newInstance(LibraryArtifact.class);
        library.getCoordinate().set(coordinate);
        library.getFile().fileValue(file.toFile());
        return library;
    }

    private static Set<String> entries(ZipFile zip) {
        var entries = new HashSet<String>();
        zip.stream().forEach(entry -> entries.add(entry.getName()));
        return entries;
    }

    private static String text(ZipFile zip, String path) throws IOException {
        try (var stream = zip.getInputStream(zip.getEntry(path))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
        assertEquals(1, matches.size(), "library " + coordinate + " (natives=" + natives + ")");
        return matches.getFirst();
    }

    private static void assertDownload(JsonObject download, Path file, String url) throws IOException {
        assertNotNull(download);
        assertEquals(Set.of("url", "sha1", "size"), download.keySet());
        assertEquals(url, download.get("url").getAsString());
        assertEquals(DigestUtils.sha1Hex(Files.readAllBytes(file)), download.get("sha1").getAsString());
        assertEquals(Files.size(file), download.get("size").getAsLong());
    }
}
