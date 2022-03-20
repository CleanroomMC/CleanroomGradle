package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.tasks.JavaExec;

import static com.cleanroommc.gradle.Constants.*;

public class CleanroomGradlePlugin implements Plugin<Project> {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Project currentProject;

    public static Project getCurrentProject() {
        return currentProject;
    }

    @Override
    public void apply(Project project) {
        if ("8".equals(System.getProperty("java.specification.version"))) {
            throw new UnsupportedOperationException("CleanroomGradle only supports Java 8 at the moment.");
        }

        CleanroomLogger.logTitle("Welcome to CleanroomGradle.");

        CleanroomLogger.log2("Adding java-library and idea plugins...");
        project.apply(ImmutableMap.of("plugin", "java"));
        project.apply(ImmutableMap.of("plugin", "java-library"));
        project.apply(ImmutableMap.of("plugin", "idea"));

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
        CleanroomLogger.log2("      runDir: \"{}\"", mcExt.getRunDir());
        CleanroomLogger.log2("      clientJvmArgs: {}", mcExt.getClientJvmArgs());
        CleanroomLogger.log2("      serverJvmArgs: {}", mcExt.getServerJvmArgs());

        CleanroomLogger.log2("Setting up client run task...");
        JavaExec runClient = project.getTasks().create("runClient", JavaExec.class);
        runClient.getOutputs().dir(mcExt.getRunDir());
        runClient.doFirst(task -> ((JavaExec) task).setWorkingDir(mcExt.getRunDir()));
        runClient.setStandardOutput(System.out);
        runClient.setErrorOutput(System.err);
        runClient.setDescription("Runs Minecraft's Client");
        CleanroomLogger.log2("Setting up server run task...");
        JavaExec runServer = project.getTasks().create("runServer", JavaExec.class);
        runServer.getOutputs().dir(mcExt.getRunDir());
        runServer.doFirst(task -> ((JavaExec) task).setWorkingDir(mcExt.getRunDir()));
        runServer.setStandardInput(System.in);
        runServer.setStandardOutput(System.out);
        runServer.setErrorOutput(System.err);
        runServer.setDescription("Runs Minecraft's Server");
        /*
                    JavaExec exec = makeTask("runClient", JavaExec.class);
            exec.getOutputs().dir(delayedFile(REPLACE_RUN_DIR));
            exec.setMain(GRADLE_START_CLIENT);
            exec.doFirst(task -> ((JavaExec) task).workingDir(delayedFile(REPLACE_RUN_DIR)));
            exec.setStandardOutput(System.out);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft client");

            exec.doFirst(makeRunDir);

            exec.dependsOn("makeStart");
         */

        currentProject = project;
    }

}
