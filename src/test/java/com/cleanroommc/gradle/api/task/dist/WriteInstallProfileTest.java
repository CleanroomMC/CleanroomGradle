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
    void installerProfileUsesTheVersionMetadataMinecraftId() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getVersionMeta().set(versionMetaWithId("1.12.2-custom", "--username ${auth_player_name}"));

        task.write();

        assertEquals("1.12.2-custom", json(task.getInstallProfile().get().getAsFile().toPath())
                .get("minecraft").getAsString());
    }

    @Test
    void writesResolvedGraphWithPlatformNativeMetadata() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);

        var universal = file("cleanroom-1.0.0-universal.jar", "cleanroom");
        var guava = file("guava-33.6.0-jre.jar", "guava");
        var lwjgl = file("lwjgl-3.4.2.jar", "lwjgl");
        var lwjglLinux = file("lwjgl-3.4.2-natives-linux.jar", "lwjgl linux native");
        var lwjglLinuxArm = file("lwjgl-3.4.2-natives-linux-arm64.jar", "lwjgl linux arm native");
        var lwjglWindows = file("lwjgl-3.4.2-natives-windows.jar", "lwjgl windows native");
        var nettyLinux = file("netty-transport-native-epoll-4.2.16.Final-linux-x86_64.jar", "netty linux native");
        var nettyArm = file("netty-transport-native-epoll-4.2.16.Final-linux-aarch_64.jar", "netty arm native");
        var text2speech = file("text2speech-1.10.3-natives-linux.jar", "text2speech linux native");
        var text2speechWindows = file("text2speech-1.10.3-natives-windows.jar", "text2speech windows native");
        var jinputLinux = file("jinput-platform-2.0.5-natives-linux.jar", "jinput linux native");
        var jinputWindows = file("jinput-platform-2.0.5-natives-windows.jar", "jinput windows native");
        var soundsystem = file("soundsystem-20120107.jar", "soundsystem");

        task.getUniversalJar().fileValue(universal.toFile());
        task.getLibraries().add(library(project, "com.google.guava:guava:33.6.0-jre", guava, "https://authority.example/releases/"));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2", lwjgl));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-linux", lwjglLinux));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-linux-arm64", lwjglLinuxArm));
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2:natives-windows", lwjglWindows));
        task.getLibraries().add(library(project, "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64", nettyLinux));
        task.getLibraries().add(library(project, "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64", nettyArm));
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
        assertFalse(names.contains("com.google.guava:guava:21.0"), names.toString());
        assertFalse(names.contains("com.mojang:patchy:1.3.9"), names.toString());
        assertTrue(names.stream().noneMatch(name -> name.startsWith("org.lwjgl:") && name.contains(":natives-")),
                names.toString());
        assertEquals(List.of("com.cleanroommc:cleanroom:1.0.0:universal",
                        "com.google.guava:guava:33.6.0-jre",
                        "com.paulscode:soundsystem:20120107",
                        "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64",
                        "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64",
                        "org.lwjgl:lwjgl:3.4.2",
                        "com.mojang:text2speech:1.10.3",
                        "com.mojang:text2speech:1.10.3",
                        "net.java.jinput:jinput-platform:2.0.5",
                        "net.java.jinput:jinput-platform:2.0.5",
                        "org.lwjgl:lwjgl:3.4.2",
                        "org.lwjgl:lwjgl:3.4.2",
                        "org.lwjgl:lwjgl:3.4.2"),
                names);

        // Mojang hosts libraries no public Maven carries, so the manifest's url wins for what it names
        assertEquals("https://libraries.minecraft.net/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar",
                library(libraries, "com.paulscode:soundsystem:20120107")
                        .getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
        // A library this build moved off Minecraft's version keeps its actual resolved repository
        assertEquals("https://authority.example/releases/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar",
                library(libraries, "com.google.guava:guava:33.6.0-jre")
                        .getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
        assertEquals("https://authority.example/releases/",
                json(task.getInstallProfile().get().getAsFile().toPath())
                        .getAsJsonObject("repositories").get("com.google.guava").getAsString());

        // The loader's own jar travels inside the installer
        var embedded = library(libraries, "com.cleanroommc:cleanroom:1.0.0:universal")
                .getAsJsonObject("downloads").getAsJsonObject("artifact");
        assertEquals("", embedded.get("url").getAsString());

        // LWJGL natives are extracted by launchers and are therefore recognizable as client-only by installers
        var lwjglNative = library(libraries, "org.lwjgl:lwjgl:3.4.2", true, "linux", "x64");
        assertEquals("client", lwjglNative.get("side").getAsString());
        assertEquals("natives-linux", lwjglNative.getAsJsonObject("natives").get("linux").getAsString());
        assertFalse(lwjglNative.getAsJsonObject("downloads").has("artifact"));
        assertEquals(Set.of("natives-linux"),
                lwjglNative.getAsJsonObject("downloads").getAsJsonObject("classifiers").keySet());
        var lwjglArmNative = library(libraries, "org.lwjgl:lwjgl:3.4.2", true, "linux", "arm64");
        assertEquals("natives-linux-arm64", lwjglArmNative.getAsJsonObject("natives").get("linux").getAsString());
        library(libraries, "org.lwjgl:lwjgl:3.4.2", true, "windows", "x64");

        // Netty classifiers contain Java classes, so they remain on the classpath behind exact platform rules
        var nettyNative = library(libraries,
                "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64", false, "linux", "arm64");
        assertNotNull(nettyNative.getAsJsonObject("downloads").getAsJsonObject("artifact"));

        // Minecraft's own natives are extracted instead, which needs the classifier/platform shape
        var narrator = library(libraries, "com.mojang:text2speech:1.10.3", true, "linux", "x64");
        assertEquals("natives-linux", narrator.getAsJsonObject("natives").get("linux").getAsString());
        var narratorClassifiers = narrator.getAsJsonObject("downloads").getAsJsonObject("classifiers");
        assertEquals(Set.of("natives-linux"), narratorClassifiers.keySet());
        assertEquals("https://libraries.minecraft.net/com/mojang/text2speech/1.10.3/text2speech-1.10.3-natives-linux.jar",
                narratorClassifiers.getAsJsonObject("natives-linux").get("url").getAsString());
        assertEquals(List.of("META-INF/"), narrator.getAsJsonObject("extract").getAsJsonArray("exclude")
                .asList().stream().map(element -> element.getAsString()).toList());

        var jinput = library(libraries, "net.java.jinput:jinput-platform:2.0.5", true, "windows", "x64");
        assertEquals(Set.of("windows"), jinput.getAsJsonObject("natives").keySet());
        assertFalse(jinput.getAsJsonObject("downloads").has("artifact"));
        assertEquals(Set.of("natives-windows"),
                jinput.getAsJsonObject("downloads").getAsJsonObject("classifiers").keySet());
    }

    @Test
    void dropsLwjgl2ThatVanillaWouldHaveContributed() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);

        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getLibraries().add(library(project, "org.lwjgl:lwjgl:3.4.2",
                file("lwjgl-3.4.2.jar", "lwjgl3")));
        task.getLibraries().add(library(project, "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209",
                file("lwjgl-2.9.4-nightly-20150209.jar", "lwjgl2")));
        task.getNativeLibraries().add(library(project, "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209:natives-linux",
                file("lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar", "lwjgl2 native")));
        task.getNativeLibraries().add(library(project, "com.mojang:text2speech:1.10.3:natives-linux",
                file("text2speech-1.10.3-natives-linux.jar", "narrator")));
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var names = names(json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries"));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.4.2"), names.toString());
        assertTrue(names.contains("com.mojang:text2speech:1.10.3"), names.toString());
        assertTrue(names.stream().noneMatch(name -> name.startsWith("org.lwjgl.lwjgl:")), names.toString());
    }

    @Test
    void keepsExactArtifactUrlsWhenAGroupSpansRepositories() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        task.getLibraries().add(library(project, "example.shared:first:1.0",
                file("first-1.0.jar", "first"), "https://first.example/releases/"));
        task.getLibraries().add(library(project, "example.shared:second:1.0",
                file("second-1.0.jar", "second"), "https://second.example/releases/"));
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var versionLibraries = json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries");
        assertEquals("https://first.example/releases/example/shared/first/1.0/first-1.0.jar",
                library(versionLibraries, "example.shared:first:1.0").getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
        assertEquals("https://second.example/releases/example/shared/second/1.0/second-1.0.jar",
                library(versionLibraries, "example.shared:second:1.0").getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
        assertFalse(json(task.getInstallProfile().get().getAsFile().toPath()).getAsJsonObject("repositories").has("example.shared"));
    }

    @Test
    void embedsLibrariesResolvedFromMavenLocal() throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = task(project);
        task.getUniversalJar().fileValue(file("cleanroom-1.0.0-universal.jar", "cleanroom").toFile());
        var mcttf = file("mcttf-0.1.0-beta+local.0.jar", "local mcttf");
        var coordinate = "example.local:mcttf:0.1.0-beta+local.0";
        task.getLibraries().add(library(project, coordinate, mcttf,
                directory.resolve("m2").toUri().toString()));
        task.getManifestUrls().put(coordinate, "https://example.invalid/mcttf.jar");
        task.getVersionMeta().set(versionMeta("--username ${auth_player_name}"));

        task.write();

        var versionLibraries = json(task.getVersionJson().get().getAsFile().toPath()).getAsJsonArray("libraries");
        var versionDownload = library(versionLibraries, coordinate)
                .getAsJsonObject("downloads").getAsJsonObject("artifact");
        assertEquals("", versionDownload.get("url").getAsString());
        assertEquals("example/local/mcttf/0.1.0-beta+local.0/mcttf-0.1.0-beta+local.0.jar",
                versionDownload.get("path").getAsString());

        var profile = json(task.getInstallProfile().get().getAsFile().toPath());
        assertEquals("", library(profile.getAsJsonArray("libraries"), coordinate)
                .getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString());
        assertFalse(profile.getAsJsonObject("repositories").has("example.local"));
        assertEquals("local mcttf", Files.readString(task.getEmbeddedLibraries().get().getAsFile().toPath()
                .resolve("example/local/mcttf/0.1.0-beta+local.0/mcttf-0.1.0-beta+local.0.jar")));
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
        task.getUniversalUrl().set("https://maven.cleanroommc.com/com/cleanroommc/cleanroom/1.0.0/cleanroom-1.0.0-universal.jar");
        task.getLibraryExcludeRules().add("org.lwjgl.lwjgl:*");
        task.getReleaseTime().set("1970-01-01T00:00:00+0000");
        task.getInstallProfile().fileValue(directory.resolve("install_profile.json").toFile());
        task.getVersionJson().fileValue(directory.resolve("version.json").toFile());
        task.getEmbeddedLibraries().set(directory.resolve("maven-local").toFile());
        return task;
    }

    private static VersionMeta versionMeta(String minecraftArguments, String... libraries) {
        return versionMetaWithId("1.12.2", minecraftArguments, libraries);
    }

    private static VersionMeta versionMetaWithId(String minecraftVersion, String minecraftArguments, String... libraries) {
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
                minecraftVersion, new VersionMeta.JavaVersion("jre-legacy", 8), entries, null,
                "net.minecraft.client.main.Main", minecraftArguments, 18,
                "2017-09-18T08:39:46+00:00", "2017-09-18T08:39:46+00:00", "release");
    }

    private static LibraryArtifact library(Project project, String coordinate, Path file) {
        return library(project, coordinate, file, "https://repo.maven.apache.org/maven2/");
    }

    private static LibraryArtifact library(Project project, String coordinate, Path file, String repositoryUrl) {
        var library = project.getObjects().newInstance(LibraryArtifact.class);
        library.getCoordinate().set(coordinate);
        library.getFile().fileValue(file.toFile());
        library.getRepositoryUrl().set(repositoryUrl);
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

    private static JsonObject library(JsonArray libraries, String name, boolean natives, String os, String arch) {
        for (var element : libraries) {
            var library = element.getAsJsonObject();
            if (!library.get("name").getAsString().equals(name) || library.has("natives") != natives) {
                continue;
            }
            var ruleOs = library.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonObject("os");
            if (ruleOs.get("name").getAsString().equals(os) && ruleOs.get("arch").getAsString().equals(arch)) {
                return library;
            }
        }
        throw new AssertionError("No library named " + name + " for " + os + "/" + arch + " in " + libraries);
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
