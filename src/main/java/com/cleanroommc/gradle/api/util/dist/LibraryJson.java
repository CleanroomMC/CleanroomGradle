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

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.launcherutils.Coordinate;
import com.cleanroommc.launcherutils.LauncherNames;
import com.cleanroommc.launcherutils.rule.Rule;
import com.cleanroommc.launcherutils.version.Download;
import com.cleanroommc.launcherutils.version.Library;
import com.cleanroommc.launcherutils.version.VersionJson;
import com.cleanroommc.platformutils.Architecture;
import com.cleanroommc.platformutils.Platform;

import org.gradle.api.GradleException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns resolved artifacts into the library entries launchers understand.
 *
 * <p>The MMC pack and the installer profile are two dialects of the same JSON.
 * MMC writes the overlay on top of Minecraft 1.12.2, whereas the installer list is self-contained.
 */
public final class LibraryJson {

    private LibraryJson() { }

    public static List<Artifact> resolve(Artifact universal, List<? extends LibraryArtifact> libraries) {
        return resolve(universal, libraries, Set.of());
    }

    /**
     * Resolves a component's libraries with the Cleanroom universal artifact included.
     *
     * @param universal the universal artifact the component publishes
     * @param libraries the component's declared libraries
     * @param inherited coordinates the parent component already supplies, which are skipped
     */
    public static List<Artifact> resolve(
        Artifact universal, List<? extends LibraryArtifact> libraries, Set<String> inherited) {
        var artifacts = new TreeMap<String, Artifact>();
        artifacts.put(universal.coordinate().toString(), universal);

        resolveInto(artifacts, libraries, inherited, universal.coordinate());
        return new ArrayList<>(artifacts.values());
    }

    /**
     * Resolves a component's libraries without adding the Cleanroom universal artifact.
     *
     * @param libraries the component's declared libraries
     * @param inherited coordinates a parent component already supplies, which are skipped
     */
    public static List<Artifact> resolve(List<? extends LibraryArtifact> libraries, Set<String> inherited) {
        var artifacts = new TreeMap<String, Artifact>();
        resolveInto(artifacts, libraries, inherited, null);
        return new ArrayList<>(artifacts.values());
    }

