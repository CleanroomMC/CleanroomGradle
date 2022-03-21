package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.tasks.DownloadTask;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersion;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersionsAdapter;
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

        CleanroomLogger.log2("Setting up download tasks...");
        DownloadTask dlVersionTask = project.getTasks().create("DownloadVersion", DownloadTask.class);
        dlVersionTask.setOutputFile(Utils.supplyToClosure(CleanroomGradlePlugin.class, () -> JSON_VERSION.apply(mcExt.getVersion())));
        dlVersionTask.doFirst(Utils.supplyToClosure(CleanroomGradlePlugin.class, () -> {
            if (ManifestVersion.versions == null) {
                CleanroomLogger.log("Requesting Minecraft's Manifest...");
                // mcManifest = JsonFactory.GSON.fromJson(getWithEtag(URL_MC_MANIFEST, jsonCache, etagFile), new TypeToken<Map<String, ManifestVersion>>() {}.getType());
                ManifestVersion.versions = Utils.GSON.fromJson(Utils.getWithETag(project, MINECRAFT_MANIFEST_LINK, MINECRAFT_MANIFEST_FILE, MINECRAFT_MANIFEST_ETAG),
                        ManifestVersionsAdapter.TYPE);
            }
            return null;
        }));
        DownloadTask dlAssetIndexTask = project.getTasks().create("DownloadAssetIndex", DownloadTask.class);
        dlAssetIndexTask.setOutputFile(Utils.supplyToClosure(CleanroomGradlePlugin.class, () -> JSON_ASSET_INDEX.apply(mcExt.getVersion())));

    }

}
