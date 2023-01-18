package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.tasks.PrepareDependenciesTask;
import com.cleanroommc.gradle.tasks.download.*;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static com.cleanroommc.gradle.Constants.*;
import static com.cleanroommc.gradle.Constants.PREPARE_DEPENDENCIES_TASK;

public enum Tasks {
    RUN_CLEAR_CACHE_TASK {
        @Override
        protected void doTask() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            logRunningTask(CLEAR_CACHE_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(CLEAR_CACHE_TASK);
            Assertions.assertTrue(task instanceof Delete);
            var deleteClean = Delete.class.getDeclaredMethod("clean");
            deleteClean.setAccessible(true);
            deleteClean.invoke(task);
        }
    },
    RUN_DOWNLOAD_MANIFEST {
        @Override
        protected void doTask() throws IOException {
            logRunningTask(DOWNLOAD_MANIFEST);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_MANIFEST);
            Assertions.assertTrue(task instanceof DownloadManifestTask);
            DownloadManifestTask dlMeta = (DownloadManifestTask) task;
            dlMeta.task$downloadManifest();
        }
    },
    RUN_DOWNLOAD_VERSION {
        @Override
        protected void doTask() throws IOException {
            logRunningTask(DOWNLOAD_VERSION);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_VERSION);
            Assertions.assertTrue(task instanceof DownloadVersionTask);
            DownloadVersionTask dlVersion = (DownloadVersionTask) task;
            dlVersion.task$downloadVersion();
        }
    },
    RUN_GRAB_ASSETS {
        @Override
        protected void doTask() throws IOException, InterruptedException {
            logRunningTask(GRAB_ASSETS);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(GRAB_ASSETS);
            Assertions.assertTrue(task instanceof GrabAssetsTask);
            GrabAssetsTask grabAssets = (GrabAssetsTask) task;
            grabAssets.task$getOrDownload();
        }
    },
    RUN_DOWNLOAD_CLIENT_TASK {
        @Override
        protected void doTask() throws IOException {
            logRunningTask(DOWNLOAD_CLIENT_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_CLIENT_TASK);
            Assertions.assertTrue(task instanceof DownloadClientTask);
            DownloadClientTask downloadClient = (DownloadClientTask) task;
            downloadClient.task$downloadClient();
        }
    },
    RUN_DOWNLOAD_SERVER_TASK {
        @Override
        protected void doTask() throws IOException {
            logRunningTask(DOWNLOAD_SERVER_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_SERVER_TASK);
            Assertions.assertTrue(task instanceof DownloadServerTask);
            DownloadServerTask downloadServer = (DownloadServerTask) task;
            downloadServer.task$downloadServer();
        }
    },
    RUN_PREPARE_DEPENDENCIES_TASK {
        @Override
        protected void doTask() throws IOException {
            logRunningTask(PREPARE_DEPENDENCIES_TASK);
            Task task = ProjectTestInstance.getProject().getTasks().getByPath(PREPARE_DEPENDENCIES_TASK);
            Assertions.assertTrue(task instanceof PrepareDependenciesTask);
            PrepareDependenciesTask downloadDependencies = (PrepareDependenciesTask) task;
            downloadDependencies.task$downloadDependencies();
        }
    };

    protected abstract void doTask() throws Exception;

    private static void logRunningTask(String task) {
        CleanroomLogger.log("Running Task >> {}", task);
    }

    public static void executeTasks(Tasks... tasks) {
        Arrays.stream(tasks).forEach(task -> {
            try {
                task.doTask();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void executeAllTasks() {
        Arrays.stream(Tasks.values()).forEach(task -> {
            try {
                task.doTask();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
