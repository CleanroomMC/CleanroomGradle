package com.cleanroommc.gradle.json.schema;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Thanks to Fabric Loom
 */
public record AssetIndexObjects(Map<String, Entry> objects, boolean virtual,
                                @SerializedName("map_to_resources") boolean mapToResources) {

    public AssetIndexObjects() {
        this(new LinkedHashMap<>(), false, false);
    }

    public Stream<Object> getObjectStream() {
        return objects.entrySet().stream().map(Object::new);
    }

    public List<Object> getObjectList() {
        return getObjectStream().toList();
    }

    public record Entry(String hash, long size) {
    }

    public record Object(String path, String hash, long size) {

        private Object(Map.Entry<String, Entry> entry) {
            this(entry.getKey(), entry.getValue().hash(), entry.getValue().size());
        }

        public String getPath() {
            return hash.substring(0, 2) + '/' + hash;
        }

        public String name() {
            int end = path().lastIndexOf("/") + 1;
            if (end > 0) {
                return path().substring(end);
            }
            return path();
        }
    }

}
