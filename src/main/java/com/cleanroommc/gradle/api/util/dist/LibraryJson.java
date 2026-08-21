package com.cleanroommc.gradle.api.util.dist;

import com.cleanroommc.gradle.api.util.IO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.GradleException;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * <p>Both the MMC pack and the installer profile describe the same dependency set in two dialects of the same JSON.
 */
public final class LibraryJson {

    private static final String NATIVES_PREFIX = "natives-";

    /**
     * Resolves the library inputs into a deterministic, deduplicated artifact list.
     *
     * @param universal    the loader's own jar, always listed first
     * @param inherited    coordinates the parent component already supplies, which are skipped
     * @param repositories group prefix to base URL, with {@code *} as the fallback
     */
    public static List<Artifact> resolve(Artifact universal, List<? extends LibraryArtifact> libraries,
                                         Set<String> inherited, Map<String, String> repositories) {
        var artifacts = new TreeMap<String, Artifact>();
        artifacts.put(universal.coordinate().serialized(), universal);

        for (var input : libraries) {
            var coordinate = Coordinate.parse(input.getCoordinate().get());
            if (inherited.contains(coordinate.serialized()) || coordinate.sameArtifact(universal.coordinate())) {
                continue;
            }
            var path = input.getFile().get().getAsFile().toPath();
            var artifact = artifact(coordinate, path, artifactUrl(coordinate, repositories));
            var previous = artifacts.put(coordinate.serialized(), artifact);
            if (previous != null && !previous.path().equals(path)) {
                throw new GradleException("Multiple files resolved for library " + coordinate.serialized());
            }
        }
        return new ArrayList<>(artifacts.values());
    }

    /**
     * The MultiMC/Prism dialect:
     * Natives are folded into their base module under {@code classifiers} with a {@code natives} platform map.
     */
    public static JsonArray mmcLibraries(List<Artifact> artifacts) {
        var ordinary = new ArrayList<Artifact>();
        var natives = new TreeMap<String, List<Artifact>>();
        split(artifacts, ordinary, natives);

        ordinary.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
        var output = new JsonArray();
        for (var artifact : ordinary) {
            output.add(ordinaryLibrary(artifact));
            var nativeArtifacts = natives.remove(artifact.coordinate().module());
            if (nativeArtifacts != null) {
                output.add(nativeLibrary(artifact.coordinate().withoutClassifier(), nativeArtifacts));
            }
        }
        // A classifier-only dependency is legal even when its base artifact was not selected
        for (var nativeArtifacts : natives.values()) {
            nativeArtifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
            output.add(nativeLibrary(nativeArtifacts.getFirst().coordinate().withoutClassifier(), nativeArtifacts));
        }
        return output;
    }

    /**
     * The Mojang dialect used by {@code version.json}:
     * One flat entry per artifact, natives included, ordinary entries carries their classifiers in their names.
     */
    public static JsonArray mojangLibraries(List<Artifact> artifacts) {
        var sorted = new ArrayList<>(artifacts);
        sorted.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
        var output = new JsonArray();
        for (var artifact : sorted) {
            var downloads = new JsonObject();
            downloads.add("artifact", download(artifact, true));

            var library = new JsonObject();
            library.addProperty("name", artifact.coordinate().serialized());
            library.add("downloads", downloads);
            output.add(library);
        }
        return output;
    }

    /** A library entry whose file the installer embeds rather than downloads. */
    public static JsonObject embeddedLibrary(Artifact artifact) {
        var download = new JsonObject();
        download.addProperty("path", artifact.coordinate().mavenPath());
        // An empty url is signal for "take this from the installer's /maven"
        download.addProperty("url", "");
        download.addProperty("sha1", IO.sha1(artifact.path()));
        download.addProperty("size", size(artifact.path()));

        var downloads = new JsonObject();
        downloads.add("artifact", download);

        var library = new JsonObject();
        library.addProperty("name", artifact.coordinate().serialized());
        library.add("downloads", downloads);
        return library;
    }

    public static JsonObject ordinaryLibrary(Artifact artifact) {
        if (isLocal(artifact)) {
            return localLibrary(artifact);
        }
        var downloads = new JsonObject();
        downloads.add("artifact", download(artifact, false));

        var library = new JsonObject();
        library.addProperty("name", artifact.coordinate().serialized());
        library.add("downloads", downloads);
        return library;
    }

