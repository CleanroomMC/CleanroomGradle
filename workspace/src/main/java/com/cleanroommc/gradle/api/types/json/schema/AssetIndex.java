package com.cleanroommc.gradle.api.types.json.schema;

/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
