package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.GradleException;

import java.util.Locale;

/**
 * A Maven coordinate in {@code group:artifact:version[:classifier][@extension]} form.
 */
public record Coordinate(String group, String artifact, String version, String classifier, String extension) {

    public static Coordinate parse(String value) {
        var extensionSplit = value.split("@", 2);
        var parts = extensionSplit[0].split(":", -1);
        if (parts.length < 3 || parts.length > 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new GradleException("Invalid Maven coordinate: " + value);
        }
        var extension = extensionSplit.length == 2 ? extensionSplit[1] : "jar";
        var classifier = parts.length == 4 && !parts[3].isBlank() ? parts[3] : null;
        return new Coordinate(parts[0], parts[1], parts[2], classifier, extension);
    }

    public String serialized() {
        var value = group + ":" + artifact + ":" + version;
        if (classifier != null) {
            value += ":" + classifier;
        }
        if (!extension.equals("jar")) {
            value += "@" + extension;
        }
        return value;
    }

    public String module() {
        return group + ":" + artifact + ":" + version;
    }

    public Coordinate withoutClassifier() {
        return new Coordinate(group, artifact, version, null, extension);
    }

    public boolean sameArtifact(Coordinate other) {
        return group.equals(other.group) && artifact.equals(other.artifact)
                && version.equals(other.version) && classifierEquals(classifier, other.classifier)
                && extension.equals(other.extension);
    }

    public String mavenPath() {
        return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName();
    }

    public String fileName() {
        var fileName = artifact + "-" + version;
        if (classifier != null) {
            fileName += "-" + classifier;
        }
        return fileName + "." + extension.toLowerCase(Locale.ROOT);
    }

    private static boolean classifierEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

}
