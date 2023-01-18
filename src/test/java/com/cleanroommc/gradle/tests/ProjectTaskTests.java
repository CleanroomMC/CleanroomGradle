package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.tasks.PrepareDependenciesTask;
import com.cleanroommc.gradle.tasks.download.*;
import org.gradle.api.Task;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.IOException;

import static com.cleanroommc.gradle.Constants.*;

@TestMethodOrder(OrderAnnotation.class)
public class ProjectTaskTests {

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
    }

    @Test
    @Order(1)
    public void testDefaults() {
        // Assert default maven repos
        Assertions.assertTrue(ProjectTestInstance.getProject().getRepositories().stream().anyMatch(ar -> ar.getName().equals("Minecraft")));
        Assertions.assertTrue(ProjectTestInstance.getProject().getRepositories().stream().anyMatch(ar -> ar.getName().equals("CleanroomMC")));
    }

    @Test
    @Order(2)
    public void testTasks() throws IOException, InterruptedException {
        Task task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_MANIFEST);
        Assertions.assertTrue(task instanceof DownloadManifestTask);
        DownloadManifestTask dlMeta = (DownloadManifestTask) task;
        dlMeta.task$downloadManifest();

        task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_VERSION);
        Assertions.assertTrue(task instanceof DownloadVersionTask);
        DownloadVersionTask dlVersion = (DownloadVersionTask) task;
        dlVersion.task$downloadVersion();

        task = ProjectTestInstance.getProject().getTasks().getByPath(GRAB_ASSETS);
        Assertions.assertTrue(task instanceof GrabAssetsTask);
        GrabAssetsTask grabAssets = (GrabAssetsTask) task;
        grabAssets.task$getOrDownload();

        task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_CLIENT_TASK);
        Assertions.assertTrue(task instanceof DownloadClientTask);
        DownloadClientTask downloadClient = (DownloadClientTask) task;
        downloadClient.task$downloadClient();

        task = ProjectTestInstance.getProject().getTasks().getByPath(DOWNLOAD_SERVER_TASK);
        Assertions.assertTrue(task instanceof DownloadServerTask);
        DownloadServerTask downloadServer = (DownloadServerTask) task;
        downloadServer.task$downloadServer();

        task = ProjectTestInstance.getProject().getTasks().getByPath(PREPARE_DEPENDENCIES_TASK);
        Assertions.assertTrue(task instanceof PrepareDependenciesTask);
        PrepareDependenciesTask downloadDependencies = (PrepareDependenciesTask) task;
        downloadDependencies.task$downloadDependencies();

    }

}
