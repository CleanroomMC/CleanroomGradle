package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaDistributionLibrariesTest {

    @TempDir
    Path directory;

    @Test
    void distributionGraphOmitsLwjgl2AndKeepsEveryPlatform() {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var libraries = project.getConfigurations().create("distributionLibraries");
        var natives = project.getConfigurations().create("distributionNatives");
        var meta = versionMeta(
                jar("com.paulscode:soundsystem:20120107"),
                jar("com.google.guava:guava:21.0"),
                jar("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209"),
                jar("org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209"),
                natives("org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209",
                        "natives-linux", "natives-osx", "natives-windows"),
                natives("net.java.jinput:jinput-platform:2.0.5",
                        "natives-linux", "natives-osx", "natives-windows"),
                nativesWithArtifact("com.mojang:text2speech:1.10.3", "natives-linux", "natives-windows"),
                nativesWithArtifact("ca.weblite:java-objc-bridge:1.0.0", "natives-osx"));

        VanillaTasks.addDistributionLibraries(project.getDependencyFactory(), libraries.getDependencies(), meta);
        VanillaTasks.addDistributionNatives(project.getDependencyFactory(), natives.getDependencies(), meta,
                Map.of("ca.weblite:java-objc-bridge", "1.2", "com.mojang:text2speech", "1.10.3"));

        var libraryNames = names(libraries.getDependencies());
        assertEquals(Set.of(
                "com.paulscode:soundsystem:20120107",
                "com.google.guava:guava:21.0",
                "com.mojang:text2speech:1.10.3",
                "ca.weblite:java-objc-bridge:1.0.0"), libraryNames);
        assertTrue(libraryNames.stream().noneMatch(name -> name.startsWith(VanillaTasks.LWJGL2_GROUP + ":")));

        var nativeNames = names(natives.getDependencies());
        assertEquals(Set.of(
                "net.java.jinput:jinput-platform:2.0.5:natives-linux",
                "net.java.jinput:jinput-platform:2.0.5:natives-osx",
                "net.java.jinput:jinput-platform:2.0.5:natives-windows",
                "com.mojang:text2speech:1.10.3:natives-linux",
                "com.mojang:text2speech:1.10.3:natives-windows"), nativeNames);
        assertTrue(nativeNames.stream().noneMatch(name -> name.startsWith(VanillaTasks.LWJGL2_GROUP + ":")));
        assertTrue(nativeNames.stream().noneMatch(name -> name.startsWith("ca.weblite:java-objc-bridge:")));
        assertFalse(nativeNames.contains("org.lwjgl:lwjgl:3.4.2:natives-linux"));
    }

    @Test
    void localGraphPrefersReplacementOrdinaryArtifact() {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var natives = project.getConfigurations().create("vanillaNatives");
        var meta = versionMeta(
                natives("net.java.jinput:jinput-platform:2.0.5",
                        "natives-linux", "natives-osx", "natives-windows"),
                nativesWithArtifact("ca.weblite:java-objc-bridge:1.0.0",
                        "natives-linux", "natives-osx", "natives-windows"));

        VanillaTasks.addNatives(project.getDependencyFactory(), natives.getDependencies(), meta,
                Map.of("ca.weblite:java-objc-bridge", "1.2", "net.java.jinput:jinput-platform", "2.0.10"));

        var nativeNames = names(natives.getDependencies());
        assertEquals(1, nativeNames.size());
        assertTrue(nativeNames.iterator().next().startsWith("net.java.jinput:jinput-platform:2.0.5:natives-"));
        assertTrue(nativeNames.stream().noneMatch(name -> name.startsWith("ca.weblite:java-objc-bridge:")));
    }

    private static Set<String> names(Iterable<Dependency> dependencies) {
        return StreamSupport.stream(dependencies.spliterator(), false)
                .map(VanillaDistributionLibrariesTest::notation)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String notation(Dependency dependency) {
        var name = dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion();
        if (dependency instanceof ExternalModuleDependency module && !module.getArtifacts().isEmpty()) {
            var classifier = module.getArtifacts().iterator().next().getClassifier();
            if (classifier != null && !classifier.isBlank()) {
                return name + ":" + classifier;
            }
        }
        return name;
    }

    private static VersionMeta versionMeta(VersionMeta.Library... libraries) {
        return new VersionMeta(null,
                new VersionMeta.AssetIndex("1.12", 1, null, "sha1", 1, "https://example.invalid/1.12.json"),
                "1.12", 0,
                Map.of("client", new VersionMeta.Download(null, "sha1", 1, "https://example.invalid/client.jar")),
                "1.12.2", new VersionMeta.JavaVersion("jre-legacy", 8), List.of(libraries), null,
                "net.minecraft.client.main.Main", "", 18,
                "2017-09-18T08:39:46+00:00", "2017-09-18T08:39:46+00:00", "release");
    }

    private static VersionMeta.Library jar(String name) {
        return new VersionMeta.Library(new VersionMeta.Downloads(
                new VersionMeta.Download("path", "sha1", 1, "https://example.invalid/" + name), Map.of()),
                name, null, null, null);
    }

    private static VersionMeta.Library natives(String name, String... classifiers) {
        return natives(name, false, classifiers);
    }

    private static VersionMeta.Library nativesWithArtifact(String name, String... classifiers) {
        return natives(name, true, classifiers);
    }

    private static VersionMeta.Library natives(String name, boolean withArtifact, String... classifiers) {
        var downloads = new LinkedHashMap<String, VersionMeta.Download>();
        var platforms = new LinkedHashMap<String, String>();
        var coordinates = name.split(":");
        var basePath = coordinates[0].replace('.', '/') + "/" + coordinates[1] + "/" + coordinates[2] + "/"
                + coordinates[1] + "-" + coordinates[2] + "-";
        for (var classifier : classifiers) {
            downloads.put(classifier, new VersionMeta.Download(basePath + classifier + ".jar", "sha1", 1,
                    "https://example.invalid/" + classifier));
            platforms.put(classifier.substring("natives-".length()), classifier);
        }
        var artifact = withArtifact
                ? new VersionMeta.Download(basePath.substring(0, basePath.length() - 1) + ".jar", "sha1", 1,
                "https://example.invalid/" + coordinates[1] + ".jar")
                : null;
        return new VersionMeta.Library(new VersionMeta.Downloads(artifact, downloads), name, platforms, null, null);
    }

}
