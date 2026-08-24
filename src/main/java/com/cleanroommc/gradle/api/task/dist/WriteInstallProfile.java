package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.dist.Artifact;
import com.cleanroommc.gradle.api.util.dist.Coordinate;
import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.cleanroommc.gradle.api.util.dist.LibraryJson;
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
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes the two JSON documents a Cleanroom installer jar carries.
 *
 * <p>{@code version.json} is deliberately self-contained.
 * Inheriting from {@code 1.12.2} would make the launcher merge the parent's libraries,
 * which some are replaced (e.g. LWJGL 2).
 *
 * <p>The library list is the resolved graph: Cleanroom's runtime, every-platform LWJGL 3 natives,
 * and every-platform vanilla jars and extracted natives. LWJGL 2 is omitted because Cleanroom ships LWJGL 3.
 * The version manifest is used only for Mojang-hosted download URLs and launch metadata.
 *
 * <p>{@code install_profile.json} keeps the empty {@code processors} and {@code data} blocks Forge's format defines.
 * Forge needs them to patch jars it may not redistribute.
 */
@CacheableTask
public abstract class WriteInstallProfile extends DefaultTask {

    public static final int SPEC = 1;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    @Input
    public abstract Property<String> getProfileName();

    @Input
    public abstract Property<String> getCleanroomVersion();

    @Input
    public abstract Property<String> getVersionId();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getServerMainClass();

    @Input
    public abstract ListProperty<String> getTweakers();

    @Input
    public abstract ListProperty<String> getServerTweakers();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Input
    public abstract Property<Integer> getMinimumJava();

    @Input
    public abstract Property<Integer> getRecommendedJava();

    @Input
    @Optional
    public abstract Property<String> getJavaDistro();

    @Input
    public abstract Property<String> getUniversalCoordinate();

    @Input
    public abstract Property<String> getUniversalUrl();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniversalJar();

    @Nested
    public abstract ListProperty<LibraryArtifact> getLibraries();

    @Nested
    public abstract ListProperty<LibraryArtifact> getNativeLibraries();

    @Input
    public abstract SetProperty<String> getExcludedLibraryGroups();

    @Input
    public abstract MapProperty<String, String> getManifestUrls();

    @Internal
    public abstract Property<VersionMeta> getVersionMeta();

    @Input
    public Provider<String> getVersionMetaContent() {
        return getVersionMeta().map(VersionMeta::toString);
    }

    @Input
    public abstract Property<String> getReleaseTime();

    @OutputFile
    public abstract RegularFileProperty getInstallProfile();

    @OutputFile
    public abstract RegularFileProperty getVersionJson();

    @TaskAction
    public void write() {
        var universalCoordinate = Coordinate.parse(getUniversalCoordinate().get());
        var universal = LibraryJson.artifact(
                universalCoordinate,
                getUniversalJar().get().getAsFile().toPath(),
                getUniversalUrl().get());
        var excluded = getExcludedLibraryGroups().get();
        var artifacts = LibraryJson.resolve(universal, getLibraries().get());
        artifacts.removeIf(artifact -> drop(artifact, universal, excluded));
        var natives = LibraryJson.resolve(universal, getNativeLibraries().get());
        natives.removeIf(artifact -> drop(artifact, universal, excluded));

        writeJson(getVersionJson().get().getAsFile(), versionJson(manifestUrls(artifacts), manifestUrls(natives), universal));
        writeJson(getInstallProfile().get().getAsFile(), installProfile(universal));
    }

    private boolean drop(Artifact artifact, Artifact universal, Set<String> excluded) {
        return artifact.coordinate().sameArtifact(universal.coordinate()) || excluded.contains(artifact.coordinate().group());
    }

    private List<Artifact> manifestUrls(List<Artifact> artifacts) {
        var urls = getManifestUrls().get();
        return artifacts.stream()
                .map(artifact -> {
                    var url = urls.get(artifact.coordinate().serialized());
                    return url == null ? artifact : new Artifact(artifact.coordinate(), artifact.path(), url);
                })
                .toList();
    }

    private JsonObject versionJson(List<Artifact> artifacts, List<Artifact> natives, Artifact universal) {
        var meta = getVersionMeta().get();
        var version = new JsonObject();
        version.addProperty("id", getVersionId().get());
        version.addProperty("time", getReleaseTime().get());
        version.addProperty("releaseTime", getReleaseTime().get());
        version.addProperty("type", "release");
        version.addProperty("mainClass", getMainClass().get());
        version.add("assetIndex", GSON.toJsonTree(meta.assetIndex()));
        version.addProperty("assets", meta.assets());
        version.add("downloads", GSON.toJsonTree(meta.downloads()));
        var java = new JsonObject();
        java.addProperty("component", "java-runtime-epsilon");
        java.addProperty("majorVersion", getRecommendedJava().get());
        version.add("javaVersion", java);
        version.addProperty("complianceLevel", meta.complianceLevel());
        version.addProperty("minimumLauncherVersion", Math.max(18, meta.minimumLauncherVersion()));
        version.addProperty("minecraftArguments", minecraftArguments(meta));

        var libraries = new JsonArray();
        libraries.add(LibraryJson.embeddedLibrary(universal));
        libraries.addAll(LibraryJson.mojangLibraries(artifacts));
        libraries.addAll(LibraryJson.mojangNativeLibraries(natives));
        version.add("libraries", libraries);
        return version;
    }

