/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.dist;

import com.cleanroommc.launcherutils.Coordinate;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolution-graph helpers shared by the MMC pack and the installer profile.
 */
public final class ResolvedLibraries {

    private static final String EXCLUDE_WILDCARD = "*";
    private static final String LOCAL_GROUP = "local";

    private ResolvedLibraries() { }

    public static Provider<List<LibraryArtifact>> artifacts(
        ObjectFactory objects, Provider<Configuration> configuration, Provider<Map<String, String>> repositoryUrls) {
        return artifacts(
            objects, configuration.flatMap(config -> config.getIncoming().getArtifacts().getResolvedArtifacts()),
            configuration.flatMap(config -> config.getIncoming().getResolutionResult().getRootComponent()),
            repositoryUrls
        );
    }

    public static Provider<List<LibraryArtifact>> artifacts(
        ObjectFactory objects, Provider<Set<ResolvedArtifactResult>> resolved, Provider<ResolvedComponentResult> root,
        Provider<Map<String, String>> repositoryUrls) {
        return repositoryUrls.flatMap(urls -> resolved.zip(root, (artifacts, component) -> {
            var origins = new HashMap<ComponentIdentifier, String>();
            var identities = new HashMap<ComponentIdentifier, ModuleVersionIdentifier>();
            collectRepositoryUrls(component, urls, origins, identities, new HashSet<>());
            return artifactInputs(objects, artifacts, origins, identities);
        }));
    }

    public static Provider<List<String>> modules(Provider<ResolvedComponentResult> root) {
        return root.map(component -> {
            var coordinates = new LinkedHashSet<String>();
            collectModules(component, coordinates, new HashSet<>());
            return List.copyOf(coordinates);
        });
    }

    public static Set<String> excludeRules(Configuration configuration) {
        var rules = new TreeSet<String>();
        for (var parent : configuration.getHierarchy()) {
            for (var rule : parent.getExcludeRules()) {
                rules.add(excludePart(rule.getGroup()) + ":" + excludePart(rule.getModule()));
            }
        }
        return rules;
    }

    // Gradle publishes a declared dependency even when an exclude rule of the same configuration matches it
    public static void removeExcluded(Collection<Dependency> dependencies, Configuration configuration) {
        var rules = excludeRules(configuration);
        dependencies.removeIf(dependency -> isExcluded(
            Coordinate.of(dependency.getGroup(), dependency.getName(), dependency.getVersion()), rules
        ));
    }

    public static boolean isExcluded(Coordinate coordinate, Set<String> rules) {
        for (var value : rules) {
            var parts = value.split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new GradleException("Invalid library exclude rule: " + value);
            }
            if ((parts[0].equals(EXCLUDE_WILDCARD) || parts[0].equals(coordinate.group())) &&
                (parts[1].equals(EXCLUDE_WILDCARD) || parts[1].equals(coordinate.artifact()))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> mergeNatives(List<String> modules, List<String> natives) {
        var versions = new HashMap<String, String>();
        for (var module : modules) {
            var parts = module.split(":");
            versions.put(parts[0] + ":" + parts[1], parts[2]);
        }
        var libraries = new LinkedHashSet<>(modules);
        for (var nativeLibrary : natives) {
            var parts = nativeLibrary.split(":");
            var module = parts[0] + ":" + parts[1];
            var version = versions.get(module);
            if (version == null) {
                continue;
            }
            libraries.add(module + ":" + version + ":" + parts[2]);
        }
        return List.copyOf(libraries);
    }

    private static List<LibraryArtifact> artifactInputs(
        ObjectFactory objects, Set<ResolvedArtifactResult> artifacts, Map<ComponentIdentifier, String> repositoryUrls,
        Map<ComponentIdentifier, ModuleVersionIdentifier> identities) {
        return artifacts.stream().map(artifact -> {
            var component = artifact.getId().getComponentIdentifier();
            var input = objects.newInstance(LibraryArtifact.class);
            input.getFile().fileValue(artifact.getFile());
            if (component instanceof ModuleComponentIdentifier) {
                var repositoryUrl = repositoryUrls.get(component);
                if (repositoryUrl == null) {
                    throw new GradleException("No repository origin found for " + artifact.getId());
                }
                input.getCoordinate().set(coordinate(artifact).toString());
                input.getRepositoryUrl().set(repositoryUrl);
            } else {
                // Projects, included builds and plain files come from no repository, so their jars travel with the distribution
                input.getCoordinate().set(localCoordinate(artifact.getFile(), identities.get(component)).toString());
                input.getRepositoryUrl().set(artifact.getFile().getParentFile().toURI().toString());
            }
            return input;
        }).sorted(Comparator.comparing(input -> input.getCoordinate().get())).toList();
    }

    private static Coordinate coordinate(ResolvedArtifactResult artifact) {
        if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier module)) {
            throw new GradleException("Expected a module artifact: " + artifact.getId());
        }
        var fileName = artifact.getFile().getName();
        var dot = fileName.lastIndexOf('.');
        var extension = dot == -1 ? "jar" : fileName.substring(dot + 1);
        var stem = dot == -1 ? fileName : fileName.substring(0, dot);
        var prefix = module.getModule() + "-" + module.getVersion();
        if (!stem.equals(prefix) && !stem.startsWith(prefix + "-")) {
            throw new GradleException(
                "Cannot derive the classifier for " + artifact.getId() + " from file " + fileName
            );
        }
        var classifier = stem.length() == prefix.length() ? null : stem.substring(prefix.length() + 1);
        return Coordinate.of(module.getGroup(), module.getModule(), module.getVersion())
            .withClassifier(classifier)
            .withExtension(extension);
    }

