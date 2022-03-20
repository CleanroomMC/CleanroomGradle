package com.cleanroommc.gradle.util.json.deserialization.mcversion;

import java.util.Map;

public class AssetIndex {

    public boolean virtual = false; // Sane default
    public Map<String, AssetEntry> entries;

    public static class AssetEntry {

        public final String hash;
        public final long size;

        AssetEntry(String hash, long size) {
            this.hash = hash.toLowerCase();
            this.size = size;
        }

    }

}
