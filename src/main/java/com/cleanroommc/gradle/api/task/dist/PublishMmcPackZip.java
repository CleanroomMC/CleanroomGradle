package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.util.IO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Publishes a minimal MultiMC/PrismLauncher instance archive.
 *
 * <p>The generated patch keeps launch behavior in the OneSix subset understood by both launchers.
 *
 * <p>Non-essential Prism Java compatibility hint is ignored by old MultiMC.
 * Every artifact is referenced through a {@code downloads} object with its locally verified size and SHA-1.
 * No artifact is embedded under the instance {@code libraries/} directory.</p>
 */
@CacheableTask
public abstract class PublishMmcPackZip extends DefaultTask {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String FORGE_UID = "net.minecraftforge";
    private static final String MINECRAFT_UID = "net.minecraft";
    private static final String PATCH_PATH = "patches/" + FORGE_UID + ".json";

    @Input
    public abstract Property<String> getInstanceName();

    @Input
    public abstract Property<String> getCleanroomVersion();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract ListProperty<String> getTweakers();

    /** PrismLauncher compatibility hint with old MultiMC safely ignoring this field. */
    @Input
    public abstract ListProperty<Integer> getCompatibleJavaMajors();

    @Input
    public abstract Property<String> getUniversalCoordinate();

    @Input
    public abstract Property<String> getUniversalUrl();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniversalJar();

    @Nested
    public abstract ListProperty<LibraryArtifact> getLibraries();

    @Input
    public abstract SetProperty<String> getInheritedLibraries();

    /** {@code *} entry is the fallback. */
    @Input
    public abstract MapProperty<String, String> getRepositoryUrls();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @TaskAction
    public void publish() {
        var pack = packJson();
        var patch = patchJson();

        var instance = new TreeMap<String, byte[]>();
        instance.put(PATCH_PATH, json(patch));
        instance.put("mmc-pack.json", json(pack));
        instance.put("instance.cfg", instanceCfg().getBytes(StandardCharsets.UTF_8));

        writeZip(getArchiveFile().get().getAsFile(), instance);
    }

    private JsonObject packJson() {
        var minecraft = new JsonObject();
        minecraft.addProperty("uid", MINECRAFT_UID);
        minecraft.addProperty("version", "1.12.2");
        minecraft.addProperty("important", true);

        var cleanroom = new JsonObject();
        cleanroom.addProperty("uid", FORGE_UID);
        cleanroom.addProperty("version", getCleanroomVersion().get());

        var components = new JsonArray();
        components.add(minecraft);
        components.add(cleanroom);

        var pack = new JsonObject();
        pack.addProperty("formatVersion", 1);
        pack.add("components", components);
        return pack;
    }

    private JsonObject patchJson() {
        var requirement = new JsonObject();
        requirement.addProperty("uid", MINECRAFT_UID);
        requirement.addProperty("equals", "1.12.2");
        var requirements = new JsonArray();
        requirements.add(requirement);

        var tweakers = new JsonArray();
        getTweakers().get().forEach(tweakers::add);

        var patch = new JsonObject();
        patch.addProperty("formatVersion", 1);
        patch.addProperty("name", getInstanceName().get());
        patch.addProperty("uid", FORGE_UID);
        patch.addProperty("version", getCleanroomVersion().get());
        patch.add("requires", requirements);
        patch.addProperty("mainClass", getMainClass().get());
        if (!getCompatibleJavaMajors().get().isEmpty()) {
            var javaMajors = new JsonArray();
            getCompatibleJavaMajors().get().forEach(javaMajors::add);
            patch.add("compatibleJavaMajors", javaMajors);
        }
        if (!tweakers.isEmpty()) {
            patch.add("+tweakers", tweakers);
        }
        patch.add("libraries", librariesJson());
        return patch;
    }

