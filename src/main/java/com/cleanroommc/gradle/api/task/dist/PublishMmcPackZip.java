package com.cleanroommc.gradle.api.task.dist;

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
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;

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
    private static final String LOCAL_LIBRARIES = "libraries/";

    @Input
    public abstract Property<String> getInstanceName();

    @Input
    public abstract Property<String> getCleanroomVersion();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract ListProperty<String> getTweakers();

    /**
     * PrismLauncher compatibility hint with old MultiMC safely ignoring this field.
     */
    @Input
    public abstract ListProperty<Integer> getCompatibleJavaMajors();

    @Input
    public abstract Property<String> getUniversalCoordinate();

    @Input
    public abstract Property<String> getUniversalUrl();

    @Input
    public abstract Property<Boolean> getEmbedUniversalJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUniversalJar();

    @Nested
    public abstract ListProperty<LibraryArtifact> getLibraries();

    @Input
    public abstract SetProperty<String> getInheritedLibraries();

    /**
     * {@code *} entry is the fallback.
     */
    @Input
    public abstract MapProperty<String, String> getRepositoryUrls();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @Inject
    public PublishMmcPackZip(ProviderFactory providers) {
        getEmbedUniversalJar().convention(providers.gradleProperty("release")
                .map(ignored -> false)
                .orElse(getCleanroomVersion().map(version -> version.contains("+build")).orElse(false)));
    }

    @TaskAction
    public void publish() {
        var universal = universalArtifact();
        var pack = packJson();
        var patch = patchJson(librariesJson(universal));

        var instance = new TreeMap<String, byte[]>();
        instance.put(PATCH_PATH, json(patch));
        instance.put("mmc-pack.json", json(pack));
        instance.put("instance.cfg", instanceCfg().getBytes(StandardCharsets.UTF_8));
        if (getEmbedUniversalJar().get()) {
            instance.put(LOCAL_LIBRARIES + universal.coordinate().fileName(), read(universal.path()));
        }

        writeZip(getArchiveFile().get().getAsFile(), instance);
    }

    private Artifact universalArtifact() {
        return LibraryJson.artifact(
                Coordinate.parse(getUniversalCoordinate().get()),
                getUniversalJar().get().getAsFile().toPath(),
                getEmbedUniversalJar().get() ? "" : getUniversalUrl().get()
        );
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

    private JsonObject patchJson(JsonArray libraries) {
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
        patch.add("libraries", libraries);
        return patch;
    }

    private JsonArray librariesJson(Artifact universal) {
        var artifacts = LibraryJson.resolve(universal, getLibraries().get(), getInheritedLibraries().get(), getRepositoryUrls().get());
        return LibraryJson.mmcLibraries(artifacts);
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    private String instanceCfg() {
        return "InstanceType=OneSix\n"
                + "name=" + getInstanceName().get() + "\n"
                + "iconKey=default\n";
    }

    private static byte[] json(JsonObject object) {
        return (GSON.toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
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
}
