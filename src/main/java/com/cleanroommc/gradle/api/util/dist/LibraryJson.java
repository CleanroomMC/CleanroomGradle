package com.cleanroommc.gradle.api.util.dist;

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

    public static List<Artifact> resolve(Artifact universal, List<? extends LibraryArtifact> libraries) {
        return resolve(universal, libraries, Set.of());
    }

    /**
     * @param inherited coordinates the parent component already supplies, which are skipped
     */
    public static List<Artifact> resolve(Artifact universal, List<? extends LibraryArtifact> libraries,
                                         Set<String> inherited) {
        var artifacts = new TreeMap<String, Artifact>();
        artifacts.put(universal.coordinate().serialized(), universal);

        resolveInto(artifacts, libraries, inherited, universal.coordinate());
        return new ArrayList<>(artifacts.values());
    }

    /**
     * Resolves a component's libraries without adding the Cleanroom universal artifact.
     *
     * @param inherited coordinates a parent component already supplies, which are skipped
     */
    public static List<Artifact> resolve(List<? extends LibraryArtifact> libraries, Set<String> inherited) {
        var artifacts = new TreeMap<String, Artifact>();
        resolveInto(artifacts, libraries, inherited, null);
        return new ArrayList<>(artifacts.values());
    }

    private static void resolveInto(Map<String, Artifact> artifacts, List<? extends LibraryArtifact> libraries,
                                    Set<String> inherited, Coordinate universal) {
        for (var input : libraries) {
            var coordinate = Coordinate.parse(input.getCoordinate().get());
            if (inherited.contains(coordinate.serialized())
                    || universal != null && coordinate.sameArtifact(universal)) {
                continue;
            }
            var path = input.getFile().get().getAsFile().toPath();
            var repositoryUrl = input.getRepositoryUrl().get();
            var url = isLocalRepository(repositoryUrl)
                    ? ""
                    : trailingSlash(repositoryUrl) + coordinate.mavenPath();
            var artifact = artifact(coordinate, path, url);
            var previous = artifacts.put(coordinate.serialized(), artifact);
            if (previous != null && !previous.path().equals(path)) {
                throw new GradleException("Multiple files resolved for library " + coordinate.serialized());
            }
        }
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
                output.add(nativeLibrary(artifact.coordinate().withoutClassifier(), nativeArtifacts, true));
            }
        }
        // A classifier-only dependency is legal even when its base artifact was not selected
        for (var nativeArtifacts : natives.values()) {
            nativeArtifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
            output.add(nativeLibrary(nativeArtifacts.getFirst().coordinate().withoutClassifier(), nativeArtifacts, true));
        }
        return output;
    }

    /**
     * The Mojang dialect used by {@code version.json} for classpath libraries:
     * One flat entry per artifact, classifiers in the name, including side-less native jars such as Netty's.
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
            addPlatformRule(library, artifact.coordinate().classifier());
            output.add(library);
        }
        return output;
    }

    /**
     * The Mojang dialect for natives:
     * One entry per classifier with a platform rule, extracted rather than classpathed.
     * Separate entries are required because the published classifier names do not share a usable {@code ${arch}}
     * template.
     */
    public static JsonArray mojangNativeLibraries(List<Artifact> artifacts) {
        var natives = new ArrayList<Artifact>();
        for (var artifact : artifacts) {
            if (!isNative(artifact.coordinate())) {
                throw new GradleException("Not a natives library: " + artifact.coordinate().serialized());
            }
            natives.add(artifact);
        }
        natives.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
        var output = new JsonArray();
        for (var artifact : natives) {
            var classifier = artifact.coordinate().classifier();
            var library = nativeLibrary(artifact.coordinate().withoutClassifier(),
                    new ArrayList<>(List.of(artifact)), false);
            var platform = classifierPlatform(classifier);
            if (platform != null) {
                var nativeMap = new JsonObject();
                nativeMap.addProperty(platform.os(), classifier);
                library.add("natives", nativeMap);
            }
            library.addProperty("side", "client");
            addPlatformRule(library, classifier);
            output.add(library);
        }
        return output;
    }

    private static void addPlatformRule(JsonObject library, String classifier) {
        var platform = classifierPlatform(classifier);
        if (platform == null) {
            return;
        }
        var os = new JsonObject();
        os.addProperty("name", platform.os());
        os.addProperty("arch", platform.arch());
        var rule = new JsonObject();
        rule.addProperty("action", "allow");
        rule.add("os", os);
        var rules = new JsonArray();
        rules.add(rule);
        library.add("rules", rules);
    }

    private static NativePlatform classifierPlatform(String classifier) {
        if (classifier == null) {
            return null;
        }
        var platform = classifier.startsWith(LwjglNatives.CLASSIFIER_PREFIX)
                ? classifier.substring(LwjglNatives.CLASSIFIER_PREFIX.length())
                : classifier;
        var separator = platform.indexOf('-');
        var os = separator == -1 ? platform : platform.substring(0, separator);
        var arch = separator == -1 ? null : platform.substring(separator + 1);
        if (os.equals("macos")) {
            os = "osx";
        }
        if (!os.equals("windows") && !os.equals("osx") && !os.equals("linux") && !os.equals("freebsd")) {
            return null;
        }
        if (arch == null) {
            arch = "x64";
        } else if (arch.equals("aarch_64")) {
            arch = "arm64";
        } else if (arch.equals("x86_64")) {
            arch = "x64";
        }
        return new NativePlatform(os, arch);
    }

    private record NativePlatform(String os, String arch) { }

    /**
     * A library entry whose file the installer embeds rather than downloads.
     */
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

    /**
     * An artifact with no download url is one whose file travels inside the archive.
     */
    public static boolean isLocal(Artifact artifact) {
        return artifact.url() == null || artifact.url().isBlank();
    }

    public static boolean isLocalRepository(String url) {
        return "file".equalsIgnoreCase(URI.create(url).getScheme());
    }

    private static JsonObject nativeLibrary(Coordinate base, List<Artifact> artifacts, boolean mmc) {
        artifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
        var classifiers = new JsonObject();
        var nativeMap = new JsonObject();
        for (var artifact : artifacts) {
            var classifier = artifact.coordinate().classifier();
            var download = download(artifact, false);
            if (mmc && isLocal(artifact)) {
                download.remove("url");
            }
            classifiers.add(classifier, download);
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
        if (mmc && artifacts.stream().allMatch(LibraryJson::isLocal)) {
            library.addProperty("MMC-hint", "local");
        }
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
        download.addProperty("url", isLocal(artifact) ? "" : artifact.url());
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

    public static String nativePlatform(String classifier) {
        var platform = classifier.substring(LwjglNatives.CLASSIFIER_PREFIX.length());
        if (platform.equals("macos") || platform.equals("osx")) {
            return "osx";
        }
        if (platform.startsWith("macos-")) {
            return "osx-" + platform.substring("macos-".length());
        }
        return platform;
    }

    public static boolean isNative(Coordinate coordinate) {
        return coordinate.classifier() != null && coordinate.classifier().startsWith(LwjglNatives.CLASSIFIER_PREFIX);
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
