package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion;
import com.cleanroommc.gradle.tasks.PrepareDependenciesTask;
import com.cleanroommc.gradle.tasks.download.*;
import com.cleanroommc.gradle.tasks.makerun.MakeRunTask;
import com.cleanroommc.gradle.tasks.makerun.RunType;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.cleanroommc.gradle.Constants.*;

public class TestRuns {

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CleanClientTest {
        @BeforeEach
        void beforeEach(TestInfo testInfo) {
            testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
        }

        @Test
        @Order(1)
        void executeTasks() {
            TestRuns.executeTasks(
                    Tasks.RUN_DOWNLOAD_MANIFEST,
                    Tasks.RUN_DOWNLOAD_VERSION,
                    Tasks.RUN_GRAB_ASSETS,
                    Tasks.RUN_DOWNLOAD_CLIENT_TASK,
                    Tasks.RUN_PREPARE_DEPENDENCIES_TASK);
        }

        @Test
        void testRunCleanClient() {
            initRun(RUN_CLEAN_CLIENT_TASK, DOWNLOAD_CLIENT_TASK);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CleanServerTest {
        @BeforeEach
        void beforeEach(TestInfo testInfo) {
            testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
        }

        @Test
        @Order(1)
        void executeTasks() {
            TestRuns.executeTasks(
                    Tasks.RUN_DOWNLOAD_MANIFEST,
                    Tasks.RUN_DOWNLOAD_VERSION,
                    Tasks.RUN_DOWNLOAD_SERVER_TASK,
                    Tasks.RUN_PREPARE_DEPENDENCIES_TASK);
        }

        @Test
        @Order(2)
        void testRunCleanServer() {
            CleanroomLogger.log("TEST PHASE >> {}", "testRunCleanServer");
            initRun(RUN_CLEAN_SERVER_TASK, DOWNLOAD_SERVER_TASK);
        }
    }

    private static void executeTasks(Tasks... tasks) {
        Arrays.stream(tasks).forEach(task -> {
            try {
                task.doTask();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    enum Tasks {
        RUN_DOWNLOAD_MANIFEST {
            @Override
            void doTask() throws IOException {
                logRunningTask(DOWNLOAD_MANIFEST);
                Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_MANIFEST);
                Assertions.assertTrue(task instanceof DownloadManifestTask);
                DownloadManifestTask dlMeta = (DownloadManifestTask) task;
                dlMeta.task$downloadManifest();
            }
        },
        RUN_DOWNLOAD_VERSION {
            @Override
            void doTask() throws IOException {
                logRunningTask(DOWNLOAD_VERSION);
                Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_VERSION);
                Assertions.assertTrue(task instanceof DownloadVersionTask);
                DownloadVersionTask dlVersion = (DownloadVersionTask) task;
                dlVersion.task$downloadVersion();
            }
        },
        RUN_GRAB_ASSETS {
            @Override
            void doTask() throws IOException, InterruptedException {
                logRunningTask(GRAB_ASSETS);
                Task task = ProjectTestInstance.getProject().getTasks().getByPath(GRAB_ASSETS);
                Assertions.assertTrue(task instanceof GrabAssetsTask);
                GrabAssetsTask grabAssets = (GrabAssetsTask) task;
                grabAssets.task$getOrDownload();
            }
        },
        RUN_DOWNLOAD_CLIENT_TASK {
            @Override
            void doTask() throws IOException {
                logRunningTask(DOWNLOAD_CLIENT_TASK);
                Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_CLIENT_TASK);
                Assertions.assertTrue(task instanceof DownloadClientTask);
                DownloadClientTask downloadClient = (DownloadClientTask) task;
                downloadClient.task$downloadClient();
            }
        },
        RUN_DOWNLOAD_SERVER_TASK {
            @Override
            void doTask() throws IOException {
                logRunningTask(DOWNLOAD_SERVER_TASK);
                Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_SERVER_TASK);
                Assertions.assertTrue(task instanceof DownloadServerTask);
                DownloadServerTask downloadServer = (DownloadServerTask) task;
                downloadServer.task$downloadServer();
            }
        },
        RUN_PREPARE_DEPENDENCIES_TASK {
            @Override
            void doTask() throws IOException {
                logRunningTask(PREPARE_DEPENDENCIES_TASK);
                Task task = ProjectTestInstance.getProject().getTasks().getByPath(PREPARE_DEPENDENCIES_TASK);
                Assertions.assertTrue(task instanceof PrepareDependenciesTask);
                PrepareDependenciesTask downloadDependencies = (PrepareDependenciesTask) task;
                downloadDependencies.task$downloadDependencies();
            }
        };

        protected void logRunningTask(String task) {
            CleanroomLogger.log("Running Task >> {}", task);
        }

        abstract void doTask() throws IOException, InterruptedException;
    }

    private static void initRun(String launchTaskName, String downloadTaskName) {
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
