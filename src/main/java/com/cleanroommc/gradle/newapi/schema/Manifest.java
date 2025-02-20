package com.cleanroommc.gradle.newapi.schema;

import java.util.List;
import java.util.Map;

public record Manifest(List<Versions> versions, Map<String, String> latest) {

    public static class Versions {

        public String id, type, url, time, releaseTime, sha1, complianceLevel;

    }

    public Versions version(String id) {
        return versions().stream()
                .filter(versions -> versions.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

}
