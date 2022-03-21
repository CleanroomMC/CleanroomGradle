package com.cleanroommc.gradle.util.json.deserialization.mcversion;

import com.cleanroommc.gradle.util.Utils;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class AssetIndex {

    public static AssetIndex load(File json) throws JsonSyntaxException, JsonIOException, IOException {
        FileReader reader = new FileReader(json);
        AssetIndex a = Utils.GSON.fromJson(reader, AssetIndex.class);
        reader.close();
        return a;
    }

    public boolean virtual = false; // Sane default
    public Map<String, AssetEntry> objects;

    public static class AssetEntry {

        public final String hash;
        public final long size;

        AssetEntry(String hash, long size) {
            this.hash = hash.toLowerCase();
            this.size = size;
        }

    }

}