    private String minecraftArguments(VersionMeta meta) {
        var arguments = new ArrayList<String>();
        var vanilla = meta.minecraftArguments() == null ? "" : meta.minecraftArguments().trim();
        if (!vanilla.isEmpty()) {
            Collections.addAll(arguments, vanilla.split("\\s+"));
        }
        for (var tweaker : getTweakers().get()) {
            addOption(arguments, "--tweakClass", tweaker);
        }
        // Vanilla already passes --versionType; ours replaces it instead of being appended after it
        replaceOption(arguments, "--versionType", getProfileName().get());
        return String.join(" ", arguments);
    }

    /**
     * Appends an option a launcher reads more than once, such as {@code --tweakClass}, unless that exact pair is there.
     */
    private static void addOption(List<String> arguments, String key, String value) {
        for (int i = 0; i < arguments.size() - 1; i++) {
            if (arguments.get(i).equals(key) && arguments.get(i + 1).equals(value)) {
                return;
            }
        }
        arguments.add(key);
        arguments.add(value);
    }

    /**
     * Sets an option a launcher reads once, dropping whatever value the vanilla arguments carried.
     */
    private static void replaceOption(List<String> arguments, String key, String value) {
        for (int i = arguments.size() - 2; i >= 0; i--) {
            if (arguments.get(i).equals(key)) {
                arguments.subList(i, i + 2).clear();
            }
        }
        arguments.add(key);
        arguments.add(value);
    }

    private JsonObject installProfile(Artifact universal) {
        var profile = new JsonObject();
        profile.addProperty("spec", SPEC);
        profile.addProperty("profile", getProfileName().get());
        profile.addProperty("version", getVersionId().get());
        profile.addProperty("minecraft", "1.12.2");
        profile.addProperty("cleanroomVersion", getCleanroomVersion().get());
        profile.addProperty("json", "/version.json");
        profile.addProperty("path", universal.coordinate().serialized());
        profile.addProperty("logo", "/cleanroom.png");
        profile.addProperty("welcome", "Installing " + getProfileName().get() + " " + getCleanroomVersion().get());
        profile.addProperty("mirrorList", "");

        var libraries = new JsonArray();
        libraries.add(LibraryJson.embeddedLibrary(universal));
        profile.add("libraries", libraries);
        profile.add("processors", new JsonArray());
        profile.add("data", new JsonObject());

        var java = new JsonObject();
        java.addProperty("minimum", getMinimumJava().get());
        java.addProperty("recommended", getRecommendedJava().get());
        java.addProperty("distro", getJavaDistro().getOrElse("zulu"));
        profile.add("java", java);

        profile.add("jvmArgs", GSON.toJsonTree(getJvmArgs().get()));
        profile.addProperty("mainClass", getMainClass().get());
        profile.addProperty("serverMainClass", getServerMainClass().get());
        profile.add("tweakers", GSON.toJsonTree(getTweakers().get()));
        profile.add("serverTweakers", GSON.toJsonTree(getServerTweakers().get()));
        profile.addProperty("serverJarPath", "libraries/" + universal.coordinate().mavenPath());
        profile.add("repositories", GSON.toJsonTree(repositoryUrls(universal)));
        return profile;
    }

    private Map<String, String> repositoryUrls(Artifact universal) {
        var resolved = new TreeMap<String, Set<String>>();
        addRepository(resolved, universal.coordinate(), repositoryUrl(universal));
        addRepositories(resolved, getLibraries().get());
        addRepositories(resolved, getNativeLibraries().get());
        var repositories = new TreeMap<String, String>();
        resolved.forEach((group, urls) -> {
            if (urls.size() == 1) {
                repositories.put(group, urls.iterator().next());
            }
        });
        return repositories;
    }

    private static void addRepositories(Map<String, Set<String>> repositories, List<LibraryArtifact> libraries) {
        for (var library : libraries) {
            addRepository(repositories, Coordinate.parse(library.getCoordinate().get()),
                    library.getRepositoryUrl().get());
        }
    }

    private static void addRepository(Map<String, Set<String>> repositories, Coordinate coordinate, String repositoryUrl) {
        var url = LibraryJson.trailingSlash(repositoryUrl);
        repositories.computeIfAbsent(coordinate.group(), ignored -> new TreeSet<>()).add(url);
    }

    private static String repositoryUrl(Artifact artifact) {
        var path = artifact.coordinate().mavenPath();
        if (!artifact.url().endsWith(path)) {
            throw new GradleException("Artifact URL does not use Maven layout: " + artifact.url());
        }
        return artifact.url().substring(0, artifact.url().length() - path.length());
    }

    private static void writeJson(File output, JsonObject json) {
        try {
            Files.createDirectories(output.toPath().getParent());
            Files.write(output.toPath(), (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + output, e);
        }
    }

}
