package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.dependency.MinecraftDependency;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.json.schema.ManifestVersion.Versions;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.task.json.ReadJsonFileTask;
import com.cleanroommc.gradle.util.ClosureUtil;
import de.undercouch.gradle.tasks.download.Download;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ManifestTasks {

    public static final String MANIFEST_GROUP = "manifest";

    public static final String GATHER_MANIFEST = "gatherManifest";
    public static final String READ_MANIFEST = "readManifest";

    public static void register(TaskContainer taskContainer) {
        TaskProvider<Download> gatherManifestTask = taskContainer.register(GATHER_MANIFEST, Download.class, task -> {
            task.setGroup(MANIFEST_GROUP);
            // task.onlyIf($task -> !$task.getProject().getExtensions().getByType(ManifestExtension.class).getLocation().get().getAsFile().exists());
            task.src(CleanroomMeta.VERSION_MANIFESTS_V2_URL);
            task.dest(task.getProject().getExtensions().getByType(ManifestExtension.class).getLocation());
            task.overwrite(false);
            task.onlyIfModified(true);
            task.useETag(true);
        });
        taskContainer.register(READ_MANIFEST, ReadJsonFileTask.class, task -> {
            task.setOnlyIf($task -> $task.getProject().getExtensions().findByType(ManifestVersion.class) == null);
            task.setGroup(MANIFEST_GROUP);
            task.getInputFile().fileProvider(gatherManifestTask.map(Download::getDest));
            task.getType().set(ManifestVersion.class);
            task.doLast("storeManifest", $task -> $task.getProject().getExtensions().getByType(ManifestExtension.class).getVersions()
                    .set((ManifestVersion) ((ReadJsonFileTask) $task).output));
        });
    }

    public static void registerAfterEvaluation(Project project) {
        Set<MinecraftDependency> minecraftDependencies = MinecraftDependency.getMinecraftDependencies(project);
        List<String> defaultTasks = new ArrayList<>();
        TaskContainer tasks = project.getTasks();
        for (String vanillaVersion : MinecraftDependency.getUniqueVanillaVersions(minecraftDependencies)) {
            String downloadManifestTaskName = "download" + vanillaVersion.replace('.', '_') + "Manifest";
            defaultTasks.add(downloadManifestTaskName);
            TaskProvider<Download> downloadManifestTask  = tasks.register(downloadManifestTaskName, Download.class, task -> {
                Closure<String> urlGetter = ClosureUtil.of(() -> {
                    ManifestVersion manifestVersion = task.getProject().getExtensions().getByType(ManifestExtension.class).getVersions().get();
                    return manifestVersion.versions().stream().filter(v -> v.id().equals(vanillaVersion)).map(Versions::url).findFirst().orElseThrow();
                });
                task.dependsOn(READ_MANIFEST);
                task.src(urlGetter);
                task.dest(CleanroomMeta.getVanillaVersionsCacheDirectory(project, vanillaVersion));
                task.overwrite(false);
                task.onlyIfModified(true);
                task.useETag(true);
            });

            String readManifestTaskName = "read" + vanillaVersion.replace('.', '_') + "Manifest";
            defaultTasks.add(readManifestTaskName);
            tasks.register(readManifestTaskName, ReadJsonFileTask.class, task -> {
                task.setGroup(MANIFEST_GROUP);
                task.dependsOn(downloadManifestTaskName);
                task.getInputFile().fileProvider(downloadManifestTask.map(Download::getDest));
                task.getType().set(VersionMetadata.class);
                task.doLast("storeManifest", $task -> $task.getProject().getExtensions().getByType(ManifestExtension.class).getMetadataCache()
                        .put(vanillaVersion, (VersionMetadata) ((ReadJsonFileTask) $task).output));
            });
        }
        defaultTasks.addAll(project.getGradle().getStartParameter().getTaskNames());
        project.getGradle().getStartParameter().setTaskNames(defaultTasks); // TODO
    }

    private ManifestTasks() { }

}
