/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.ext.NativeFamily;
import com.cleanroommc.gradle.api.util.dist.NativeJars;
import com.cleanroommc.platformutils.Architecture;
import com.cleanroommc.platformutils.OperatingSystem;
import com.cleanroommc.platformutils.Platform;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.nativeplatform.MachineArchitecture;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Wires one configuration per {@link NativeFamily} in {@code cleanroom.natives}.
 *
 * <p>Each family ships one jar per platform. But the published metadata has to serve every distribution.
 * Runtime classpath takes the current platform's classifier.
 * While publications take every platform a family maps, one
 * OS and architecture specific variant each, so a consumer resolves only its own.
 * Family jars always go on the classpath, and Cleanroom loads their natives from there.
 */
public final class Natives {

    public static final String CURRENT_CONFIGURATION_NAME = "nativesCurrent";
    public static final String ALL_CONFIGURATION_NAME = "nativesAll";
    /**
     * The platforms native jars are published for. Every other platform runs on its {@link #canonical(Platform)}.
     */
    public static final List<Platform> NATIVE_PLATFORMS = List.of(
        Platform.of(OperatingSystem.WINDOWS, Architecture.X64), Platform.of(OperatingSystem.WINDOWS, Architecture.X86),
        Platform.of(OperatingSystem.WINDOWS, Architecture.ARM64), Platform.of(OperatingSystem.LINUX, Architecture.X64),
        Platform.of(OperatingSystem.LINUX, Architecture.ARM64), Platform.of(OperatingSystem.LINUX, Architecture.ARM32),
        Platform.of(OperatingSystem.LINUX, Architecture.PPC64LE),
        Platform.of(OperatingSystem.LINUX, Architecture.RISCV64), Platform.of(OperatingSystem.MAC_OS, Architecture.X64),
        Platform.of(OperatingSystem.MAC_OS, Architecture.ARM64),
        Platform.of(OperatingSystem.FREE_BSD, Architecture.X64)
    );
    public static final List<String> PLATFORMS = NATIVE_PLATFORMS.stream().map(Platform::id).toList();

    private Natives() { }

