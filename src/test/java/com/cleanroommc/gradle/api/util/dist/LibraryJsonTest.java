package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryJsonTest {

    @TempDir
    Path directory;

    @Test
    void nativeDetectionAndPlatformMapping() {
        assertTrue(LibraryJson.isNative(Coordinate.parse("g:a:1:natives-linux")));
        assertFalse(LibraryJson.isNative(Coordinate.parse("g:a:1")));
        assertFalse(LibraryJson.isNative(Coordinate.parse("g:a:1:linux")));

        assertEquals("osx", LibraryJson.nativePlatform("natives-macos"));
        assertEquals("osx", LibraryJson.nativePlatform("natives-osx"));
        assertEquals("osx-arm64", LibraryJson.nativePlatform("natives-macos-arm64"));
        assertEquals("linux", LibraryJson.nativePlatform("natives-linux"));
    }

    @Test
    void trailingSlashLocalAndDownloadShape() throws IOException {
        assertEquals("https://example.invalid/", LibraryJson.trailingSlash("https://example.invalid"));
        assertEquals("https://example.invalid/", LibraryJson.trailingSlash("https://example.invalid/"));

        assertTrue(LibraryJson.isLocalRepository("file:///tmp/repo"));
        assertTrue(LibraryJson.isLocalRepository("FILE:///tmp/repo"));
        assertFalse(LibraryJson.isLocalRepository("https://example.invalid/"));

        var file = write("lib.jar", "contents");
        var remote = new Artifact(Coordinate.parse("g:a:1"), file, "https://example.invalid/g/a/1/a-1.jar");
        var download = LibraryJson.download(remote, true);
        assertEquals("g/a/1/a-1.jar", download.get("path").getAsString());
        assertEquals("https://example.invalid/g/a/1/a-1.jar", download.get("url").getAsString());

        var local = new Artifact(Coordinate.parse("g:a:1"), file, "");
        assertTrue(LibraryJson.isLocal(local));
        assertFalse(LibraryJson.isLocal(remote));
        var localDownload = LibraryJson.download(local, false);
        assertEquals("", localDownload.get("url").getAsString());
        assertFalse(localDownload.has("path"));
    }

    @Test
    void artifactRequiresAnExistingFile() {
        var missing = this.directory.resolve("missing.jar");
        assertThrows(GradleException.class,
                () -> LibraryJson.artifact(Coordinate.parse("g:a:1"), missing, "https://example.invalid/"));
    }

    @Test
    void mmcFoldsNativesIntoTheirBaseModule() throws IOException {
        var base = artifact("g:lib:1", "base");
        var linux = artifact("g:lib:1:natives-linux", "linux");
        var windows = artifact("g:lib:1:natives-windows", "windows");

        var libraries = LibraryJson.mmcLibraries(List.of(windows, linux, base));
        assertEquals(2, libraries.size());
        assertEquals("g:lib:1", libraries.get(0).getAsJsonObject().get("name").getAsString());
        var natives = libraries.get(1).getAsJsonObject();
        assertEquals("g:lib:1", natives.get("name").getAsString());
        assertTrue(natives.getAsJsonObject("downloads").getAsJsonObject("classifiers").has("natives-linux"));
        assertTrue(natives.getAsJsonObject("natives").has("linux"));
    }

    @Test
    void mojangDialectsCarryRulesAndRejectNonNatives() throws IOException {
        var plain = artifact("g:lib:1", "plain");
        var libraries = LibraryJson.mojangLibraries(List.of(plain));
        assertEquals("g:lib:1", libraries.get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(libraries.get(0).getAsJsonObject().getAsJsonObject("downloads").has("artifact"));

        var nativeArtifact = artifact("g:lib:1:natives-linux", "native");
        var nativeLibraries = LibraryJson.mojangNativeLibraries(List.of(nativeArtifact));
        assertEquals("client", nativeLibraries.get(0).getAsJsonObject().get("side").getAsString());
        assertTrue(nativeLibraries.get(0).getAsJsonObject().has("rules"));

        assertThrows(GradleException.class,
                () -> LibraryJson.mojangNativeLibraries(List.of(plain)));
    }

    @Test
    void localLibrariesCarryNoUrl() throws IOException {
        var local = new Artifact(Coordinate.parse("g:a:1"), write("a.jar", "a"), "");
        var library = LibraryJson.ordinaryLibrary(local);
        assertEquals("local", library.get("MMC-hint").getAsString());
        assertFalse(library.getAsJsonObject("downloads").getAsJsonObject("artifact").has("url"));

        var embedded = LibraryJson.embeddedLibrary(local);
        assertEquals("", embedded.getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
    }

    private Artifact artifact(String coordinate, String content) throws IOException {
        return new Artifact(Coordinate.parse(coordinate), write(coordinate.replace(':', '-') + ".jar", content),
                "https://example.invalid/");
    }

    private Path write(String name, String content) throws IOException {
        var file = this.directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

}
