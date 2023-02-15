package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion;
import com.cleanroommc.gradle.tasks.download.DownloadClientTask;
import com.cleanroommc.gradle.tasks.download.DownloadServerTask;
import com.cleanroommc.gradle.tasks.makerun.MakeRunTask;
import com.cleanroommc.gradle.tasks.makerun.RunType;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.util.function.Consumer;

import static com.cleanroommc.gradle.Constants.*;

public class Runs {
    private Runs() {
    }

    public static void executeRun(Consumer<Runs> runsConsumer) {
        Runs runs = new Runs();
        runsConsumer.accept(runs);
    }

    public void runCleanClient() {
        initRun(RUN_CLEAN_CLIENT_TASK, DOWNLOAD_CLIENT_TASK);
    }

    public void runCleanServer() {
        initRun(RUN_CLEAN_SERVER_TASK, DOWNLOAD_SERVER_TASK);
    }

    private void initRun(String launchTaskName, String downloadTaskName) {
        MinecraftExtension mcExt = MinecraftExtension.get(Constants.PROJECT);

        Task task = ProjectTestInstance.getProject().getTasks().getByPath(MAKE_RUN_TASK);
        Assertions.assertTrue(task instanceof MakeRunTask);
        MakeRunTask makeRun = (MakeRunTask) task;
        makeRun.getRunType().set(RunType.CleanServer);
        makeRun.task$makeRun();

        task = ProjectTestInstance.getProject().getTasks().getByPath(launchTaskName);
        Assertions.assertTrue(task instanceof JavaExec);
        JavaExec runCleanClient = (JavaExec) task;
        runCleanClient.workingDir("run");
        runCleanClient.classpath(MAKE_RUNS_FOLDER.apply(mcExt.getVersion()));

        switch (downloadTaskName) {
            case DOWNLOAD_SERVER_TASK: {
                if (ProjectTestInstance.getProject().getTasks().getByPath(downloadTaskName) instanceof DownloadServerTask downloadServerTask) {
                    runCleanClient.classpath(downloadServerTask.getJar());
                    break;
                }
                Assertions.fail("DOWNLOAD_SERVER_TASK is somehow not a instance of DownloadServerTask");
            }

            case DOWNLOAD_CLIENT_TASK: {
                if (ProjectTestInstance.getProject().getTasks().getByPath(downloadTaskName) instanceof DownloadClientTask downloadClientTask) {
                    runCleanClient.classpath(downloadClientTask.getJar());
                    break;
                }
                Assertions.fail("DOWNLOAD_CLIENT_TASK is somehow not a instance of DownloadClientTask");
            }

            default:
                Assertions.fail("Unsupported task, only DOWNLOAD_SERVER_TASK and DOWNLOAD_CLIENT_TASK are supported");
        }

        File targetFolder = LIBRARIES_FOLDER.apply(mcExt.getVersion());
        for (MinecraftVersion.Library library : mcExt.getVersionInfo().libraries()) {
            if (library.downloads.artifact != null) {
                CleanroomLogger.log(library.name);
                runCleanClient.classpath(new File(targetFolder, library.downloads.artifact.path));
            }
        }
        runCleanClient.systemProperty("java.library.path", EXTRACTED_NATIVES_FOLDER.apply(mcExt.getVersion()));
        runCleanClient.args(
                "--accessToken", "CleanroomGradle",
                "--version", mcExt.getVersion(),
                "--assetIndex", mcExt.getVersionInfo().assetIndex.id,
                "--assetsDir", ASSETS_CACHE_FOLDER.toString());
        runCleanClient.exec();
    }
}
