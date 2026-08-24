package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.attributes.Category;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolution-graph helpers shared by the MMC pack and the installer profile.
 */
public final class ResolvedLibraries {

    public static Provider<List<LibraryArtifact>> artifacts(ObjectFactory objects,
                                                             Provider<Set<ResolvedArtifactResult>> resolved,
                                                             Provider<ResolvedComponentResult> root,
                                                             Map<String, String> repositoryUrls) {
        return resolved.zip(root, (artifacts, component) -> {
            var origins = new HashMap<ComponentIdentifier, String>();
            collectRepositoryUrls(component, repositoryUrls, origins, new HashSet<>());
            return artifactInputs(objects, artifacts, origins);
        });
    }

    private static List<LibraryArtifact> artifactInputs(ObjectFactory objects, Set<ResolvedArtifactResult> artifacts,
                                                         Map<ComponentIdentifier, String> repositoryUrls) {
        return artifacts.stream()
                .filter(artifact -> artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)
                .sorted(Comparator.comparing(artifact -> Coordinate.from(artifact).serialized()))
                .map(artifact -> {
                    var input = objects.newInstance(LibraryArtifact.class);
                    input.getCoordinate().set(Coordinate.from(artifact).serialized());
                    input.getFile().fileValue(artifact.getFile());
                    var repositoryUrl = repositoryUrls.get(artifact.getId().getComponentIdentifier());
                    if (repositoryUrl == null) {
                        throw new GradleException("No repository origin found for " + artifact.getId());
                    }
                    input.getRepositoryUrl().set(repositoryUrl);
                    return input;
                })
                .toList();
    }

    public static Provider<List<String>> modules(Provider<ResolvedComponentResult> root) {
        return root.map(component -> {
            var coordinates = new LinkedHashSet<String>();
            collectModules(component, coordinates, new HashSet<>());
            return List.copyOf(coordinates);
        });
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

    private static void collectModules(ResolvedComponentResult component, Set<String> coordinates, Set<ComponentIdentifier> seen) {
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

    private static void collectRepositoryUrls(ResolvedComponentResult component, Map<String, String> repositories,
                                               Map<ComponentIdentifier, String> origins, Set<ComponentIdentifier> seen) {
        if (!seen.add(component.getId())) {
            return;
        }
        if (component.getId() instanceof ModuleComponentIdentifier) {
            if (!(component instanceof ResolvedComponentResultInternal internal)) {
                throw new GradleException("Gradle did not expose the repository for " + component.getId());
            }
            var repositoryId = internal.getRepositoryId();
            var repositoryUrl = repositories.get(repositoryId);
            if (repositoryUrl == null) {
                throw new GradleException("Resolved " + component.getId() + " from unknown repository '"
                        + repositoryId + "'. Configured Maven repositories: " + repositories.keySet());
            }
            origins.put(component.getId(), repositoryUrl);
        }
        for (var dependency : component.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                collectRepositoryUrls(resolved.getSelected(), repositories, origins, seen);
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

    private ResolvedLibraries() { }

}
