package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extensions.MappingsExtension;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion.Library;
import com.cleanroommc.gradle.tasks.PrepareDependenciesTask;
import com.cleanroommc.gradle.tasks.download.*;
import com.cleanroommc.gradle.tasks.makerun.MakeRunTask;
import com.cleanroommc.gradle.util.Utils;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

import static com.cleanroommc.gradle.Constants.*;

public class CleanroomGradlePlugin implements Plugin<Project> {

    public static Project PROJECT;

    @Override
    public void apply(Project project) {
        // if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            // throw new UnsupportedOperationException("CleanroomGradle only supports Java 8 at the moment.");
        // }

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
        MinecraftExtension mcExt = project.getExtensions().create(MINECRAFT_EXTENSION_KEY, MinecraftExtension.class);
        // MinecraftExtension mcExt = MinecraftExtension.get(project);

        CleanroomLogger.log2("Setting up Mappings DSL Block...");
        project.getExtensions().create(MAPPINGS_EXTENSION_KEY, MappingsExtension.class);

        // Setup a clearCache task
        Utils.createTask(project, CLEAR_CACHE_TASK, Delete.class).delete(CACHE_FOLDER, PROJECT_TEMP_FOLDER);

        final TaskProvider<DownloadManifestTask> downloadManifest = DownloadManifestTask.setupDownloadManifestTask(project);

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

        final TaskProvider<DownloadClientTask> downloadClient = DownloadClientTask.setupDownloadClientTask(project);
        Utils.configureTask(project, downloadClient, task -> task.dependsOn(downloadVersion));

        final TaskProvider<DownloadServerTask> downloadServer = DownloadServerTask.setupDownloadServerTask(project);
        Utils.configureTask(project, downloadServer, task -> task.dependsOn(downloadVersion));

        final TaskProvider<PrepareDependenciesTask> prepareDependencies = PrepareDependenciesTask.setupPrepareDependenciesTask(project);

        final TaskProvider<MakeRunTask> makeRun = MakeRunTask.setupMakeRunTask(project);

        CleanroomLogger.log2("Setting up vanilla client run task...");
        JavaExec runCleanClient = Utils.createTask(project, RUN_CLEAN_CLIENT_TASK, JavaExec.class);
        runCleanClient.dependsOn(downloadClient, prepareDependencies);
        runCleanClient.doFirst(task -> {
            new File(mcExt.getRunDir()).mkdirs();
            JavaExec javaExecTask = (JavaExec) task;
            javaExecTask.workingDir(mcExt.getRunDir());
            javaExecTask.classpath(downloadClient.get().getJar());
            File targetFolder = LIBRARIES_FOLDER.apply(mcExt.getVersion());
            for (Library library : mcExt.getVersionInfo().libraries()) {
                if (library.downloads.artifact != null) {
                    if (library.isApplicable()) {
                        javaExecTask.classpath(new File(targetFolder, library.downloads.artifact.path));
                    }
                }
            }
        });
        runCleanClient.getOutputs().dir(mcExt.getRunDir());
        runCleanClient.setStandardOutput(System.out);
        runCleanClient.setErrorOutput(System.err);
        runCleanClient.setDescription("Runs vanilla Minecraft Client");
        runCleanClient.getMainClass().set("net.minecraft.client.main.Main");
        // runCleanClient.getMainClass().set("CleanClient");

        CleanroomLogger.log2("Setting up vanilla server run task...");
        JavaExec runCleanServer = Utils.createTask(project, RUN_CLEAN_SERVER_TASK, JavaExec.class);
        runCleanServer.dependsOn(downloadServer, prepareDependencies);
        runCleanServer.doFirst(task -> {
            new File(mcExt.getRunDir()).mkdirs();
            JavaExec javaExecTask = (JavaExec) task;
            javaExecTask.workingDir(mcExt.getRunDir());
            javaExecTask.classpath(downloadServer.get().getJar());
            File targetFolder = LIBRARIES_FOLDER.apply(mcExt.getVersion());
            for (Library library : mcExt.getVersionInfo().libraries()) {
                if (library.downloads.artifact != null) {
                    if (library.isApplicable()) {
                        javaExecTask.classpath(new File(targetFolder, library.downloads.artifact.path));
                    }
                }
            }
        });
        runCleanServer.getOutputs().dir(mcExt.getRunDir());
        runCleanServer.setStandardInput(System.in);
        runCleanServer.setStandardOutput(System.out);
        runCleanServer.setErrorOutput(System.err);
        runCleanServer.setDescription("Runs vanilla Minecraft's Server");
        runCleanServer.getMainClass().set("net.minecraft.server.MinecraftServer");



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