    private JsonArray librariesJson() {
        var inherited = getInheritedLibraries().get();
        var artifacts = new TreeMap<String, Artifact>();
        var universal = artifact(getUniversalCoordinate().get(), getUniversalJar().get().getAsFile().toPath(), getUniversalUrl().get());
        artifacts.put(universal.coordinate().serialized(), universal);

        for (var input : getLibraries().get()) {
            var coordinate = Coordinate.parse(input.getCoordinate().get());
            if (inherited.contains(coordinate.serialized()) || coordinate.sameArtifact(universal.coordinate())) {
                continue;
            }
            var path = input.getFile().get().getAsFile().toPath();
            var artifact = artifact(coordinate, path, artifactUrl(coordinate));
            var previous = artifacts.put(coordinate.serialized(), artifact);
            if (previous != null && !previous.path().equals(path)) {
                throw new GradleException("Multiple files resolved for MMC library " + coordinate.serialized());
            }
        }

        var ordinary = new ArrayList<Artifact>();
        var natives = new TreeMap<String, List<Artifact>>();
        for (var artifact : artifacts.values()) {
            var classifier = artifact.coordinate().classifier();
            if (classifier != null && classifier.startsWith("natives-")) {
                natives.computeIfAbsent(artifact.coordinate().module(), ignored -> new ArrayList<>()).add(artifact);
            } else {
                ordinary.add(artifact);
            }
        }

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

    private JsonObject ordinaryLibrary(Artifact artifact) {
        var downloads = new JsonObject();
        downloads.add("artifact", download(artifact));

        var library = new JsonObject();
        library.addProperty("name", artifact.coordinate().serialized());
        library.add("downloads", downloads);
        return library;
    }

    private JsonObject nativeLibrary(Coordinate base, List<Artifact> artifacts) {
        artifacts.sort(Comparator.comparing(artifact -> artifact.coordinate().serialized()));
        var classifiers = new JsonObject();
        var nativeMap = new JsonObject();
        for (var artifact : artifacts) {
            var classifier = artifact.coordinate().classifier();
            classifiers.add(classifier, download(artifact));
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

    private JsonObject download(Artifact artifact) {
        var download = new JsonObject();
        download.addProperty("url", artifact.url());
        download.addProperty("sha1", IO.sha1(artifact.path()));
        try {
            download.addProperty("size", Files.size(artifact.path()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect MMC library " + artifact.path(), e);
        }
        return download;
    }

    private Artifact artifact(String coordinate, Path path, String url) {
        return artifact(Coordinate.parse(coordinate), path, url);
    }

    private Artifact artifact(Coordinate coordinate, Path path, String url) {
        if (!Files.isRegularFile(path)) {
            throw new GradleException("MMC library does not exist: " + path);
        }
        return new Artifact(coordinate, path, url);
    }

    private String artifactUrl(Coordinate coordinate) {
        var repositories = getRepositoryUrls().get();
        var selectedPrefix = repositories.keySet().stream()
                .filter(prefix -> !prefix.equals("*"))
                .filter(prefix -> coordinate.group().equals(prefix) || coordinate.group().startsWith(prefix + "."))
                .max(Comparator.comparingInt(String::length));
        var repository = selectedPrefix.map(repositories::get).orElse(repositories.get("*"));
        if (repository == null || repository.isBlank()) {
            throw new GradleException("No MMC download repository configured for " + coordinate.serialized());
        }
        return trailingSlash(repository) + coordinate.mavenPath();
    }

    private static String nativePlatform(String classifier) {
        var platform = classifier.substring("natives-".length());
        if (platform.equals("macos") || platform.equals("osx")) {
            return "osx";
        }
        if (platform.startsWith("macos-")) {
            return "osx-" + platform.substring("macos-".length());
        }
        return platform;
    }

    private String instanceCfg() {
        return "InstanceType=OneSix\n"
                + "name=" + getInstanceName().get() + "\n"
                + "iconKey=default\n";
    }

    private static byte[] json(JsonObject object) {
        return (GSON.toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String trailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static void writeZip(File output, Map<String, byte[]> files) {
        try {
            Files.createDirectories(output.toPath().getParent());
            try (var zip = IO.zipOut(output)) {
                for (var file : files.entrySet()) {
                    var entry = new ZipEntry(file.getKey());
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    zip.write(file.getValue());
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write MMC archive " + output, e);
        }
    }

    public abstract static class LibraryArtifact {

        @Input
        public abstract Property<String> getCoordinate();

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        public abstract RegularFileProperty getFile();

    }

    private record Artifact(Coordinate coordinate, Path path, String url) { }

    private record Coordinate(String group, String artifact, String version, String classifier, String extension) {

        private static Coordinate parse(String value) {
            var extensionSplit = value.split("@", 2);
            var parts = extensionSplit[0].split(":", -1);
            if (parts.length < 3 || parts.length > 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw new GradleException("Invalid MMC Maven coordinate: " + value);
            }
            var extension = extensionSplit.length == 2 ? extensionSplit[1] : "jar";
            var classifier = parts.length == 4 && !parts[3].isBlank() ? parts[3] : null;
            return new Coordinate(parts[0], parts[1], parts[2], classifier, extension);
        }

        private String serialized() {
            var value = group + ":" + artifact + ":" + version;
            if (classifier != null) {
                value += ":" + classifier;
            }
            if (!extension.equals("jar")) {
                value += "@" + extension;
            }
            return value;
        }

        private String module() {
            return group + ":" + artifact + ":" + version;
        }

        private Coordinate withoutClassifier() {
            return new Coordinate(group, artifact, version, null, extension);
        }

        private boolean sameArtifact(Coordinate other) {
            return group.equals(other.group) && artifact.equals(other.artifact)
                    && version.equals(other.version) && classifierEquals(classifier, other.classifier)
                    && extension.equals(other.extension);
        }

        private static boolean classifierEquals(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }

        String mavenPath() {
            var filename = artifact + "-" + version;
            if (classifier != null) {
                filename += "-" + classifier;
            }
            filename += "." + extension.toLowerCase(Locale.ROOT);
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + filename;
        }

    }

}
