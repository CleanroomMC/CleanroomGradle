package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomLogging;
import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.json.schema.AssetIndexObjects;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.json.schema.ManifestVersion.Versions;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.task.json.ReadJsonFileTask;
import com.cleanroommc.gradle.util.DirectoryUtil;
import com.google.common.base.Suppliers;
import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ManifestTasks {
    public static final String MANIFEST_GROUP = "manifest";
    public static final String GATHER_MANIFEST = "gatherManifest";
    public static final String READ_MANIFEST = "readManifest";
    private final Project rootProject;
    private final Map<String, ManifestTasksForVersion> manifestTasksForVersions;
    private ManifestVersion manifestVersion;

    private final Map<String, VersionMetadata> metadataCache;
    private final Map<String, AssetIndexObjects> assetCache;
    private static ManifestTasks instance;

    public static ManifestTasks get(Logger logger, Project rootProject) {
        if (instance == null) {
            CleanroomLogging.step(logger, "Registering manifest tasks...");

            rootProject = rootProject.getRootProject();
            instance = new ManifestTasks(rootProject);
            instance.registerMainTasks();
            rootProject.afterEvaluate(project -> instance.registerAfterEvaluation());

        }
        return instance;
    }

    private ManifestTasks(Project rootProject) {
        this.rootProject = rootProject;
        manifestTasksForVersions = new HashMap<>();
        metadataCache = new HashMap<>();
        assetCache = new HashMap<>();
    }

    private void registerMainTasks() {
        TaskContainer taskContainer = rootProject.getProject().getRootProject().getTasks();
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
            task.doLast("storeManifest",
                    $task -> {
                        this.manifestVersion = (ManifestVersion) ((ReadJsonFileTask) $task).output;
                    });
        });
    }

    public void registerAfterEvaluation() {
        List<String> defaultTasks = new ArrayList<>();

        for (String vanillaVersion : CleanroomGradle.getVersions()) {
            var manifestVersionTasks = new ManifestTasksForVersion(this, rootProject, vanillaVersion);
            manifestVersionTasks.createTasks(defaultTasks);
            manifestTasksForVersions.put(vanillaVersion, manifestVersionTasks);
        }
        defaultTasks.addAll(rootProject.getGradle().getStartParameter().getTaskNames());
        rootProject.getGradle().getStartParameter().setTaskNames(defaultTasks); // TODO
    }

    public static class ManifestTasksForVersion {
        private final Project project;
        private final ManifestTasks manifestTasks;
        private final String version;
        public final String downloadManifestTaskName;
        public final String readManifestTaskName;
        public final String downloadAssetMetadataCacheTaskName;
        public final String readAssetMetadataCacheTaskName;

        public ManifestTasksForVersion(ManifestTasks manifestTasks, Project project, String version) {
            this.project = project;
            this.version = version;
            this.manifestTasks = manifestTasks;
            downloadManifestTaskName = "download" + version.replace('.', '_') + "Manifest";
            readManifestTaskName = "read" + version.replace('.', '_') + "Manifest";
            downloadAssetMetadataCacheTaskName = "download" + version.replace('.', '_') + "Assets";
            readAssetMetadataCacheTaskName = "read" + version.replace('.', '_') + "Assets";
        }

        private void createTasks(List<String> defaultTasks) {
            var tasks = project.getTasks();

            defaultTasks.add(downloadManifestTaskName);
            TaskProvider<Download> downloadManifestTask = tasks.register(downloadManifestTaskName, Download.class, task -> {
                var urlGetter = project.provider(() -> {
                    return manifestTasks.manifestVersion.versions().stream().
                            filter(v -> v.id().equals(version)).map(Versions::url).findFirst().orElseThrow();
                });
                task.dependsOn(READ_MANIFEST);
                task.src(urlGetter);
                task.dest(DirectoryUtil.create(project, dir -> dir.getVersionManifest(version)));
                task.overwrite(false);
                task.onlyIfModified(true);
                task.useETag(true);
            });

            defaultTasks.add(readManifestTaskName);
            tasks.register(readManifestTaskName, ReadJsonFileTask.class, task -> {
                task.dependsOn(downloadManifestTaskName);
                task.setGroup(MANIFEST_GROUP);
                task.getInputFile().fileProvider(downloadManifestTask.map(Download::getDest));
                task.getType().set(VersionMetadata.class);
                task.doLast("storeManifest", $task -> {
                    manifestTasks.metadataCache.put(version, (VersionMetadata) ((ReadJsonFileTask) $task).output);
                });
            });

            defaultTasks.add(downloadAssetMetadataCacheTaskName);
            TaskProvider<Download> downloadAssetMetadataCacheTask = tasks.register(downloadAssetMetadataCacheTaskName, Download.class, task -> {
                task.dependsOn(readManifestTaskName);
                Supplier<VersionMetadata> versionMetadata = Suppliers.memoize(() -> manifestTasks.metadataCache.get(version));
                var dataUrlProvider = project.provider(() -> versionMetadata.get().assetIndex().url());
                task.src(dataUrlProvider);
                task.dest(project.provider(() -> DirectoryUtil.create(project, dir -> dir.getAssetManifestForVersion(versionMetadata.get().assetIndex().id()))));
                task.overwrite(false);
                task.onlyIfModified(true);
                task.useETag(true);
            });

            defaultTasks.add(readAssetMetadataCacheTaskName);
            tasks.register(readAssetMetadataCacheTaskName, ReadJsonFileTask.class, task -> {
                task.dependsOn(downloadAssetMetadataCacheTaskName);
                task.setGroup(MANIFEST_GROUP);
                task.getInputFile().fileProvider(downloadAssetMetadataCacheTask.map(Download::getDest));
                task.getType().set(AssetIndexObjects.class);
                task.doLast("storeAssetsManifest", $task -> {
                    manifestTasks.assetCache.put(manifestTasks.metadataCache.get(version).assetIndex().id(), (AssetIndexObjects) ((ReadJsonFileTask) $task).output);
                });
            });

        }

    }

}
