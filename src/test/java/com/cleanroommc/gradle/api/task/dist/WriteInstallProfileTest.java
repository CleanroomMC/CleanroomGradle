package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteInstallProfileTest {

    @TempDir
    Path directory;

    @Test
    void writesOneEntryPerModuleFromTheBuildsOwnGraph() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);

        var universal = file("cleanroom-1.0.0-universal.jar", "cleanroom");
        var guava = file("guava-33.6.0-jre.jar", "guava");
        var lwjgl = file("lwjgl-3.3.6.jar", "lwjgl");
        var lwjglLinux = file("lwjgl-3.3.6-natives-linux.jar", "lwjgl linux native");
        var text2speech = file("text2speech-1.10.3-natives-linux.jar", "text2speech linux native");
        var text2speechWindows = file("text2speech-1.10.3-natives-windows.jar", "text2speech windows native");
        var jinputLinux = file("jinput-platform-2.0.5-natives-linux.jar", "jinput linux native");
        var jinputWindows = file("jinput-platform-2.0.5-natives-windows.jar", "jinput windows native");
        var soundsystem = file("soundsystem-20120107.jar", "soundsystem");

        task.getUniversalJar().fileValue(universal.toFile());
        task.getLibraries().add(library(project, "com.google.guava:guava:33.6.0-jre", guava));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6", lwjgl));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6:natives-linux", lwjglLinux));
        task.getLibraries().add(library(project, "com.paulscode:soundsystem:20120107", soundsystem));
        task.getNativeLibraries().add(library(project, "com.mojang:text2speech:1.10.3:natives-linux", text2speech));
        task.getNativeLibraries().add(library(project, "com.mojang:text2speech:1.10.3:natives-windows", text2speechWindows));
        task.getNativeLibraries().add(library(project, "net.java.jinput:jinput-platform:2.0.5:natives-linux", jinputLinux));
        task.getNativeLibraries().add(library(project, "net.java.jinput:jinput-platform:2.0.5:natives-windows", jinputWindows));
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name} --versionType ${version_type}",
                "com.google.guava:guava:21.0", "com.mojang:patchy:1.3.9", "com.paulscode:soundsystem:20120107"));
        task.getManifestUrls().put("com.paulscode:soundsystem:20120107",
                "https://libraries.minecraft.net/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar");
        task.getManifestUrls().put("com.mojang:text2speech:1.10.3:natives-linux",
                "https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar");

        task.write();

        var version = json(task.getVersionJson().get().getAsFile().toPath());
        var libraries = version.getAsJsonArray("libraries");
        var names = names(libraries);
        assertEquals(names.size(), new HashSet<>(names).size(), names.toString());
        assertFalse(names.contains("com.google.guava:guava:21.0"), names.toString());
        assertFalse(names.contains("com.mojang:patchy:1.3.9"), names.toString());
        assertEquals(List.of("com.cleanroommc:cleanroom:1.0.0:universal",
                        "com.google.guava:guava:33.6.0-jre",
                        "com.paulscode:soundsystem:20120107",
                        "org.lwjgl:lwjgl:3.3.6",
                        "org.lwjgl:lwjgl:3.3.6:natives-linux",
                        "com.mojang:text2speech:1.10.3",
                        "net.java.jinput:jinput-platform:2.0.5"),
                names);

        // Mojang hosts libraries no public Maven carries, so the manifest's url wins for what it names
        assertEquals("https://libraries.minecraft.net/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar",
                library(libraries, "com.paulscode:soundsystem:20120107")
                        .getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
        // A library this build moved off Minecraft's version keeps the configured repository
        assertEquals("https://repo.maven.apache.org/maven2/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar",
                library(libraries, "com.google.guava:guava:33.6.0-jre")
                        .getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());

        // The loader's own jar travels inside the installer
        var embedded = library(libraries, "com.cleanroommc:cleanroom:1.0.0:universal")
                .getAsJsonObject("downloads").getAsJsonObject("artifact");
        assertEquals("", embedded.get("url").getAsString());

        // LWJGL 3 loads its natives off the classpath, so they stay ordinary entries
        var lwjglNative = library(libraries, "org.lwjgl:lwjgl:3.3.6:natives-linux");
        assertFalse(lwjglNative.has("natives"));
        assertNotNull(lwjglNative.getAsJsonObject("downloads").getAsJsonObject("artifact"));

        // Minecraft's own natives are extracted instead, which needs the classifier/platform shape
        var narrator = library(libraries, "com.mojang:text2speech:1.10.3");
        assertEquals("natives-linux", narrator.getAsJsonObject("natives").get("linux").getAsString());
        assertEquals("natives-windows", narrator.getAsJsonObject("natives").get("windows").getAsString());
        var narratorClassifiers = narrator.getAsJsonObject("downloads").getAsJsonObject("classifiers");
        assertEquals(Set.of("natives-linux", "natives-windows"), narratorClassifiers.keySet());
        assertEquals("https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar",
                narratorClassifiers.getAsJsonObject("natives-linux").get("url").getAsString());
        assertEquals(List.of("META-INF/"), narrator.getAsJsonObject("extract").getAsJsonArray("exclude")
                .asList().stream().map(element -> element.getAsString()).toList());

        var jinput = library(libraries, "net.java.jinput:jinput-platform:2.0.5");
        assertEquals(Set.of("linux", "windows"), jinput.getAsJsonObject("natives").keySet());
        assertFalse(jinput.getAsJsonObject("downloads").has("artifact"));
        assertEquals(Set.of("natives-linux", "natives-windows"),
                jinput.getAsJsonObject("downloads").getAsJsonObject("classifiers").keySet());
    }

    @Test
    void dropsLwjgl2ThatVanillaWouldHaveContributed() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);

        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.3.6",
                file("lwjgl-3.3.6.jar", "lwjgl3")));
        task.getLibraries().add(library(project, "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209",
                file("lwjgl-2.9.4-nightly-20150209.jar", "lwjgl2")));
        task.getNativeLibraries().add(library(project, "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209:natives-linux",
                file("lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar", "lwjgl2 native")));
        task.getNativeLibraries().add(library(project, "com.mojang:text2speech:1.10.3:natives-linux",
                file("text2speech-1.10.3-natives-linux.jar", "narrator")));
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var names = names(json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries"));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.6"), names.toString());
        assertTrue(names.contains("com.mojang:text2speech:1.10.3"), names.toString());
        assertTrue(names.stream().noneMatch(name -> name.startsWith("org.lwjgl.lwjgl:")), names.toString());
    }

    @Test
    void namesEveryArgumentOnce() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getTweakers().add("net.minecraftforge.fml.common.launcher.FMLTweaker");
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name} --versionType ${version_type} "
                + "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker"));

        task.write();

        var arguments = json(task.getVersionJson().get().getAsFile().toPath())
                .get("minecraftArguments").getAsString();
        assertEquals("--username ${auth_player_name} "
                + "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker "
                + "--versionType Cleanroom", arguments);
        assertEquals(1, count(arguments, "--versionType"));
        assertEquals(1, count(arguments, "--tweakClass"));
        assertFalse(arguments.contains("${version_type}"));
    }

    private WriteInstallProfile task(Project project) {
        var task = project.getTasks().create("writeInstallProfile", WriteInstallProfile.class);
        task.getProfileName().set("Cleanroom");
        task.getCleanroomVersion().set("1.0.0");
        task.getVersionId().set("Cleanroom-1.0.0");
        task.getMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getServerMainClass().set("top.outlands.foundation.boot.Foundation");
        task.getMinimumJava().set(25);
        task.getRecommendedJava().set(25);
        task.getUniversalCoordinate().set("com.cleanroommc:cleanroom:1.0.0:universal");
        task.getExcludedLibraryGroups().add("org.lwjgl.lwjgl");
        task.getRepositoryUrls().put("*", "https://repo.maven.apache.org/maven2/");
        task.getReleaseTime().set("1970-01-01T00:00:00+0000");
        task.getInstallProfile().fileValue(directory.resolve("install_profile.json").toFile());
        task.getVersionJson().fileValue(directory.resolve("version.json").toFile());
        return task;
    }

    private static VersionMeta versionMeta(String minecraftArguments, String... libraries) {
        var entries = new ArrayList<VersionMeta.Library>();
        for (var name : libraries) {
            entries.add(new VersionMeta.Library(new VersionMeta.Downloads(
                    new VersionMeta.Download("ignored", "sha1", 1, "https://example.invalid/ignored.jar"), Map.of()),
                    name, null, null, null));
        }
        return new VersionMeta(null,
                new VersionMeta.AssetIndex("1.12", 1, null, "sha1", 1, "https://example.invalid/1.12.json"),
                "1.12", 0,
                Map.of("client", new VersionMeta.Download(null, "sha1", 1, "https://example.invalid/client.jar")),
                "1.12.2", new VersionMeta.JavaVersion("jre-legacy", 8), entries, null,
                "net.minecraft.client.main.Main", minecraftArguments, 18,
                "2017-09-18T08:39:46+00:00", "2017-09-18T08:39:46+00:00", "release");
    }

    private static LibraryArtifact library(Project project, String coordinate, Path file) {
        var library = project.getObjects().newInstance(LibraryArtifact.class);
        library.getCoordinate().set(coordinate);
        library.getFile().fileValue(file.toFile());
        return library;
    }

    private static JsonObject library(JsonArray libraries, String name) {
        for (var element : libraries) {
            if (element.getAsJsonObject().get("name").getAsString().equals(name)) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError("No library named " + name + " in " + libraries);
    }

    private static List<String> names(JsonArray libraries) {
        return libraries.asList().stream().map(element -> element.getAsJsonObject().get("name").getAsString()).toList();
    }

    private static int count(String arguments, String option) {
        return (int) List.of(arguments.split(" ")).stream().filter(option::equals).count();
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private Path file(String name, String contents) throws IOException {
        return Files.writeString(directory.resolve(name), contents, StandardCharsets.UTF_8);
    }

}
