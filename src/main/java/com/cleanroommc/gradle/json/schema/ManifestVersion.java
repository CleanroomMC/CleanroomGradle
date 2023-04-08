package com.cleanroommc.gradle.json.schema;

import java.util.List;
import java.util.Map;

/**
 * Thanks to Fabric Loom
 */
public record ManifestVersion(List<Versions> versions, Map<String, String> latest) {

    public record Versions(String id, String url, String sha1) {

    }

}
