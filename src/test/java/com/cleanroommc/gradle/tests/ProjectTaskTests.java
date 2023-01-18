package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion.Library;
import com.cleanroommc.gradle.tasks.PrepareDependenciesTask;
import com.cleanroommc.gradle.tasks.download.*;
import com.cleanroommc.gradle.tasks.makerun.MakeRunTask;
import com.cleanroommc.gradle.tasks.makerun.RunType;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
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
        new File(projectDir, "run").mkdirs();
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

        task = project.getTasks().getByPath(PREPARE_DEPENDENCIES_TASK);
        Assertions.assertTrue(task instanceof PrepareDependenciesTask);
        PrepareDependenciesTask downloadDependencies = (PrepareDependenciesTask) task;
        downloadDependencies.task$downloadDependencies();

    }

    @Test
    @Order(3)
    public void testRunCleanClient() {

        MinecraftExtension mcExt = MinecraftExtension.get(Constants.PROJECT);

        Task task = project.getTasks().getByPath(MAKE_RUN_TASK);
        Assertions.assertTrue(task instanceof MakeRunTask);
        MakeRunTask makeRun = (MakeRunTask) task;
        makeRun.getRunType().set(RunType.CleanClient);
        makeRun.task$makeRun();

        task = project.getTasks().getByPath(RUN_CLEAN_CLIENT_TASK);
        Assertions.assertTrue(task instanceof JavaExec);
        JavaExec runCleanClient = (JavaExec) task;
        runCleanClient.workingDir("run");
        runCleanClient.classpath(MAKE_RUNS_FOLDER.apply(mcExt.getVersion()));
        runCleanClient.classpath(((DownloadClientTask) project.getTasks().getByPath(DOWNLOAD_CLIENT_TASK)).getJar());
        File targetFolder = LIBRARIES_FOLDER.apply(mcExt.getVersion());
        for (Library library : mcExt.getVersionInfo().libraries()) {
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
