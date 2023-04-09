package com.cleanroommc.gradle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;

import java.io.File;

public final class CleanroomMeta {

    // Useful URLs
    public static final String LIBRARIES_BASE_URL = "https://libraries.minecraft.net/";
    public static final String RESOURCES_BASE_URL = "https://resources.download.minecraft.net/";
    public static final String VERSION_MANIFESTS_V2_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    // Useful Repositories
    public static final String CLEANROOM_REPOSITORY = "https://maven.cleanroommc.com/";
    public static final String FABRIC_REPOSITORY = "https://maven.fabricmc.net/";
    public static final String FORGE_REPOSITORY = "https://maven.minecraftforge.net/";

    // Global GSON Instance
    public static final Gson GSON;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.enableComplexMapKeySerialization().setPrettyPrinting();
        GSON = builder.create();
    }

    /**
     * Locations
     **/

    public static File getGradleHome(Project project) {
        return project.getGradle().getGradleUserHomeDir();
    }

    public static File getGradleGlobalCache(File gradleHomeLocation) {
        return new File(gradleHomeLocation, "caches");
    }

    public static File getGradleGlobalCache(Project project) {
        return new File(getGradleHome(project), "caches");
    }

    public static File getCacheRoot(File gradleHomeLocation) {
        return new File(getGradleGlobalCache(gradleHomeLocation), "cleanroom_gradle");
    }

    public static File getCacheRoot(Project project) {
        return new File(getGradleGlobalCache(project), "cleanroom_gradle");
    }

    public static File getCacheDirectory(File gradleHomeLocation, String... paths) {
        return FileUtils.getFile(getCacheRoot(gradleHomeLocation), paths);
    }

    public static File getCacheDirectory(Project project, String... paths) {
        return FileUtils.getFile(getCacheRoot(project), paths);
    }

    public static File getVersionsCacheDirectory(File gradleHomeLocation, String... paths) {
        return FileUtils.getFile(new File(getCacheDirectory(gradleHomeLocation), "versions"), paths);
    }

    public static File getVersionsCacheDirectory(Project project, String... paths) {
        return FileUtils.getFile(new File(getCacheDirectory(project), "versions"), paths);
    }

    public static File getManifest(File gradleHomeLocation, String name) {
        return getVersionsCacheDirectory(gradleHomeLocation, name + ".json");
    }

    public static File getManifest(Project project, String name) {
        return getVersionsCacheDirectory(project, name + ".json");
    }


    public static File getCacheDirectoryForVersion(File gradleHomeLocation, String version, String... paths) {
        return FileUtils.getFile(getVersionsCacheDirectory(gradleHomeLocation, version), paths);
    }

    public static File getCacheDirectoryForVersion(Project project, String version, String... paths) {
        return FileUtils.getFile(getVersionsCacheDirectory(project, version), paths);
    }

    public static File getVersionManifest(File gradleHomeLocation, String version) {
        return getCacheDirectoryForVersion(gradleHomeLocation, version, version + ".json");
    }

    public static File getVersionManifest(Project project, String version) {
        return getCacheDirectoryForVersion(project, version, version + ".json");
    }

    public static File getAssetsCacheDirectory(File gradleHomeLocation, String... paths) {
        return FileUtils.getFile(new File(getCacheDirectory(gradleHomeLocation), "assets"), paths);
    }

    public static File getAssetsCacheDirectory(Project project, String... paths) {
        return FileUtils.getFile(new File(getCacheDirectory(project), "assets"), paths);
    }

    public static File getCacheDirectoryForAssetVersion(File gradleHomeLocation, String version, String... paths) {
        return FileUtils.getFile(getVersionsCacheDirectory(gradleHomeLocation, version), paths);
    }

    public static File getCacheDirectoryForAssetVersion(Project project, String version, String... paths) {
        return FileUtils.getFile(getVersionsCacheDirectory(project, version), paths);
    }

    public static File getAssetManifest(File gradleHomeLocation, String version) {
        return getCacheDirectoryForAssetVersion(gradleHomeLocation, version, version + ".json");
    }

    public static File getAssetManifest(Project project, String version) {
        return getCacheDirectoryForAssetVersion(project, version, version + ".json");
    }

    // TODO
    public static Directory getRelativeDirectory(Project project, String... paths) {
        Directory directory = project.getLayout().getProjectDirectory();
        for (String path : paths) {
            directory = directory.dir(path);
        }
        return directory;
    }

    private CleanroomMeta() {
    }

}
