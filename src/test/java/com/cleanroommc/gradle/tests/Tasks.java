package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.tasks.PrepareDependenciesTask;
import com.cleanroommc.gradle.tasks.download.*;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import static com.cleanroommc.gradle.Constants.*;

public class Tasks {
    private Tasks() {
    }

    public static void runTasks(Consumer<Tasks> tasksConsumer) {
        Tasks tasks = new Tasks();
        tasksConsumer.accept(tasks);
    }

    private static void logRunningTask(String task) {
        CleanroomLogger.log("Running Task >> {}", task);
    }

    public void runClearCacheTask() {
        try {
            logRunningTask(CLEAR_CACHE_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(CLEAR_CACHE_TASK);
            Assertions.assertTrue(task instanceof Delete);
            Method deleteClean = Delete.class.getDeclaredMethod("clean");
            deleteClean.setAccessible(true);
            deleteClean.invoke(task);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDownloadManifest() {
        try {
            logRunningTask(DOWNLOAD_MANIFEST);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_MANIFEST);
            Assertions.assertTrue(task instanceof DownloadManifestTask);
            DownloadManifestTask dlMeta = (DownloadManifestTask) task;
            dlMeta.task$downloadManifest();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDownloadVersion() {
        try {
            logRunningTask(DOWNLOAD_VERSION);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_VERSION);
            Assertions.assertTrue(task instanceof DownloadVersionTask);
            DownloadVersionTask dlVersion = (DownloadVersionTask) task;
            dlVersion.task$downloadVersion();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runGrabAssets() {
        try {
            logRunningTask(GRAB_ASSETS);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(GRAB_ASSETS);
            Assertions.assertTrue(task instanceof GrabAssetsTask);
            GrabAssetsTask grabAssets = (GrabAssetsTask) task;
            grabAssets.task$getOrDownload();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDownloadClientTask() {
        try {
            logRunningTask(DOWNLOAD_CLIENT_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_CLIENT_TASK);
            Assertions.assertTrue(task instanceof DownloadClientTask);
            DownloadClientTask downloadClient = (DownloadClientTask) task;
            downloadClient.task$downloadClient();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runDownloadServerTask() {
        try {
            logRunningTask(DOWNLOAD_SERVER_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_SERVER_TASK);
            Assertions.assertTrue(task instanceof DownloadServerTask);
            DownloadServerTask downloadServer = (DownloadServerTask) task;
            downloadServer.task$downloadServer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runPrepareDependenciesTask() {
        try {
            logRunningTask(PREPARE_DEPENDENCIES_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(PREPARE_DEPENDENCIES_TASK);
            Assertions.assertTrue(task instanceof PrepareDependenciesTask);
            PrepareDependenciesTask downloadDependencies = (PrepareDependenciesTask) task;
            downloadDependencies.task$downloadDependencies();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
