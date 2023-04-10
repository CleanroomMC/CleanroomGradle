package com.cleanroommc.gradle.util;

import org.gradle.api.Project;

import java.io.File;
import java.util.function.Function;

public final class DirectoryUtil {
    private DirectoryUtil() {
    }

    public static File create(Project project, Function<Directories, File> dir) {
        Directories directories = new Directories(project);
        return dir.apply(directories);
    }

    public static File create(File gradleHome, Function<Directories, File> dir) {
        Directories directories = new Directories(gradleHome);
        return dir.apply(directories);
    }

    public static class Directories {
        private final File gradleHome;

        private Directories(Project project) {
            this.gradleHome = project.getGradle().getGradleUserHomeDir();
        }

        private Directories(File gradleHomeLocation) {
            this.gradleHome = gradleHomeLocation;
        }

        public File getGradleHome() {
            return gradleHome;
        }

        public File getGradleGlobalCache() {
            return new File(gradleHome, "caches");
        }

        public File getCacheRoot() {
            return new File(getGradleGlobalCache(), "cleanroom_gradle");
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/
         */
        public File getVersionsCacheDirectory() {
            return new File(getCacheRoot(), "versions");
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/name.json ideally the main manifest
         */
        public File getMainVersionManifest(String name) {
            return new File(getVersionsCacheDirectory(), name + ".json");
        }


        /**
         * Location gradle_home/cleanroom_gradle/versions/version
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/versions/1.12.2
         */
        public File getCacheDirectoryForVersion(String version) {
            return new File(getVersionsCacheDirectory(), version);
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/version/version.json
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/versions/1.12.2/1.12.2.json version manifest
         */
        public File getVersionManifest(String version) {
            return new File(getCacheDirectoryForVersion(version), version + ".json");
        }

        /**
         * Location gradle_home/cleanroom_gradle/assets/
         */
        public File getAssetsCacheDirectory() {
            return new File(getCacheRoot(), "assets");
        }

        /**
         * Location gradle_home/cleanroom_gradle/assets/version
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/assets/1.12.2
         */
        public File getCacheDirectoryForAssetVersion(String version) {
            return new File(getAssetsCacheDirectory(), version);
        }

        /**
         * Location gradle_home/cleanroom_gradle/assets/version/version.json
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/assets/1.12.2/1.12.2.json version manifest
         */
        public File getAssetManifest(String version) {
            return new File(getCacheDirectoryForAssetVersion(version), version + ".json");
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/version/libs
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/versions/1.12.2/libs
         */
        public File getLibs(String version) {
            return new File(getCacheDirectoryForVersion(version), "libs");
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/version/natives
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/versions/1.12.2/natives
         */
        public File getNatives(String version) {
            return new File(getCacheDirectoryForVersion(version), "natives");
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/version/extracted_natives
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/versions/1.12.2/extracted_natives
         */
        public File getExtractedNatives(String version) {
            return new File(getCacheDirectoryForVersion(version), "extracted_natives");
        }

        /**
         * Location gradle_home/cleanroom_gradle/versions/version/run
         * <p>
         * Example for 1.12.2 gradle_home/cleanroom_gradle/versions/1.12.2/run
         */
        public File getSide(String version) {
            return new File(getCacheDirectoryForVersion(version), "run");
        }

    }

}
