package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.tasks.download.*;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.File;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.*;

@TestMethodOrder(OrderAnnotation.class)
public class ProjectTaskTests {

    static Project project;

    @BeforeAll
    public static void setupProject() {
        File projectDir = new File(".", "test/project/");
        File homeDir = new File(".", "test/gradle_home/");
        project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(homeDir)
                .withName(PROJECT_TEST_NAME)
                .build();
        // Load
        project.getPluginManager().apply("com.cleanroommc.gradle");
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
        MinecraftExtension.get(project).setVersion("1.12.2");
    }

    @Test
    @Order(1)
    public void testDefaults() {
        // Assert default maven repos
        Assertions.assertTrue(project.getRepositories().stream().anyMatch(ar -> ar.getName().equals("Minecraft")));
        Assertions.assertTrue(project.getRepositories().stream().anyMatch(ar -> ar.getName().equals("CleanroomMC")));
    }

    @Test
    @Order(2)
    public void testTasks() throws IOException, InterruptedException {
        Task task = project.getTasks().getByPath(DOWNLOAD_MANIFEST);
        Assertions.assertTrue(task instanceof DownloadManifestTask);
        DownloadManifestTask dlMeta = (DownloadManifestTask) task;
        dlMeta.task$downloadManifest();

        task = project.getTasks().getByPath(DOWNLOAD_VERSION);
        Assertions.assertTrue(task instanceof DownloadVersionTask);
        DownloadVersionTask dlVersion = (DownloadVersionTask) task;
        dlVersion.task$downloadVersion();

        task = project.getTasks().getByPath(GRAB_ASSETS);
        Assertions.assertTrue(task instanceof GrabAssetsTask);
        GrabAssetsTask grabAssets = (GrabAssetsTask) task;
        grabAssets.task$getOrDownload();

        task = project.getTasks().getByPath(DOWNLOAD_CLIENT_TASK);
        Assertions.assertTrue(task instanceof DownloadClientTask);
        DownloadClientTask downloadClient = (DownloadClientTask) task;
        downloadClient.task$downloadClient();

        task = project.getTasks().getByPath(DOWNLOAD_SERVER_TASK);
        Assertions.assertTrue(task instanceof DownloadServerTask);
        DownloadServerTask downloadServer = (DownloadServerTask) task;
        downloadServer.task$downloadServer();
    }

}
