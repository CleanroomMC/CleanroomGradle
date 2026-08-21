package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.dist.Artifact;
import com.cleanroommc.gradle.api.util.dist.Coordinate;
import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.cleanroommc.gradle.api.util.dist.LibraryJson;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
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
import java.util.List;

/**
 * Writes the two JSON documents a Cleanroom installer jar carries.
 *
 * <p>{@code version.json} is deliberately self-contained.
 * Inheriting from {@code 1.12.2} would make the launcher merge the parent's libraries,
 * which some are replaced (e.g. LWJGL 2).
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

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniversalJar();

    @Nested
    public abstract ListProperty<LibraryArtifact> getLibraries();

    @Input
    public abstract SetProperty<String> getInheritedLibraries();

    @Input
    public abstract MapProperty<String, String> getRepositoryUrls();

    @Input
    public abstract Property<VersionMeta> getVersionMeta();

    @Input
    public abstract Property<String> getReleaseTime();

    @OutputFile
    public abstract RegularFileProperty getInstallProfile();

    @OutputFile
    public abstract RegularFileProperty getVersionJson();

    @TaskAction
    public void write() {
        var universal = LibraryJson.artifact(
                Coordinate.parse(getUniversalCoordinate().get()),
                getUniversalJar().get().getAsFile().toPath(),
                LibraryJson.artifactUrl(Coordinate.parse(getUniversalCoordinate().get()), getRepositoryUrls().get()));
        var artifacts = LibraryJson.resolve(universal, getLibraries().get(),
                getInheritedLibraries().get(), getRepositoryUrls().get());
        artifacts.removeIf(artifact -> artifact.coordinate().sameArtifact(universal.coordinate()));

        writeJson(getVersionJson().get().getAsFile(), versionJson(artifacts, universal));
        writeJson(getInstallProfile().get().getAsFile(), installProfile(universal));
    }

    private JsonObject versionJson(List<Artifact> artifacts, Artifact universal) {
        var meta = getVersionMeta().get();
        var version = new JsonObject();
        version.addProperty("id", getVersionId().get());
        version.addProperty("time", getReleaseTime().get());
        version.addProperty("releaseTime", getReleaseTime().get());
        version.addProperty("type", "release");
        version.addProperty("mainClass", getMainClass().get());
        version.addProperty("inheritsFrom", (String) null);
        version.remove("inheritsFrom");
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
        for (var library : meta.libraries()) {
            libraries.add(GSON.toJsonTree(library));
        }
        libraries.addAll(LibraryJson.mojangLibraries(artifacts));
        version.add("libraries", libraries);
        return version;
    }

    private String minecraftArguments(VersionMeta meta) {
        var builder = new StringBuilder(meta.minecraftArguments() == null ? "" : meta.minecraftArguments());
        for (var tweaker : getTweakers().get()) {
            builder.append(" --tweakClass ").append(tweaker);
        }
        builder.append(" --versionType ").append(getProfileName().get());
        return builder.toString().trim();
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
        profile.add("repositories", GSON.toJsonTree(getRepositoryUrls().get()));
        return profile;
    }

    private static void writeJson(File output, JsonObject json) {
        try {
            Files.createDirectories(output.toPath().getParent());
            Files.write(output.toPath(), (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + output, e);
        }
    }

    static String sha1(File file) {
        return IO.sha1(file);
    }

}
