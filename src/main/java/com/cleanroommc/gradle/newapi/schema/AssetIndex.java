package com.cleanroommc.gradle.newapi.schema;

import com.google.gson.annotations.SerializedName;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public record AssetIndex(Map<String, Entry> objects, boolean virtual, @SerializedName("map_to_resources") boolean mapToResources) {

    public AssetIndex() {
        this(new LinkedHashMap<>(), false, false);
    }

    public Collection<AssetEntry> objectCollection() {
        return objects().entrySet().stream().map(AssetEntry::new).toList();
    }

    public record Entry(String hash, long size) { }

    public record AssetEntry(@SerializedName("path") String realPath, String hash, long size) {

        private AssetEntry(Map.Entry<String, Entry> entry) {
            this(entry.getKey(), entry.getValue().hash(), entry.getValue().size());
        }

        public String path() {
            return hash().substring(0, 2) + '/' + hash();
        }

    }
}

