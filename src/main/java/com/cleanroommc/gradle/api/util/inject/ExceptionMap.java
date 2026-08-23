package com.cleanroommc.gradle.api.util.inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP {@code exceptions.txt}: {@code <owner>/<name> <descriptor> <exception>...} per line.
 */
public final class ExceptionMap {

    public static ExceptionMap load(Path file) throws IOException {
        var owners = new HashMap<String, Map<String, String[]>>();
        for (var line : Files.readAllLines(file)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var first = line.indexOf(' ');
            var second = first < 0 ? -1 : line.indexOf(' ', first + 1);
            var slash = first < 0 ? -1 : line.lastIndexOf('/', first);
            if (second < 0 || slash < 0) {
                throw new IOException("Malformed exceptions line, expected '<owner>/<name> <descriptor> <exception>...': " + line);
            }
            owners.computeIfAbsent(line.substring(0, slash), ignored -> new HashMap<>())
                    .put(line.substring(slash + 1, second), line.substring(second + 1).split(" "));
        }
        return new ExceptionMap(owners);
    }

    private final Map<String, Map<String, String[]>> owners;

    private ExceptionMap(Map<String, Map<String, String[]>> owners) {
        this.owners = owners;
    }

    public Map<String, String[]> get(String owner) {
        return this.owners.get(owner);
    }

}
