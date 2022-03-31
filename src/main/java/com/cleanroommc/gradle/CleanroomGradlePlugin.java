package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extensions.MappingsExtension;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.tasks.download.DownloadManifestTask;
import com.cleanroommc.gradle.tasks.download.DownloadVersionTask;
import com.cleanroommc.gradle.tasks.download.GrabAssetsTask;
import com.cleanroommc.gradle.util.Utils;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;

import static com.cleanroommc.gradle.Constants.*;

public class CleanroomGradlePlugin implements Plugin<Project> {

    public static Project PROJECT;

    @Override
    public void apply(Project project) {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            throw new UnsupportedOperationException("CleanroomGradle only supports Java 8 at the moment.");
        }

        CleanroomLogger.logTitle("Welcome to CleanroomGradle.");

        PROJECT = project;

        CleanroomLogger.log2("Adding java-library and idea plugins...");
        project.apply(ImmutableMap.of("plugin", "java"));
        project.apply(ImmutableMap.of("plugin", "java-library"));
        project.apply(ImmutableMap.of("plugin", "idea"));

        CleanroomLogger.log2("Adding default configurations...");
        // project.getConfigurations().maybeCreate(CONFIG_MCP_DATA);
        // project.getConfigurations().maybeCreate(CONFIG_MCP_MAPPINGS);
        project.getConfigurations().maybeCreate(CONFIG_NATIVES);
        project.getConfigurations().maybeCreate(CONFIG_FFI_DEPS);
        project.getConfigurations().maybeCreate(CONFIG_MC_DEPS);
        project.getConfigurations().maybeCreate(CONFIG_MC_DEPS_CLIENT);

        CleanroomLogger.log2("Adding mavenCentral, ModCoderPack archive, Minecraft, CleanroomMC's maven repositories...");
        project.getAllprojects().forEach(p -> {
            RepositoryHandler handler = p.getRepositories();
            handler.mavenCentral();
            handler.maven(repo -> {
                repo.setName("Minecraft");
                repo.setUrl(MINECRAFT_MAVEN);
            });
            handler.maven(repo -> {
                repo.setName("CleanroomMC");
                repo.setUrl(CLEANROOM_MAVEN);
            });
        });

        CleanroomLogger.log2("Setting up Minecraft DSL Block...");
        project.getExtensions().create(MINECRAFT_EXTENSION_KEY, MinecraftExtension.class);
        // MinecraftExtension mcExt = MinecraftExtension.get(project);

        CleanroomLogger.log2("Setting up Mappings DSL Block...");
        project.getExtensions().create(MAPPINGS_EXTENSION_KEY, MappingsExtension.class);

        // Setup a clearCache task
        Utils.createTask(project, CLEAR_CACHE_TASK, Delete.class).delete(CACHE_FOLDER, PROJECT_TEMP_FOLDER);

        final TaskProvider<DownloadManifestTask> downloadManifest = DownloadManifestTask.setupDownloadMetaTask(project);

        final TaskProvider<DownloadVersionTask> downloadVersion = DownloadVersionTask.setupDownloadVersionTask(project);
        Utils.configureTask(project, downloadVersion, task -> {
            task.dependsOn(downloadManifest);
            task.getManifestFile().set(downloadManifest.flatMap(DownloadManifestTask::getManifest));
        });

        final TaskProvider<GrabAssetsTask> grabAssets = GrabAssetsTask.setupDownloadAssetsTask(project);
        Utils.configureTask(project, grabAssets, task -> {
            task.dependsOn(downloadVersion);
            task.getMeta().set(downloadVersion.flatMap(DownloadVersionTask::getVersionFile));
        });

        /*
        CleanroomLogger.log2("Setting up client run task...");
        JavaExec runClient = Utils.createTask(project, RUN_MINECRAFT_CLIENT_TASK, JavaExec.class);
        runClient.getOutputs().dir(mcExt.getRunDir());
        runClient.doFirst(task -> ((JavaExec) task).setWorkingDir(mcExt.getRunDir()));
        runClient.setStandardOutput(System.out);
        runClient.setErrorOutput(System.err);
        runClient.setDescription("Runs Minecraft's Client");

        CleanroomLogger.log2("Setting up server run task...");
        JavaExec runServer = Utils.createTask(project, RUN_MINECRAFT_SERVER_TASK, JavaExec.class);
        runServer.getOutputs().dir(mcExt.getRunDir());
        runServer.doFirst(task -> ((JavaExec) task).setWorkingDir(mcExt.getRunDir()));
        runServer.setStandardInput(System.in);
        runServer.setStandardOutput(System.out);
        runServer.setErrorOutput(System.err);
        runServer.setDescription("Runs Minecraft's Server");

        CleanroomLogger.log2("Setting up download tasks...");
        ETaggedDownloadTask.setupDownloadVersionTask(project);
        ETaggedDownloadTask.setupDownloadAssetIndexTask(project);
        PureDownloadTask.setupDownloadClientTask(project);
        PureDownloadTask.setupDownloadServerTask(project);
        GrabAssetsTask.setupDownloadAssetsTask(project);

        CleanroomLogger.log2("Setting up jar manipulation tasks...");
        SplitServerJarTask.setupSplitJarTask(project);
        // MergeJarsTask.setupMergeJarsTask(project);

        CleanroomLogger.log2("Setting up config extraction tasks...");
        ExtractConfigTask.setupExtractConfigTasks(project);
         */

    }

}
