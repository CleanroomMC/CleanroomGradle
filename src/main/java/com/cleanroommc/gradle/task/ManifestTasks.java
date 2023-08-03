package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomLogging;
import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.extension.ManifestExtension;
import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public final class ManifestTasks {
    public static final String MANIFEST_GROUP = "manifest";
    public static final String GATHER_MANIFEST = "gatherManifest";
    private TaskProvider<Download> gatherManifestTask;
    public static final String PREPARE_NEEDED_MANIFESTS = "prepareNeededManifests";
    private TaskProvider<ManifestParser> manifestParserTask;
    public static final String VERSION_DOWNLOADER_TASK = "versionDownloaderTask";
    private TaskProvider<MinecraftDownloader> versionDownloaderTask;
    private final Project project;

    public static void create(Logger logger, Project project) {
        CleanroomLogging.step(logger, "Registering manifest tasks...");
        final var tasks = new ManifestTasks(project);
        tasks.registerMainManifestTasksForProject();
    }

    private ManifestTasks(Project project) {
        this.project = project;
    }

    private void registerMainManifestTasksForProject() {
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
        manifestParserTask = taskContainer.register(PREPARE_NEEDED_MANIFESTS, ManifestParser.class, task -> {
            task.setGroup(MANIFEST_GROUP);
            task.dependsOn(gatherManifestTask);
            task.getInputFile().fileProvider(gatherManifestTask.map(Download::getDest));
        });
        versionDownloaderTask = taskContainer.register(VERSION_DOWNLOADER_TASK, MinecraftDownloader.class, task -> {
            task.setGroup(MANIFEST_GROUP);
            task.dependsOn(manifestParserTask);
            task.getManifests().set(manifestParserTask.map(ManifestParser::getVersionMetadata).get());
        });
    }

}
