/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cleanroom's library loader picks its boot libraries off the launch classpath by file name, so the boot list has to
 * name each one exactly as the installer profile and the MMC pack lay it out.
 */
class BootListNamesTest extends BaseFunctionalTest {

    private static Set<String> fileNames(Iterable<?> libraries, boolean byPath) {
        var names = new HashSet<String>();
        for (var element : libraries) {
            var library = (JsonObject) element;
            if (byPath) {
                var downloads = library.getAsJsonObject("downloads");
                if (downloads != null && downloads.has("artifact")) {
                    var path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                    names.add(path.substring(path.lastIndexOf('/') + 1));
                }
            } else {
                var parts = library.get("name").getAsString().split(":");
                names.add(parts[1] + "-" + parts[2] + (parts.length > 3 ? "-" + parts[3] : "") + ".jar");
            }
        }
        return names;
    }

    @Test
    void bootLibrariesKeepTheirFileNamesInTheInstallerAndThePack() throws IOException {
        module("example", "boot", "1.0", null);
        module("example", "tools", "2.1", "shaded");
        module("example", "game", "1.0", null);
        // The MMC pack needs one LWJGL version for its LWJGL component
        module("org.lwjgl", "lwjgl", "3.4.3", null);
        new JarOutputStream(Files.newOutputStream(this.projectDir.resolve("universal.jar"))).close();
        this.project.loader(
            """
            group = 'com.cleanroommc'
            version = '0.1.0'
            apply plugin: 'maven-publish'
            publishing.repositories.maven {
                url = layout.buildDirectory.dir('publish-repository')
            }
            repositories {
                maven { url = layout.projectDirectory.dir('fixture-repo') }
            }
            dependencies {
                implementation 'example:boot:1.0'
                implementation 'example:tools:2.1:shaded'
                implementation 'example:game:1.0'
                implementation 'org.lwjgl:lwjgl:3.4.3'
            }
            cleanroom.loader.bootLibraries = ['example:boot', 'example:tools']
            // Only the library names are under test, so neither Minecraft nor the loader gets built
            def universal = layout.projectDirectory.file('universal.jar')
            tasks.named('writeBootList') { classes = layout.projectDirectory.dir('classes') }
            tasks.named('writeInstallProfile') { universalJar = universal }
            tasks.named('publishMmcPackZip') { universalJar = universal }
            gradle.projectsEvaluated {
                ['vanilla', 'distributionLibraries', 'distributionNatives'].each { name ->
                    configurations.named(name) { withDependencies { it.clear() } }
                }
            }
            """
        );

        this.project.runner("writeBootList", "writeInstallProfile", "publishMmcPackZip").build();

        var boot = new HashSet<String>();
        for (var line : Files.readAllLines(this.projectDir.resolve(
            "build/generated/bootList/META-INF/cleanroom/boot.list"
        ))) {
            if (line.startsWith("library ")) {
                boot.add(line.substring("library ".length()));
            }
        }
        assertThat(boot).containsExactlyInAnyOrder("boot-1.0.jar", "tools-2.1-shaded.jar");

        var dist = this.projectDir.resolve("build/cleanroom_gradle/dist");
        var version = JsonParser.parseString(Files.readString(dist.resolve("version.json"))).getAsJsonObject();
        assertThat(fileNames(version.getAsJsonArray("libraries"), true)).as("installer").containsAll(boot);

        try (var pack = new ZipFile(dist.resolve("mmc-installer.zip").toFile())) {
            var patch = JsonParser.parseReader(new InputStreamReader(
                pack.getInputStream(pack.getEntry("patches/net.minecraftforge.json")),
                StandardCharsets.UTF_8
            ))
                .getAsJsonObject();
            assertThat(fileNames(patch.getAsJsonArray("libraries"), false)).as("MMC pack").containsAll(boot);
        }
    }

    private void module(String group, String artifact, String version, String classifier) throws IOException {
        var directory = this.projectDir
            .resolve("fixture-repo")
            .resolve(group.replace('.', '/'))
            .resolve(artifact)
            .resolve(version);
        Files.createDirectories(directory);
        Files.writeString(
            directory.resolve(artifact + "-" + version + ".pom"),
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
            </project>
            """.formatted(group, artifact, version)
        );
        var jar = artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + ".jar";
        new JarOutputStream(Files.newOutputStream(directory.resolve(jar))).close();
    }

}
