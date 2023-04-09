package com.cleanroommc.gradle.task.artifact;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.dependency.MinecraftDependency;
import com.cleanroommc.gradle.dependency.Side;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.task.ManifestTasks;
import com.cleanroommc.gradle.util.ClosureUtil;
import com.cleanroommc.gradle.util.DirectoryUtil;
import de.undercouch.gradle.tasks.download.Download;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.TaskContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ArtifactTasks {

    // TODO: 09/04/2023 improve 
    public static void registerAfterEvaluation(Project project) {
        Set<MinecraftDependency> minecraftDependencies = MinecraftDependency.getMinecraftDependencies(project);
        List<String> defaultTasks = new ArrayList<>();
        TaskContainer tasks = project.getTasks();
        for (MinecraftDependency vanillaVersion : minecraftDependencies) {
            String downloadArtifactsTaskName = downloadArtifactsTaskName(vanillaVersion.getTaskDescription());
            defaultTasks.add(downloadArtifactsTaskName);

            tasks.register(downloadArtifactsTaskName, Download.class, task -> {
                Closure<List<String>> urlGetter = ClosureUtil.of(() -> {
                    MapProperty<String, VersionMetadata> versionMetadataMap = task.getProject().getExtensions().getByType(ManifestExtension.class).getMetadataCache();
                    VersionMetadata versionMetadata = versionMetadataMap.get().get(vanillaVersion.getVanillaVersion());
                    return filterArtifacts(versionMetadata, vanillaVersion);
                });
                task.dependsOn(ManifestTasks.readManifestTaskName(vanillaVersion.getVanillaVersion()));
                task.src(urlGetter);
                task.dest(DirectoryUtil.create(project, dir -> dir.getArtifacts(vanillaVersion.getVanillaVersion())));
                task.overwrite(false);
                task.onlyIfModified(true);
                task.useETag(true);
            });

        }
        defaultTasks.addAll(project.getGradle().getStartParameter().getTaskNames());
        project.getGradle().getStartParameter().setTaskNames(defaultTasks); // TODO
    }

    private static List<String> filterArtifacts(VersionMetadata versionMetadata, MinecraftDependency vanillaVersion) {
        List<String> artifactUrls = new ArrayList<>();
        for (VersionMetadata.Library library : versionMetadata.libraries()) {
            if (library.rules() != null && !library.rules().isEmpty()) {
                for (VersionMetadata.Rule rule : library.rules()) {
                    if (rule.appliesToOS() && rule.isAllowed()) {
                        artifactUrls.add(Objects.requireNonNull(library.artifact()).url());
                    }
                }
                continue;
            }

            artifactUrls.add(Objects.requireNonNull(library.artifact()).url());
        }

        switch (vanillaVersion.getInternalSide()) {
            case JOINED -> {
                artifactUrls.add(versionMetadata.downloads().get(Side.CLIENT_ONLY.getValue()).url());
                artifactUrls.add(versionMetadata.downloads().get(Side.SERVER_ONLY.getValue()).url());
            }

            case CLIENT_ONLY -> artifactUrls.add(versionMetadata.downloads().get(Side.CLIENT_ONLY.getValue()).url());
            case SERVER_ONLY -> artifactUrls.add(versionMetadata.downloads().get(Side.SERVER_ONLY.getValue()).url());
        }


        return artifactUrls;
    }

    public static String downloadArtifactsTaskName(String version) {
        return "download" + version.replace('.', '_') + "Artifacts";
    }

    private ArtifactTasks() {
    }

}