    /**
     * The MultiMC/Prism dialect:
     * Natives are folded into their base module under {@code classifiers} with a {@code natives} platform map.
     *
     * @param artifacts the resolved artifacts to write
     * @param natives what {@code cleanroom.natives} declares about per-platform jars
     */
    public static List<Library> mmcLibraries(List<Artifact> artifacts, NativeJars natives) {
        var ordinary = new ArrayList<Artifact>();
        var extracted = new TreeMap<String, List<Artifact>>();
        split(artifacts, ordinary, extracted, natives);

        ordinary.sort(Comparator.comparing(artifact -> artifact.coordinate().toString()));
        var output = new ArrayList<Library>();
        for (var artifact : ordinary) {
            var library = ordinaryLibrary(artifact);
            if (natives.platform(artifact.coordinate()) != null) {
                addMmcPlatformRules(library, classifierPlatform(artifact.coordinate(), natives));
            }
            output.add(library);
            var nativeArtifacts = extracted.remove(versionKey(artifact.coordinate()));
            if (nativeArtifacts != null) {
                output.add(nativeLibrary(artifact.coordinate().withoutClassifier(), nativeArtifacts, true, natives));
            }
        }
        // A classifier-only dependency is legal even when its base artifact was not selected
        for (var nativeArtifacts : extracted.values()) {
            nativeArtifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().toString()));
            output.add(nativeLibrary(
                nativeArtifacts.getFirst().coordinate().withoutClassifier(), nativeArtifacts, true, natives
            ));
        }
        return output;
    }

    /**
     * The Mojang dialect used by {@code version.json} for classpath libraries:
     * One flat entry per artifact, classifiers in the name, including side-less native jars such as Netty's.
     *
     * @param artifacts the resolved artifacts to write
     * @param natives what {@code cleanroom.natives} declares about per-platform jars
     */
    public static List<Library> mojangLibraries(List<Artifact> artifacts, NativeJars natives) {
        var sorted = new ArrayList<>(artifacts);
        sorted.sort(Comparator.comparing(artifact -> artifact.coordinate().toString()));
        var output = new ArrayList<Library>();
        for (var artifact : sorted) {
            var library = Library.of(artifact.coordinate()).artifact(download(artifact, true));
            addPlatformRule(library, classifierPlatform(artifact.coordinate(), natives));
            output.add(library);
        }
        return output;
    }

    /**
     * The Mojang dialect for natives:
     * One entry per classifier with a platform rule, extracted rather than classpathed.
     * Separate entries are required because the published classifier names do not share a usable {@code ${arch}}
     * template.
     *
     * @param artifacts the resolved native artifacts to write
     * @param natives what {@code cleanroom.natives} declares about per-platform jars
     */
    public static List<Library> mojangNativeLibraries(List<Artifact> artifacts, NativeJars natives) {
        var extracted = new ArrayList<Artifact>();
        for (var artifact : artifacts) {
            if (!natives.isExtracted(artifact.coordinate())) {
                throw new GradleException("Not a natives library: " + artifact.coordinate());
            }
            extracted.add(artifact);
        }
        extracted.sort(Comparator.comparing(artifact -> artifact.coordinate().toString()));
        var output = new ArrayList<Library>();
        for (var artifact : extracted) {
            var library = nativeLibrary(
                artifact.coordinate().withoutClassifier(), new ArrayList<>(List.of(artifact)), false, natives
            );
            var platform = classifierPlatform(artifact.coordinate(), natives);
            library.side(VersionJson.CLIENT);
            addPlatformRule(library, platform);
            output.add(library);
        }
        return output;
    }

    /**
     * A library entry whose file the installer embeds rather than downloads.
     *
     * @param artifact the artifact the entry describes
     */
    public static Library embeddedLibrary(Artifact artifact) {
        // An empty url is signal for "take this from the installer's /maven"
        return Library.of(artifact.coordinate()).artifact(download(artifact, true).url(""));
    }

    public static Library ordinaryLibrary(Artifact artifact) {
        if (isLocal(artifact)) {
            return localLibrary(artifact);
        }
        return Library.of(artifact.coordinate()).artifact(download(artifact, false));
    }

    /**
     * A library both MultiMC and Prism take from the instance's own {@code libraries/} folder instead of downloading.
     *
     * @param artifact the artifact the entry describes
     */
    public static Library localLibrary(Artifact artifact) {
        return Library.of(artifact.coordinate()).mmcHint("local").artifact(download(artifact, true).url(""));
    }

    /**
     * An artifact with no download url is one whose file travels inside the archive.
     *
     * @param artifact the artifact to test
     */
    public static boolean isLocal(Artifact artifact) {
        return artifact.url() == null || artifact.url().isBlank();
    }

    public static boolean isLocalRepository(String url) {
        return "file".equalsIgnoreCase(URI.create(url).getScheme());
    }

    /**
     * The download block an artifact's entry carries.
     *
     * @param artifact the artifact the block describes
     * @param withPath whether to include the {@code path} key, which Mojang's format carries but not MMC's format
     */
    public static Download download(Artifact artifact, boolean withPath) {
        var download = new Download()
            .url(isLocal(artifact) ? "" : artifact.url())
            .sha1(IO.sha1(artifact.path()))
            .size(size(artifact.path()));
        return withPath ? download.path(artifact.coordinate().path()) : download;
    }

    public static Artifact artifact(Coordinate coordinate, Path path, String url) {
        if (!Files.isRegularFile(path)) {
            throw new GradleException("Library does not exist: " + path);
        }
        return new Artifact(coordinate, path, url);
    }

    public static String trailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static void resolveInto(
        Map<String, Artifact> artifacts, List<? extends LibraryArtifact> libraries, Set<String> inherited,
        Coordinate universal) {
        for (var input : libraries) {
            var coordinate = Coordinate.parse(input.getCoordinate().get());
            if (inherited.contains(coordinate.toString()) || coordinate.equals(universal)) {
                continue;
            }
            var path = input.getFile().get().getAsFile().toPath();
            var repositoryUrl = input.getRepositoryUrl().get();
            var url = isLocalRepository(repositoryUrl) ? "" : trailingSlash(repositoryUrl) + coordinate.path();
            var artifact = artifact(coordinate, path, url);
            var previous = artifacts.put(coordinate.toString(), artifact);
            if (previous != null && !previous.path().equals(path)) {
                throw new GradleException("Multiple files resolved for library " + coordinate);
            }
        }
    }

    private static void addPlatformRule(Library library, Platform platform) {
        if (platform != null) {
            library.rule(Rule.allow().os(platform.operatingSystem()).arch(platform.architecture()));
        }
    }

    /**
     * MultiMC reads only os.name and knows bare names alone, so the first rule takes every jar of the OS.
     * Prism also reads only os.name, matching {@code <os>-<arch>}, and matches the bare name on x86 and x86_64.
     * The later rules narrow Prism to this jar's architecture, and MultiMC skips them.
     */
    private static void addMmcPlatformRules(Library library, Platform platform) {
        library.rule(Rule.allow().os(platform.operatingSystem()));
        for (var legacy : List.of(Architecture.X64, Architecture.X86)) {
            if (legacy != platform.architecture()) {
                library.rule(Rule.disallow().mmcSystem(Platform.of(platform.operatingSystem(), legacy)));
            }
        }
        if (!platform.isX86()) {
            library.rule(Rule.allow().mmcSystem(platform));
        }
    }

    // A family classifier such as zstd-jni's linux_amd64 may not parse, so its platform id stands in
    private static Platform classifierPlatform(Coordinate coordinate, NativeJars natives) {
        var classifier = coordinate.classifier();
        if (classifier == null) {
            return null;
        }
        var known = natives.platform(coordinate);
        return known != null ? Platform.parse(known) : LauncherNames.classifierPlatform(classifier).orElse(null);
    }

    // MultiMC and Prism take the bare system name for x64 jars
    private static String nativesKey(Platform platform) {
        return platform.architecture() == Architecture.X64
            ? LauncherNames.os(platform)
            : LauncherNames.mmcSystem(platform);
    }

    private static Library nativeLibrary(Coordinate base, List<Artifact> artifacts, boolean mmc, NativeJars natives) {
        artifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().toString()));
        var library = Library.of(base);
        for (var artifact : artifacts) {
            var classifier = artifact.coordinate().classifier();
            var download = download(artifact, false);
            if (mmc && isLocal(artifact)) {
                download.url(null);
            }
            library.classifier(classifier, download);
            var platform = classifierPlatform(artifact.coordinate(), natives);
            var key = platform == null
                ? classifier.substring(LauncherNames.NATIVES_PREFIX.length())
                : mmc ? nativesKey(platform) : LauncherNames.os(platform);
            library.natives(key, classifier);
        }
        if (mmc && artifacts.stream().allMatch(LibraryJson::isLocal)) {
            library.mmcHint("local");
        }
        return library.extractExcludes("META-INF/");
    }

    private static void split(
        List<Artifact> artifacts, List<Artifact> ordinary, Map<String, List<Artifact>> extracted, NativeJars natives) {
        for (var artifact : artifacts) {
            if (natives.isExtracted(artifact.coordinate())) {
                extracted.computeIfAbsent(versionKey(artifact.coordinate()), ignored -> new ArrayList<>())
                    .add(artifact);
            } else {
                ordinary.add(artifact);
            }
        }
    }

    private static String versionKey(Coordinate coordinate) {
        return coordinate.module() + ":" + coordinate.version();
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect library " + path, e);
        }
    }

}
