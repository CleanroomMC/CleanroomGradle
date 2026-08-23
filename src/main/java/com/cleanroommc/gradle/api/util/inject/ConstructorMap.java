package com.cleanroommc.gradle.api.util.inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP {@code constructors.txt}: {@code <id> <owner> <descriptor>} per line.
 *
 * <p>The id is what turns up in the {@code p_i<id>_<index>_} parameter names of a constructor.
 */
public final class ConstructorMap {

    public static ConstructorMap load(Path file) throws IOException {
        var map = new ConstructorMap();
        for (var line : Files.readAllLines(file)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var parts = line.split(" ");
            if (parts.length < 3) {
                throw new IOException("Malformed constructor line, expected '<id> <owner> <descriptor>': " + line);
            }
            map.put(parts[1], parts[2], Integer.parseInt(parts[0]));
        }
        return map;
    }

    private final Map<String, Integer> ids = new HashMap<>();

    private int maxId;

    /** {@code -1} when the constructor is not in it. */
    public int get(String owner, String descriptor) {
        var id = this.ids.get(owner + " " + descriptor);
        return id == null ? -1 : id;
    }

    public int generate(String owner, String descriptor) {
        var generated = ++this.maxId;
        this.put(owner, descriptor, generated);
        return generated;
    }

    private void put(String owner, String descriptor, int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Constructor id must be positive: " + id);
        }
        this.maxId = Math.max(this.maxId, id);
        this.ids.put(owner + " " + descriptor, id);
    }

}