    public static void register(Project project) {
        var natives = natives(project);
        var configurations = project.getConfigurations();
        natives.all(family -> configurations.register(family.getName(), config -> {
            config.setDescription("Modules with per-platform jars, declared without classifiers");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
        }));
        var current = configurations.register(CURRENT_CONFIGURATION_NAME, config -> {
            config.setDescription("Native jars for the current platform");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.withDependencies(dependencies -> create(project, natives, dependencies, Platform.current()));
        });
        configurations.register(ALL_CONFIGURATION_NAME, config -> {
            config.setDescription("Native jars for every platform");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.withDependencies(dependencies -> {
                for (var platform : NATIVE_PLATFORMS) {
                    create(project, natives, dependencies, platform);
                }
            });
        });
        // runtimeElements deliberately stays free of natives
        project.getPlugins().withType(JavaPlugin.class, _ -> {
            configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).configure(config -> config.extendsFrom(current.get()));
            configurations.named(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).configure(config -> config.extendsFrom(current.get()));
        });
    }

    public static void addFor(Project project, Collection<Dependency> target, Platform platform) {
        create(project, natives(project), target, platform);
    }

    /**
     * The platform and handling of every family jar, which launcher entries are written from.
     */
    public static Provider<NativeJars> jars(Project project) {
        var natives = natives(project);
        return project.provider(() -> {
            var platforms = new TreeMap<String, String>();
            for (var family : natives) {
                var classifiers = classifiers(family);
                for (var dependency : project.getConfigurations().getByName(family.getName()).getAllDependencies()) {
                    classifiers.forEach((platform, classifier) -> {
                        var key = NativeJars.key(dependency.getGroup(), dependency.getName(), classifier);
                        var previous = platforms.put(key, platform);
                        if (previous != null && !previous.equals(platform)) {
                            throw new InvalidUserDataException(
                                key + " is mapped to both " + previous + " and " + platform + " in cleanroom.natives"
                            );
                        }
                    });
                }
            }
            return new NativeJars(platforms);
        });
    }

    /**
     * Family modules the runtime graph reaches only through their per-platform jars, as {@code group:module}.
     * A POM cannot name one classifier per platform, so it should leave these out.
     */
    public static Provider<Set<String>> perPlatformOnly(Project project, Provider<ResolvedComponentResult> root) {
        var natives = natives(project);
        var declared = project.provider(() -> {
            var families = new HashSet<String>();
            for (var family : natives) {
                for (var dependency : project.getConfigurations().getByName(family.getName()).getAllDependencies()) {
                    families.add(dependency.getGroup() + ":" + dependency.getName());
                }
            }
            for (var dependency : project.getConfigurations()
                .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .getAllDependencies()) {
                if (dependency instanceof ExternalModuleDependency external && external.getArtifacts().isEmpty()) {
                    families.remove(external.getGroup() + ":" + external.getName());
                }
            }
            return families;
        });
        return root.zip(declared, (component, families) -> {
            var only = new TreeSet<String>();
            var seen = new HashSet<ResolvedComponentResult>();
            var queue = new ArrayDeque<>(List.of(component));
            while (!queue.isEmpty()) {
                var current = queue.poll();
                if (!seen.add(current)) {
                    continue;
                }
                if (current.getId() instanceof ModuleComponentIdentifier id && families.contains(id.getGroup() + ":" + id.getModule()) && current.getDependents().stream().filter(edge -> !edge.isConstraint()).allMatch(edge -> edge.getFrom().equals(component))) {
                    only.add(id.getGroup() + ":" + id.getModule());
                }
                for (var dependency : current.getDependencies()) {
                    if (dependency instanceof ResolvedDependencyResult resolved) {
                        queue.add(resolved.getSelected());
                    }
                }
            }
            return only;
        });
    }

    /**
     * Fails for a boot library that its family maps on only some platforms.
     * The boot list ships to every platform, and Cleanroom's library loader refuses to start where a listed library has no jar.
     */
    public static void requireEveryPlatform(Project project, Collection<String> bootLibraries) {
        for (var family : natives(project)) {
            var missing = new TreeSet<>(PLATFORMS);
            missing.removeAll(classifiers(family).keySet());
            if (missing.isEmpty()) {
                continue;
            }
            for (var dependency : project.getConfigurations().getByName(family.getName()).getAllDependencies()) {
                var module = dependency.getGroup() + ":" + dependency.getName();
                if (bootLibraries.contains(module)) {
                    throw new InvalidUserDataException(
                        module + " is a boot library, but cleanroom.natives." + family.getName() + " maps no jar for " +
                            missing + ". Cleanroom's library loader refuses to start where a boot library has no jar," +
                            " so map every platform or remove it from cleanroom.loader.bootLibraries."
                    );
                }
            }
        }
    }

    /**
     * Systems without published natives run Linux's.
     * Architectures without published natives fall back to x64.
     */
    public static Platform canonical(Platform platform) {
        var exact = find(platform.operatingSystem(), platform.architecture());
        if (exact != null) {
            return exact;
        }
        var os = switch (platform.operatingSystem()) {
            case WINDOWS, MAC_OS, FREE_BSD -> platform.operatingSystem();
            default -> OperatingSystem.LINUX;
        };
        var architecture = switch (os) {
            case WINDOWS ->
                platform.architecture() == Architecture.X86 || platform.architecture() == Architecture.ARM64
                    ? platform.architecture()
                    : Architecture.X64;
            case MAC_OS -> platform.architecture() == Architecture.ARM64 ? Architecture.ARM64 : Architecture.X64;
            case FREE_BSD -> Architecture.X64;
            default -> platform.architecture() == Architecture.X86 ? Architecture.X64 : platform.architecture();
        };
        var canonical = find(os, architecture);
        return canonical == null ? find(os, Architecture.X64) : canonical;
    }

    /**
     * Gradle's {@code OperatingSystemFamily} names already match {@link OperatingSystem#id()}, but its architecture
     * names differ for x64 and arm64.
     */
    public static String machineArchitecture(Platform platform) {
        return switch (platform.architecture()) {
            case X64 -> MachineArchitecture.X86_64;
            case ARM64 -> MachineArchitecture.ARM64;
            default -> platform.architecture().id();
        };
    }

    private static Platform find(OperatingSystem operatingSystem, Architecture architecture) {
        return NATIVE_PLATFORMS.stream()
            .filter(platform -> platform.operatingSystem() == operatingSystem &&
                platform.architecture() == architecture)
            .findFirst()
            .orElse(null);
    }

    private static NamedDomainObjectContainer<NativeFamily> natives(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class).getNatives();
    }

    private static Map<String, String> classifiers(NativeFamily family) {
        var classifiers = family.getClassifiers().get();
        var platforms = new HashMap<String, String>();
        classifiers.forEach((platform, classifier) -> {
            if (!PLATFORMS.contains(platform)) {
                throw new InvalidUserDataException(
                    "cleanroom.natives." + family.getName() + " maps unknown platform " + platform +
                        ". Known platforms are " + PLATFORMS
                );
            }
            var previous = platforms.put(classifier, platform);
            if (previous != null) {
                throw new InvalidUserDataException(
                    "cleanroom.natives." + family.getName() + " maps " + previous + " and " + platform +
                        " to the same classifier " + classifier
                );
            }
        });
        return classifiers;
    }

    private static void create(
        Project project, NamedDomainObjectContainer<NativeFamily> natives, Collection<Dependency> target,
        Platform platform) {
        var id = canonical(platform).id();
        for (var family : natives) {
            var classifier = classifiers(family).get(id);
            if (classifier == null) {
                continue;
            }
            var declared = project.getConfigurations().getByName(family.getName()).getAllDependencies();
            for (var dependency : declared) {
                if (dependency.getVersion() == null || dependency.getVersion().isBlank()) {
                    throw new InvalidUserDataException(
                        dependency.getGroup() + ":" + dependency.getName() + " is declared in " + family.getName() +
                            " without a version. Native classifiers are" +
                            " published one variant each, and a platform does not travel with them."
                    );
                }
                var created = project.getDependencyFactory()
                    .create("%s:%s:%s:%s".formatted(
                        dependency.getGroup(), dependency.getName(), dependency.getVersion(), classifier
                    ));
                created.setTransitive(false);
                target.add(created);
            }
        }
    }

}
