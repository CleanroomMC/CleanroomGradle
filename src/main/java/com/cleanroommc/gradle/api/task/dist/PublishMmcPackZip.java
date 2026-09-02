package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.dist.Artifact;
import com.cleanroommc.gradle.api.util.dist.Coordinate;
import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.cleanroommc.gradle.api.util.dist.LibraryJson;
import com.cleanroommc.gradle.api.util.dist.ResolvedLibraries;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
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

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;

/**
 * Publishes a minimal MultiMC/PrismLauncher instance archive.
 *
 * <p>The generated patch keeps launch behavior in the OneSix subset understood by both launchers.
 *
 * <p>Non-essential Prism Java compatibility hint is ignored by old MultiMC.
 *
 * <p>Downloaded artifacts are referenced through a {@code downloads} object with their locally verified size and SHA-1.
 * A universal coordinate whose version has a {@code +local} SemVer build component embeds that jar under
 * {@code libraries/} with {@code MMC-hint=local}. Artifacts resolved from local Maven repositories are packed
 * the same way.
 *
 * <p>Minecraft modules excluded from the resolved distribution are replaced by higher-version empty
 * local libraries so Prism can retain its stock Minecraft metadata.
 */
@CacheableTask
public abstract class PublishMmcPackZip extends DefaultTask {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String FORGE_UID = "net.minecraftforge";
    private static final String LWJGL_UID = "org.lwjgl";
    private static final String MINECRAFT_UID = "net.minecraft";
    private static final String FORGE_PATCH_PATH = "patches/" + FORGE_UID + ".json";
    private static final String LWJGL_PATCH_PATH = "patches/" + LWJGL_UID + ".json";
    private static final String LOCAL_LIBRARIES = "libraries/";
    private static final String BLOCKED_LIBRARY_VERSION = "999999.0-empty";
    private static final byte[] EMPTY_JAR = {
            0x50, 0x4b, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    @Input
    public abstract Property<String> getInstanceName();

    @Input
    public abstract Property<String> getCleanroomVersion();

    @Input
    public abstract Property<String> getMinecraftVersion();

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

    @Input
    public abstract SetProperty<String> getMinecraftExcludeRules();

    @OutputFile
    public abstract RegularFileProperty getArchiveFile();

    @OutputFile
    public abstract RegularFileProperty getInstallerArchiveFile();

    @Inject
    public PublishMmcPackZip() {
        getMinecraftVersion().convention(Meta.ONE_TRUE_MINECRAFT_VERSION);
        getEmbedUniversalJar().convention(getUniversalCoordinate()
                .map(coordinate -> Coordinate.parse(coordinate).hasLocalComponent()));
    }

    @TaskAction
    public void publish() {
        var universal = universalArtifact();
        var libraries = librariesJson(universal);
        var blockedMinecraftModules = blockedMinecraftModules();
        addBlockedLibraries(libraries.cleanroom(), blockedMinecraftModules);
        var pack = packJson(libraries.lwjglVersion());
        var forgePatch = forgePatchJson(libraries.cleanroom(), libraries.lwjglVersion());
        var lwjglPatch = lwjglPatchJson(libraries.lwjgl(), libraries.lwjglVersion());

        var instance = new TreeMap<String, byte[]>();
        instance.put(FORGE_PATCH_PATH, json(forgePatch));
        instance.put(LWJGL_PATCH_PATH, json(lwjglPatch));
        instance.put("mmc-pack.json", json(pack));
        instance.put("instance.cfg", instanceCfg().getBytes(StandardCharsets.UTF_8));
        for (var module : blockedMinecraftModules) {
            instance.put(LOCAL_LIBRARIES + blockedLibraryFileName(module), EMPTY_JAR);
        }
        for (var artifact : libraries.local()) {
            instance.put(LOCAL_LIBRARIES + artifact.coordinate().fileName(), read(artifact.path()));
        }

        writeZip(getArchiveFile().get().getAsFile(), instance);

        var installer = new TreeMap<>(instance);
        installer.remove(LOCAL_LIBRARIES + Coordinate.parse(getUniversalCoordinate().get()).fileName());
        writeZip(getInstallerArchiveFile().get().getAsFile(), installer);
    }

    private Artifact universalArtifact() {
        return LibraryJson.artifact(
                Coordinate.parse(getUniversalCoordinate().get()),
                getUniversalJar().get().getAsFile().toPath(),
                getEmbedUniversalJar().get() ? "" : getUniversalUrl().get()
        );
    }

    private JsonObject packJson(String lwjglVersion) {
        var minecraft = new JsonObject();
        minecraft.addProperty("uid", MINECRAFT_UID);
        minecraft.addProperty("version", getMinecraftVersion().get());
        minecraft.addProperty("important", true);

        var lwjgl = new JsonObject();
        lwjgl.addProperty("uid", LWJGL_UID);
        lwjgl.addProperty("version", lwjglVersion);

        var cleanroom = new JsonObject();
        cleanroom.addProperty("uid", FORGE_UID);
        cleanroom.addProperty("version", getCleanroomVersion().get());

        var components = new JsonArray();
        components.add(minecraft);
        components.add(lwjgl);
        components.add(cleanroom);

        var pack = new JsonObject();
        pack.addProperty("formatVersion", 1);
        pack.add("components", components);
        return pack;
    }

    private JsonObject forgePatchJson(JsonArray libraries, String lwjglVersion) {
        var requirements = new JsonArray();
        requirements.add(requirement(MINECRAFT_UID, getMinecraftVersion().get()));
        requirements.add(requirement(LWJGL_UID, lwjglVersion));

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

    private JsonObject lwjglPatchJson(JsonArray libraries, String lwjglVersion) {
        var patch = new JsonObject();
        patch.addProperty("formatVersion", 1);
        patch.addProperty("name", "LWJGL 3");
        patch.addProperty("uid", LWJGL_UID);
        patch.addProperty("version", lwjglVersion);
        patch.add("libraries", libraries);
        return patch;
    }

    private static void addBlockedLibraries(JsonArray libraries, Set<String> blockedMinecraftModules) {
        for (var module : blockedMinecraftModules) {
            var library = new JsonObject();
            library.addProperty("name", module + ":" + BLOCKED_LIBRARY_VERSION);
            library.addProperty("MMC-hint", "local");
            libraries.add(library);
        }
    }

    private Set<String> blockedMinecraftModules() {
        var rules = getMinecraftExcludeRules().get();
        var blocked = new TreeSet<String>();
        for (var inherited : getInheritedLibraries().get()) {
            var coordinate = Coordinate.parse(inherited);
            if (ResolvedLibraries.isExcluded(coordinate, rules)) {
                blocked.add(coordinate.group() + ":" + coordinate.artifact());
            }
        }
        return blocked;
    }

    private static String blockedLibraryFileName(String module) {
        validateBlockedModule(module);
        var parts = module.split(":", -1);
        return parts[1] + "-" + BLOCKED_LIBRARY_VERSION + ".jar";
    }

    private static void validateBlockedModule(String module) {
        var parts = module.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new GradleException("Invalid blocked Minecraft module: " + module);
        }
    }

    private ComponentLibraries librariesJson(Artifact universal) {
        var cleanroomInputs = new ArrayList<LibraryArtifact>();
        var lwjglInputs = new ArrayList<LibraryArtifact>();
        var lwjglVersions = new HashSet<String>();
        for (var library : getLibraries().get()) {
            var coordinate = Coordinate.parse(library.getCoordinate().get());
            if (coordinate.group().equals(LWJGL_UID)) {
                lwjglInputs.add(library);
                lwjglVersions.add(coordinate.version());
            } else {
                cleanroomInputs.add(library);
            }
        }
        if (lwjglVersions.size() != 1) {
            throw new GradleException("Cleanroom's MMC component requires exactly one LWJGL version. Found "
                    + lwjglVersions);
        }

        var cleanroom = LibraryJson.resolve(universal, cleanroomInputs, getInheritedLibraries().get());
        var lwjgl = LibraryJson.resolve(lwjglInputs, Set.of());
        var local = new ArrayList<Artifact>();
        cleanroom.stream().filter(LibraryJson::isLocal).forEach(local::add);
        lwjgl.stream().filter(LibraryJson::isLocal).forEach(local::add);
        return new ComponentLibraries(
                lwjglVersions.iterator().next(),
                LibraryJson.mmcLibraries(cleanroom),
                LibraryJson.mmcLibraries(lwjgl),
                local
        );
    }

    private static JsonObject requirement(String uid, String version) {
        var requirement = new JsonObject();
        requirement.addProperty("uid", uid);
        requirement.addProperty("equals", version);
        return requirement;
    }

    private record ComponentLibraries(String lwjglVersion, JsonArray cleanroom, JsonArray lwjgl, List<Artifact> local) {
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
                + "name=" + getInstanceName().get() + " " + getCleanroomVersion().get() + "\n"
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