    /**
     * A library both MultiMC and Prism take from the instance's own {@code libraries/} folder instead of downloading.
     * The launcher matches it by bare file name, and never issues a download for it, so the entry carries no url.
     */
    public static JsonObject localLibrary(Artifact artifact) {
        var download = new JsonObject();
        download.addProperty("path", artifact.coordinate().mavenPath());
        download.addProperty("sha1", IO.sha1(artifact.path()));
        download.addProperty("size", size(artifact.path()));

        var downloads = new JsonObject();
        downloads.add("artifact", download);

        var library = new JsonObject();
        library.addProperty("name", artifact.coordinate().serialized());
        library.addProperty("MMC-hint", "local");
        library.add("downloads", downloads);
        return library;
    }

    /** An artifact with no download url is one whose file travels inside the archive. */
    public static boolean isLocal(Artifact artifact) {
        return artifact.url() == null || artifact.url().isBlank();
    }

    public static JsonObject nativeLibrary(Coordinate base, List<Artifact> artifacts) {
        artifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
        var classifiers = new JsonObject();
        var nativeMap = new JsonObject();
        for (var artifact : artifacts) {
            var classifier = artifact.coordinate().classifier();
            classifiers.add(classifier, download(artifact, false));
            nativeMap.addProperty(nativePlatform(classifier), classifier);
        }

        var downloads = new JsonObject();
        downloads.add("classifiers", classifiers);
        var extract = new JsonObject();
        var exclusions = new JsonArray();
        exclusions.add("META-INF/");
        extract.add("exclude", exclusions);

        var library = new JsonObject();
        library.addProperty("name", base.serialized());
        library.add("downloads", downloads);
        library.add("natives", nativeMap);
        library.add("extract", extract);
        return library;
    }

    /**
     * @param withPath whether to include the {@code path} key, which Mojang's format carries but not MMC's format
     */
    public static JsonObject download(Artifact artifact, boolean withPath) {
        var download = new JsonObject();
        if (withPath) {
            download.addProperty("path", artifact.coordinate().mavenPath());
        }
        download.addProperty("url", artifact.url());
        download.addProperty("sha1", IO.sha1(artifact.path()));
        download.addProperty("size", size(artifact.path()));
        return download;
    }

    public static Artifact artifact(Coordinate coordinate, Path path, String url) {
        if (!Files.isRegularFile(path)) {
            throw new GradleException("Library does not exist: " + path);
        }
        return new Artifact(coordinate, path, url);
    }

    /** Longest matching group prefix wins with {@code *} being the fallback. */
    public static String artifactUrl(Coordinate coordinate, Map<String, String> repositories) {
        var selectedPrefix = repositories.keySet().stream()
                .filter(prefix -> !prefix.equals("*"))
                .filter(prefix -> coordinate.group().equals(prefix) || coordinate.group().startsWith(prefix + "."))
                .max(Comparator.comparingInt(String::length));
        var repository = selectedPrefix.map(repositories::get).orElse(repositories.get("*"));
        if (repository == null || repository.isBlank()) {
            throw new GradleException("No download repository configured for " + coordinate.serialized());
        }
        return trailingSlash(repository) + coordinate.mavenPath();
    }

    public static String nativePlatform(String classifier) {
        var platform = classifier.substring(NATIVES_PREFIX.length());
        if (platform.equals("macos") || platform.equals("osx")) {
            return "osx";
        }
        if (platform.startsWith("macos-")) {
            return "osx-" + platform.substring("macos-".length());
        }
        return platform;
    }

    public static boolean isNative(Coordinate coordinate) {
        return coordinate.classifier() != null && coordinate.classifier().startsWith(NATIVES_PREFIX);
    }

    private static void split(List<Artifact> artifacts, List<Artifact> ordinary, Map<String, List<Artifact>> natives) {
        for (var artifact : artifacts) {
            if (isNative(artifact.coordinate())) {
                natives.computeIfAbsent(artifact.coordinate().module(), ignored -> new ArrayList<>()).add(artifact);
            } else {
                ordinary.add(artifact);
            }
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect library " + path, e);
        }
    }

    public static String trailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private LibraryJson() { }

}
