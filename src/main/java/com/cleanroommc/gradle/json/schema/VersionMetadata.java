package com.cleanroommc.gradle.json.schema;

import com.cleanroommc.gradle.util.Architecture;
import com.cleanroommc.gradle.util.OperatingSystem;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thanks to Fabric Loom
 */
public record VersionMetadata(Object arguments, AssetIndex assetIndex, String assets, int complianceLevel, Map<String, Download> downloads, String id, List<Library> libraries,
                              Object logging, String mainClass, int minimumLauncherVersion, String releaseTime, String time, String type) {

    public record Library(Downloads downloads, String name, Map<String, String> natives, List<Rule> rules, Object extract) {

        public boolean isValidForOS() {
            if (rules == null) {
                // No rules allow everything.
                return true;
            }
            boolean valid = false;
            for (Rule rule : this.rules) {
                if (rule.appliesToOS()) {
                    valid = rule.isAllowed();
                }
            }
            return valid;
        }

        public boolean hasNatives() {
            return this.natives != null;
        }

        public boolean hasNativesForOS() {
            if (!hasNatives()) {
                return false;
            }
            if (classifierForOS() == null) {
                return false;
            }
            return isValidForOS();
        }

        public Download classifierForOS() {
            String classifier = natives.get(OperatingSystem.CURRENT_OS);
            if (Architecture.CURRENT.isArm()) {
                classifier += "-arm64";
            }
            return downloads().classifier(classifier);
        }

        public Download artifact() {
            if (downloads() == null) {
                return null;
            }
            return downloads().artifact();
        }
    }

    public record Downloads(Download artifact, Map<String, Download> classifiers) {

        public Download classifier(String os) {
            return classifiers.get(os);
        }

    }

    public record Rule(String action, OS os) {

        public boolean appliesToOS() {
            return os() == null || os().isValidForOS();
        }

        public boolean isAllowed() {
            return action().equals("allow");
        }

    }

    public record OS(String name) {

        public boolean isValidForOS() {
            return name() == null || name().equalsIgnoreCase(OperatingSystem.CURRENT_OS);
        }

    }

    public record Download(String path, String sha1, long size, String url) {

        public File relativeFile(File baseDirectory) {
            Objects.requireNonNull(path(), "Cannot get relative file from a null path");
            return new File(baseDirectory, path());
        }

    }

    public record AssetIndex(String id, String sha1, long size, long totalSize, String url) {
    }
}
