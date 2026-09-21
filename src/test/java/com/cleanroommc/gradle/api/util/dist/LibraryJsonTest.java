/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.dist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.gradle.api.GradleException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryJsonTest {

    @TempDir
    Path directory;

    @Test
    void nativeDetectionAndPlatformMapping() {
        assertThat(LibraryJson.isNative(Coordinate.parse("g:a:1:natives-linux"))).isTrue();
        assertThat(LibraryJson.isNative(Coordinate.parse("g:a:1"))).isFalse();
        assertThat(LibraryJson.isNative(Coordinate.parse("g:a:1:linux"))).isFalse();

        assertThat(LibraryJson.nativePlatform("natives-macos")).isEqualTo("osx");
        assertThat(LibraryJson.nativePlatform("natives-osx")).isEqualTo("osx");
        assertThat(LibraryJson.nativePlatform("natives-macos-arm64")).isEqualTo("osx-arm64");
        assertThat(LibraryJson.nativePlatform("natives-linux")).isEqualTo("linux");
    }

    @Test
    void trailingSlashLocalAndDownloadShape() throws IOException {
        assertThat(LibraryJson.trailingSlash("https://example.invalid")).isEqualTo("https://example.invalid/");
        assertThat(LibraryJson.trailingSlash("https://example.invalid/")).isEqualTo("https://example.invalid/");

        assertThat(LibraryJson.isLocalRepository("file:///tmp/repo")).isTrue();
        assertThat(LibraryJson.isLocalRepository("FILE:///tmp/repo")).isTrue();
        assertThat(LibraryJson.isLocalRepository("https://example.invalid/")).isFalse();

        var file = write("lib.jar", "contents");
        var remote = new Artifact(Coordinate.parse("g:a:1"), file, "https://example.invalid/g/a/1/a-1.jar");
        var download = LibraryJson.download(remote, true);
        assertThat(download.get("path").getAsString()).isEqualTo("g/a/1/a-1.jar");
        assertThat(download.get("url").getAsString()).isEqualTo("https://example.invalid/g/a/1/a-1.jar");

        var local = new Artifact(Coordinate.parse("g:a:1"), file, "");
        assertThat(LibraryJson.isLocal(local)).isTrue();
        assertThat(LibraryJson.isLocal(remote)).isFalse();
        var localDownload = LibraryJson.download(local, false);
        assertThat(localDownload.get("url").getAsString()).isEqualTo("");
        assertThat(localDownload.has("path")).isFalse();
    }

    @Test
    void artifactRequiresAnExistingFile() {
        var missing = this.directory.resolve("missing.jar");
        assertThatThrownBy(() -> LibraryJson.artifact(Coordinate.parse("g:a:1"), missing, "https://example.invalid/")).isInstanceOf(GradleException.class);
    }

    @Test
    void mmcFoldsNativesIntoTheirBaseModule() throws IOException {
        var base = artifact("g:lib:1", "base");
        var linux = artifact("g:lib:1:natives-linux", "linux");
        var windows = artifact("g:lib:1:natives-windows", "windows");

        var libraries = LibraryJson.mmcLibraries(List.of(windows, linux, base));
        assertThat(libraries.size()).isEqualTo(2);
        assertThat(libraries.get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("g:lib:1");
        var natives = libraries.get(1).getAsJsonObject();
        assertThat(natives.get("name").getAsString()).isEqualTo("g:lib:1");
        assertThat(natives.getAsJsonObject("downloads").getAsJsonObject("classifiers").has("natives-linux")).isTrue();
        assertThat(natives.getAsJsonObject("natives").has("linux")).isTrue();
    }

    @Test
    void mojangDialectsCarryRulesAndRejectNonNatives() throws IOException {
        var plain = artifact("g:lib:1", "plain");
        var libraries = LibraryJson.mojangLibraries(List.of(plain));
        assertThat(libraries.get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("g:lib:1");
        assertThat(libraries.get(0).getAsJsonObject().getAsJsonObject("downloads").has("artifact")).isTrue();

        var nativeArtifact = artifact("g:lib:1:natives-linux", "native");
        var nativeLibraries = LibraryJson.mojangNativeLibraries(List.of(nativeArtifact));
        assertThat(nativeLibraries.get(0).getAsJsonObject().get("side").getAsString()).isEqualTo("client");
        assertThat(nativeLibraries.get(0).getAsJsonObject().has("rules")).isTrue();

        assertThatThrownBy(() -> LibraryJson.mojangNativeLibraries(List.of(plain))).isInstanceOf(GradleException.class);
    }

    @Test
    void localLibrariesCarryNoUrl() throws IOException {
        var local = new Artifact(Coordinate.parse("g:a:1"), write("a.jar", "a"), "");
        var library = LibraryJson.ordinaryLibrary(local);
        assertThat(library.get("MMC-hint").getAsString()).isEqualTo("local");
        assertThat(library.getAsJsonObject("downloads").getAsJsonObject("artifact").has("url")).isFalse();

        var embedded = LibraryJson.embeddedLibrary(local);
        assertThat(embedded.getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString()).isEqualTo("");
    }

    private Artifact artifact(String coordinate, String content) throws IOException {
        return new Artifact(Coordinate.parse(coordinate), write(coordinate.replace(':', '-') + ".jar", content), "https://example.invalid/");
    }

    private Path write(String name, String content) throws IOException {
        var file = this.directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

}
