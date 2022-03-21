import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.tasks.download.GrabAssetsTask;
import com.cleanroommc.gradle.tasks.download.ETaggedDownloadTask;
import com.cleanroommc.gradle.tasks.download.PureDownloadTask;
import com.cleanroommc.gradle.tasks.jarmanipulation.SplitServerJarTask;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersion;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersionsAdapter;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.Version;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.File;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.*;

/**
 * Unfortunately there is no real clear-cut way of testing out tasks
 */
@TestMethodOrder(OrderAnnotation.class)
@SuppressWarnings("ResultOfMethodCallIgnored")
public class ProjectTest {

    static Project project;
    static File json1122, json118;

    @BeforeAll
    public static void setupProject() {
        File projectDir = new File(".", "test/project/");
        File homeDir = new File(".", "test/gradle_home/");
        project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(homeDir)
                .build();
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
    }

    @Test
    @Order(1)
    public void loadPluginAndTestDefaults() {
        // Load
        project.getPluginManager().apply("com.cleanroommc.gradle");

        // Assert default maven repos
        Assertions.assertEquals(1, project.getRepositories().stream().filter(ar -> ar.getName().equals("minecraft")).count());
        Assertions.assertEquals(1, project.getRepositories().stream().filter(ar -> ar.getName().equals("cleanroom")).count());

        // Check default runDir
        Assertions.assertEquals("run", MinecraftExtension.get(project).getRunDir());

        Assertions.assertNotNull(project.getTasks().findByPath(RUN_MINECRAFT_CLIENT_TASK));
        Assertions.assertNotNull(project.getTasks().findByPath(RUN_MINECRAFT_SERVER_TASK));
    }

    @Test
    @Order(2)
    public void testDownloadManifest() {
        // Re-create DownloadVersion task routine since we can't run tasks in test env
        // Clear
        MINECRAFT_MANIFEST_FILE.delete();
        MINECRAFT_MANIFEST_ETAG.delete();
        // doFirst
        ManifestVersion.versions = Utils.GSON.fromJson(Utils.getWithETag(project, MINECRAFT_MANIFEST_LINK, MINECRAFT_MANIFEST_FILE, MINECRAFT_MANIFEST_ETAG),
                ManifestVersionsAdapter.TYPE);
        // Did the files get downloaded?
        Assertions.assertTrue(MINECRAFT_MANIFEST_FILE.exists());
        Assertions.assertTrue(MINECRAFT_MANIFEST_ETAG.exists());

        // Did the manifest contain 1.12.2, or 1.18?
        Assertions.assertTrue(ManifestVersion.versions.containsKey("1.12.2"));
        Assertions.assertTrue(ManifestVersion.versions.containsKey("1.18"));
    }

    @Test
    @Order(3)
    public void testDownloadVersion() throws IOException {
        // DownloadVersion - 1.12.2
        Task task = project.getTasks().getByPath(DL_MINECRAFT_VERSIONS_TASK);
        Assertions.assertTrue(task instanceof ETaggedDownloadTask);
        ETaggedDownloadTask dlTask = (ETaggedDownloadTask) task;
        dlTask.downloadAndGet();

        json1122 = dlTask.getOutputFile();

        Assertions.assertNotNull(json1122);

        // DownloadVersion - 1.18
        MinecraftExtension.get(project).setVersion("1.18");
        dlTask.downloadAndGet();

        json118 = dlTask.getOutputFile();

        Assertions.assertNotNull(json118);
    }

    @Test
    @Order(4)
    public void testDownloadAssetIndex() throws IOException {
        // DownloadAssetIndex - 1.12.2
        MinecraftExtension.get(project).setVersion("1.12.2");
        Version.parseVersionAndStoreDeps(project, json1122, true, json1122.getParentFile()); // Prime cachedVersion, not needed outside of tests
        Assertions.assertNotNull(Version.getCurrentVersion());
        Task task = project.getTasks().getByPath(DL_MINECRAFT_ASSET_INDEX_TASK);
        Assertions.assertTrue(task instanceof ETaggedDownloadTask);
        ETaggedDownloadTask dlTask = (ETaggedDownloadTask) task;
        dlTask.downloadAndGet();

        // DownloadAssetIndex - 1.18
        MinecraftExtension.get(project).setVersion("1.18");
        Version.parseVersionAndStoreDeps(project, json118, true, json118.getParentFile()); // Update cachedVersion to 1.18
        Assertions.assertNotNull(Version.getCurrentVersion());
        dlTask.downloadAndGet();
    }

    @Test
    @Order(5)
    public void testDownloadClientAndServer() throws IOException {
        MinecraftExtension.get(project).setVersion("1.12.2");
        Version.parseVersionAndStoreDeps(project, json1122, true, json1122.getParentFile());

        // DownloadClient - 1.12.2
        Task task = project.getTasks().getByPath(DL_MINECRAFT_CLIENT_TASK);
        Assertions.assertTrue(task instanceof PureDownloadTask);
        PureDownloadTask dlTask = (PureDownloadTask) task;
        dlTask.downloadAndGet();

        // DownloadServer - 1.12.2
        task = project.getTasks().getByPath(DL_MINECRAFT_SERVER_TASK);
        Assertions.assertTrue(task instanceof PureDownloadTask);
        dlTask = (PureDownloadTask) task;
        dlTask.downloadAndGet();

        MinecraftExtension.get(project).setVersion("1.18");
        Version.parseVersionAndStoreDeps(project, json118, true, json118.getParentFile());

        // DownloadServer - 1.18
        task = project.getTasks().getByPath(DL_MINECRAFT_CLIENT_TASK);
        dlTask = (PureDownloadTask) task;
        dlTask.downloadAndGet();

        // DownloadServer - 1.18
        task = project.getTasks().getByPath(DL_MINECRAFT_SERVER_TASK);
        dlTask = (PureDownloadTask) task;
        dlTask.downloadAndGet();
    }

    @Test
    @Order(6)
    public void testDownloadAssets() throws IOException {
        MinecraftExtension.get(project).setVersion("1.12.2");

        Task task = project.getTasks().getByPath(DL_MINECRAFT_ASSETS_TASK);
        Assertions.assertTrue(task instanceof GrabAssetsTask);
        GrabAssetsTask grabTask = (GrabAssetsTask) task;
        grabTask.downloadAndGet();

        MinecraftExtension.get(project).setVersion("1.18");

        grabTask.downloadAndGet();

    }

    @Test
    @Order(7)
    public void testSplittingServerJar() throws IOException {
        MinecraftExtension.get(project).setVersion("1.12.2");
        Task task = project.getTasks().getByPath(SPLIT_SERVER_JAR_TASK);
        Assertions.assertTrue(task instanceof SplitServerJarTask);
        SplitServerJarTask splitTask = (SplitServerJarTask) task;
        splitTask.run();
    }

}