    /**
     * A coordinate for an artifact no repository serves. A project or an included build keeps its module identity,
     * a plain file dependency, passed with a {@code null} module, is named after itself.
     */
    private static Coordinate localCoordinate(File file, ModuleVersionIdentifier module) {
        var fileName = file.getName();
        var dot = fileName.lastIndexOf('.');
        var extension = dot == -1 ? "jar" : fileName.substring(dot + 1);
        var stem = dot == -1 ? fileName : fileName.substring(0, dot);
        if (module == null) {
            return Coordinate.of(LOCAL_GROUP, stem.replaceAll("[^A-Za-z0-9._-]", "_"), "0").withExtension(extension);
        }
        var prefix = module.getName() + "-" + module.getVersion() + "-";
        var classifier = stem.startsWith(prefix) ? stem.substring(prefix.length()) : null;
        var group = module.getGroup().isBlank() ? LOCAL_GROUP : module.getGroup();
        return Coordinate.of(group, module.getName(), module.getVersion())
            .withClassifier(classifier)
            .withExtension(extension);
    }

    private static void collectModules(
        ResolvedComponentResult component, Set<String> coordinates, Set<ComponentIdentifier> seen) {
        if (!seen.add(component.getId())) {
            return;
        }
        if (component.getId() instanceof ModuleComponentIdentifier module && !isPlatform(component)) {
            coordinates.add(module.getGroup() + ":" + module.getModule() + ":" + module.getVersion());
        }
        for (var dependency : component.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                collectModules(resolved.getSelected(), coordinates, seen);
            }
        }
    }

    private static void collectRepositoryUrls(
        ResolvedComponentResult component, Map<String, String> repositories, Map<ComponentIdentifier, String> origins,
        Map<ComponentIdentifier, ModuleVersionIdentifier> identities, Set<ComponentIdentifier> seen) {
        if (!seen.add(component.getId())) {
            return;
        }
        identities.put(component.getId(), component.getModuleVersion());
        if (component.getId() instanceof ModuleComponentIdentifier) {
            if (!(component instanceof ResolvedComponentResultInternal internal)) {
                throw new GradleException("Gradle did not expose the repository for " + component.getId());
            }
            var repositoryId = internal.getRepositoryId();
            var repositoryUrl = repositories.get(repositoryId);
            if (repositoryUrl == null) {
                throw new GradleException(
                    "Resolved " + component.getId() + " from unknown repository '" + repositoryId +
                        "'. Configured Maven repositories: " + repositories.keySet()
                );
            }
            origins.put(component.getId(), repositoryUrl);
        }
        for (var dependency : component.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                collectRepositoryUrls(resolved.getSelected(), repositories, origins, identities, seen);
            }
        }
    }

    private static boolean isPlatform(ResolvedComponentResult component) {
        for (var variant : component.getVariants()) {
            var attributes = variant.getAttributes();
            for (var attribute : attributes.keySet()) {
                if (!Category.CATEGORY_ATTRIBUTE.getName().equals(attribute.getName())) {
                    continue;
                }
                var category = String.valueOf(attributes.getAttribute(attribute));
                if (Category.REGULAR_PLATFORM.equals(category) || Category.ENFORCED_PLATFORM.equals(category)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String excludePart(String value) {
        return value == null ? EXCLUDE_WILDCARD : value;
    }

}
