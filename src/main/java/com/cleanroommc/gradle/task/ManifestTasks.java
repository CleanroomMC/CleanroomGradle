package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomLogging;
import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.dependency.Side;
import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.json.schema.AssetIndexObjects;
import com.cleanroommc.gradle.json.schema.IDownload;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.json.schema.ManifestVersion.Versions;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.task.json.ReadJsonFileTask;
import com.cleanroommc.gradle.util.DirectoryUtil;
import com.cleanroommc.gradle.util.TaskUtil;
import com.google.common.base.Suppliers;
import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ManifestTasks {
    public static final String MANIFEST_GROUP = "manifest";
    public static final String GATHER_MANIFEST = "gatherManifest";
    private static TaskProvider<Download> gatherManifestTask;
    public static final String READ_MANIFEST = "readManifest";
    private static TaskProvider<ReadJsonFileTask> readManifestTask;
    private final Project rootProject;
    private final Map<String, ManifestTasksForVersion> manifestTasksForVersions;
    private ManifestVersion manifestVersion;

    private final Map<String, VersionMetadata> metadataCache;
    private final Map<String, AssetIndexObjects> assetCache;
    private static ManifestTasks instance;

    public static void create(Logger logger, Project rootProject) {
        if (instance == null) {
            CleanroomLogging.step(logger, "Registering manifest tasks...");

            rootProject = rootProject.getRootProject();
            instance = new ManifestTasks(rootProject);
            instance.registerMainTasks();
            rootProject.afterEvaluate(project -> instance.registerAfterEvaluation());

        }
    }

    public static ManifestTasks getInstance() {
        if (instance == null) {
            throw new NullPointerException();
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
        TaskContainer taskContainer = rootProject.getTasks();
        gatherManifestTask = taskContainer.register(GATHER_MANIFEST, Download.class, task -> {
            task.setGroup(MANIFEST_GROUP);
            // task.onlyIf($task -> !$task.getProject().getExtensions().getByType(ManifestExtension.class).getLocation().get().getAsFile().exists());
            task.src(CleanroomMeta.VERSION_MANIFESTS_V2_URL);
            task.dest(task.getProject().getExtensions().getByType(ManifestExtension.class).getLocation());
            task.overwrite(false);
            task.onlyIfModified(true);
            task.useETag(true);
        });
        readManifestTask = taskContainer.register(READ_MANIFEST, ReadJsonFileTask.class, task -> {
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
            var manifestVersionTasks = new ManifestTasksForVersion(vanillaVersion);
            manifestVersionTasks.createTasks(defaultTasks);
            manifestTasksForVersions.put(vanillaVersion, manifestVersionTasks);
        }
        defaultTasks.addAll(rootProject.getGradle().getStartParameter().getTaskNames());
        rootProject.getGradle().getStartParameter().setTaskNames(defaultTasks); // TODO
    }

    public class ManifestTasksForVersion {
        public final String version;
        public final String downloadManifestTaskName;
        public final String readManifestTaskName;
        public final String downloadAssetMetadataCacheTaskName;
        public final String readAssetMetadataCacheTaskName;
        public final String downloadDependenciesTaskName;
        public final String downloadNativesTaskName;
        public final String downloadSideTaskName;
        public final String downloadAssetsTaskName;

        private final Map<String, TaskProvider<?>> taskProviderMap;

        public TaskProvider<?> getTask(String name) {
            return taskProviderMap.get(name);
        }

        private ManifestTasksForVersion(String version) {
            this.version = version;
            taskProviderMap = new HashMap<>();
            downloadManifestTaskName = "download" + version.replace('.', '_') + "Manifest";
            readManifestTaskName = "read" + version.replace('.', '_') + "Manifest";
            downloadAssetMetadataCacheTaskName = "download" + version.replace('.', '_') + "AssetsMetadata";
            readAssetMetadataCacheTaskName = "read" + version.replace('.', '_') + "Assets";
            downloadDependenciesTaskName = "download" + version.replace('.', '_') + "Dependencies";
            downloadNativesTaskName = "download" + version.replace('.', '_') + "Natives";
            downloadSideTaskName = "download" + version.replace('.', '_') + "Side";
            downloadAssetsTaskName = "download" + version.replace('.', '_') + "Assets";
        }

        private void createTasks(List<String> defaultTasks) {
            defaultTasks.add(downloadManifestTaskName);
            var downloadManifestTask = register(
                    TaskUtil.registerDynamicDownloadTask(
                            rootProject,
                            downloadManifestTaskName,
                            () -> manifestVersion.versions().stream().
                                    filter(v -> v.id().equals(version)).map(Versions::url).findFirst().orElseThrow(),
                            () -> DirectoryUtil.create(rootProject, dir -> dir.getVersionManifest(version)),
                            READ_MANIFEST
                    ));

            defaultTasks.add(readManifestTaskName);
            var readManifestTask = register(readManifestTaskName, ReadJsonFileTask.class,
                    task -> {
                        task.dependsOn(downloadManifestTaskName);
                        task.setGroup(MANIFEST_GROUP);
                        task.getInputFile().fileProvider(downloadManifestTask.map(Download::getDest));
                        task.getType().set(VersionMetadata.class);
                        task.doLast("storeManifest", $task -> {
                            metadataCache.put(version, (VersionMetadata) ((ReadJsonFileTask) $task).output);
                        });
                    });


            defaultTasks.add(downloadAssetMetadataCacheTaskName);
            var downloadAssetMetadataCacheTask = register(downloadAssetMetadataCacheTaskName, Download.class,
                    task -> {
                        task.dependsOn(readManifestTaskName);
                        Supplier<VersionMetadata> versionMetadata = Suppliers.memoize(() -> metadataCache.get(version));
                        var dataUrlProvider = rootProject.provider(() -> versionMetadata.get().assetIndex().url());
                        task.src(dataUrlProvider);
                        task.dest(rootProject.provider(() -> DirectoryUtil.create(rootProject, dir -> dir.getAssetManifestForVersion(versionMetadata.get().assetIndex().id()))));
                        task.overwrite(false);
                        task.onlyIfModified(true);
                        task.useETag(true);
                    });

            defaultTasks.add(readAssetMetadataCacheTaskName);
            var readAssetMetadataCacheTask = register(readAssetMetadataCacheTaskName, ReadJsonFileTask.class,
                    task -> {
                        task.dependsOn(downloadAssetMetadataCacheTaskName);
                        task.setGroup(MANIFEST_GROUP);
                        task.getInputFile().fileProvider(downloadAssetMetadataCacheTask.map(Download::getDest));
                        task.getType().set(AssetIndexObjects.class);
                        task.doLast("storeAssetsManifest", $task -> {
                            assetCache.put(metadataCache.get(version).assetIndex().id(), (AssetIndexObjects) ((ReadJsonFileTask) $task).output);
                        });
                    });


            defaultTasks.add(downloadDependenciesTaskName);
            var downloadDependenciesTask = registerDynamicDownloadTask(downloadDependenciesTaskName,
                    () -> {
                        List<IDownload> dependencies = new ArrayList<>();
                        for (VersionMetadata.Library library : metadataCache.get(version).libraries()) {
                            if (library.isValidForOS()) {
                                var lib = library.artifact();

                                if (lib != null)
                                    dependencies.add(lib);
                            }
                        }
                        return dependencies;
                    },
                    () -> DirectoryUtil.create(rootProject, dir -> dir.getLibs(version)),
                    readAssetMetadataCacheTaskName);

            defaultTasks.add(downloadNativesTaskName);
            registerDynamicDownloadTask(downloadNativesTaskName,
                    () -> {
                        List<IDownload> natives = new ArrayList<>();

                        for (VersionMetadata.Library library : metadataCache.get(version).libraries()) {
                            if (library.hasNativesForOS()) {
                                var lib = library.classifierForOS();
                                if (lib != null)
                                    natives.add(lib);
                            }
                        }
                        return natives;
                    },
                    () -> DirectoryUtil.create(rootProject, dir -> dir.getNatives(version)),
                    readAssetMetadataCacheTaskName);


            defaultTasks.add(downloadSideTaskName);
            registerDynamicDownloadTask(downloadSideTaskName,
                    () -> {
                        List<IDownload> side = new ArrayList<>(2);
                        VersionMetadata metadata = metadataCache.get(version);
                        side.add(metadata.downloads().get(Side.CLIENT_ONLY.getValue()));
                        side.add(metadata.downloads().get(Side.SERVER_ONLY.getValue()));

                        return side;
                    },
                    () -> DirectoryUtil.create(rootProject, dir -> dir.getSide(version)),
                    readAssetMetadataCacheTaskName);


            defaultTasks.add(downloadAssetsTaskName);
            registerDynamicDownloadTask(downloadAssetsTaskName,
                    () -> assetCache.get(metadataCache.get(version).assetIndex().id()).getObjectStream().
                             map(object -> new IDownload() {
                                 @Override
                                 public String sha1() {
                                     return object.hash();
                                 }

                                 @Override
                                 public long size() {
                                     return object.size();
                                 }

                                 @Override
                                 public String url() {
                                     return CleanroomMeta.RESOURCES_BASE_URL + object.getPath();
                                 }
                             }).toList(),

                    () -> DirectoryUtil.create(rootProject,
                            dir -> dir.getAssetDirForVersion(metadataCache.get(version).assetIndex().id())),
                    readAssetMetadataCacheTaskName);

        }

        private <T extends Task> TaskProvider<T> register(String name, Class<T> type, Action<? super T> configurationAction) {
            TaskProvider<T> task = rootProject.getTasks().register(name, type, configurationAction);
            taskProviderMap.put(name, task);
            return task;
        }

        private <T extends Task> TaskProvider<T> register(TaskProvider<T> task) {
            taskProviderMap.put(task.getName(), task);
            return task;
        }

        private TaskProvider<Download> registerDynamicDownloadTask(String taskName, com.google.common.base.Supplier<Object> data,
                                                                   com.google.common.base.Supplier<File> targetDir, String dependsOn) {
            var task = TaskUtil.registerDynamicDownloadTaskWithSha1Validation(rootProject, taskName,
                    data, targetDir, dependsOn);
            taskProviderMap.put(taskName, task);
            return task;
        }

    }

}
