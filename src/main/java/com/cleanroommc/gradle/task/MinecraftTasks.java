package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomLogging;
import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.dependency.MinecraftDependency;
import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.task.run.RunMinecraftTask;
import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public final class MinecraftTasks {
    public static void create(Logger logger, Project project) {
        ManifestTasks.create(logger, project);
        RunTask.create(logger, project);
    }

    private static class ManifestTasks {
        public static final String MANIFEST_GROUP = "manifest";
        public static final String GATHER_MANIFEST = "gatherManifest";
        private final TaskProvider<Download> gatherManifestTask;
        public static final String PREPARE_NEEDED_MANIFESTS = "prepareNeededManifests";
        private TaskProvider<ManifestDownloader> manifestParserTask;
        public static final String VERSION_DOWNLOADER_TASK = "versionDownloaderTask";
        private TaskProvider<MinecraftDownloader> versionDownloaderTask;

        public static void create(Logger logger, Project project) {
            CleanroomLogging.step(logger, "Registering manifest tasks...");
            var tasks = new ManifestTasks(project);
            project.afterEvaluate(tasks::afterEval);
        }

        private ManifestTasks(Project project) {
            TaskContainer taskContainer = project.getTasks();
            gatherManifestTask = taskContainer.register(GATHER_MANIFEST, Download.class, task -> {
                task.setGroup(MANIFEST_GROUP);
                // task.onlyIf($task -> !$task.getProject().getExtensions().getByType(ManifestExtension.class).getLocation().get().getAsFile().exists());
                task.src(CleanroomMeta.VERSION_MANIFESTS_V2_URL);
                task.dest(task.getProject().getExtensions().getByType(ManifestExtension.class).getLocation());
                task.overwrite(false);
                task.onlyIfModified(true);
                task.useETag(true);
            });
        }

        private void afterEval(Project project) {
            TaskContainer taskContainer = project.getTasks();
            manifestParserTask = taskContainer.register(PREPARE_NEEDED_MANIFESTS, ManifestDownloader.class, task -> {
                task.setGroup(MANIFEST_GROUP);
                task.dependsOn(gatherManifestTask);
                task.getInputFile().fileProvider(gatherManifestTask.map(Download::getDest));
            });
            versionDownloaderTask = taskContainer.register(VERSION_DOWNLOADER_TASK, MinecraftDownloader.class, task -> {
                task.setGroup(MANIFEST_GROUP);
                task.dependsOn(manifestParserTask);
                task.getManifests().set(manifestParserTask.map(ManifestDownloader::getManifests).get());
            });
        }
    }

    private static class RunTask {
        public final TaskProvider<RunMinecraftTask> run;

        public static void create(Logger logger, Project project) {
            project.afterEvaluate(p -> {
                CleanroomLogging.step(logger, "Registering run tasks...");
                for (MinecraftDependency mcDep : p.getExtensions().getByType(CleanroomGradle.class).getMinecraftDependencies()) {
                    new RunTask(p, mcDep);
                }
            });

        }

        public static String getName(MinecraftDependency mcDep) {
            return "run_" + mcDep.getTaskDescription();
        }

        public RunTask(Project project, MinecraftDependency mcDep) {
            run = project.getTasks().register(getName(mcDep), RunMinecraftTask.class, task -> {
                task.getVersion().set(mcDep.getVersion());
                task.setClasspath(mcDep.getFiles());
            });
        }

    }
}
