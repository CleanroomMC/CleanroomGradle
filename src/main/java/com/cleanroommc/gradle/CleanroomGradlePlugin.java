package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.tasks.ETaggedDownloadTask;
import com.cleanroommc.gradle.tasks.PureDownloadTask;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.tasks.JavaExec;

import java.io.File;

import static com.cleanroommc.gradle.Constants.*;

public class CleanroomGradlePlugin implements Plugin<Project> {

    public static File GRADLE_USER_HOME_DIR;

    @Override
    public void apply(Project project) {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) {
            throw new UnsupportedOperationException("CleanroomGradle only supports Java 8 at the moment.");
        }

        CleanroomLogger.logTitle("Welcome to CleanroomGradle.");

        GRADLE_USER_HOME_DIR = project.getGradle().getGradleUserHomeDir();

        CleanroomLogger.log2("Adding java-library and idea plugins...");
        project.apply(ImmutableMap.of("plugin", "java"));
        project.apply(ImmutableMap.of("plugin", "java-library"));
        project.apply(ImmutableMap.of("plugin", "idea"));

        CleanroomLogger.log2("Adding default configurations...");
        project.getConfigurations().maybeCreate(CONFIG_MCP_DATA);
        project.getConfigurations().maybeCreate(CONFIG_MAPPINGS);
        project.getConfigurations().maybeCreate(CONFIG_NATIVES);
        project.getConfigurations().maybeCreate(CONFIG_FFI_DEPS);
        project.getConfigurations().maybeCreate(CONFIG_MC_DEPS);
        project.getConfigurations().maybeCreate(CONFIG_MC_DEPS_CLIENT);

        CleanroomLogger.log2("Adding mavenCentral, Minecraft, CleanroomMC's maven repositories...");
        project.getAllprojects().forEach(p -> {
            RepositoryHandler handler = p.getRepositories();
            handler.mavenCentral();
            handler.maven(repo -> {
                repo.setName("minecraft");
                repo.setUrl(MINECRAFT_MAVEN);
            });
            handler.maven(repo -> {
                repo.setName("cleanroom");
                repo.setUrl(CLEANROOM_MAVEN);
            });
        });

        CleanroomLogger.log2("Setting up Minecraft DSL Block...");
        project.getExtensions().create(MINECRAFT_EXTENSION_KEY, MinecraftExtension.class);
        MinecraftExtension mcExt = MinecraftExtension.get(project);

        CleanroomLogger.log2("Setting up client run task...");
        JavaExec runClient = project.getTasks().create(RUN_MINECRAFT_CLIENT_TASK, JavaExec.class);
        runClient.getOutputs().dir(mcExt.getRunDir());
        runClient.doFirst(task -> ((JavaExec) task).setWorkingDir(mcExt.getRunDir()));
        runClient.setStandardOutput(System.out);
        runClient.setErrorOutput(System.err);
        runClient.setDescription("Runs Minecraft's Client");
        CleanroomLogger.log2("Setting up server run task...");
        JavaExec runServer = project.getTasks().create(RUN_MINECRAFT_SERVER_TASK, JavaExec.class);
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

    }

}
