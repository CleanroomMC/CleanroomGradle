package com.cleanroommc.gradle.api.util.inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP {@code access.txt}: one {@code LEVEL owner[name[descriptor]]} entry per line.
 */
public final class AccessMap {

    /** The key a {@link ClassChanges} holds for the class' own visibility, which no member can collide with. */
    private static final String CLASS = "";

    /** The visibility changes that apply to one class and its members. */
    public static final class ClassChanges {

        private final Map<String, AccessLevel> levels = new HashMap<>();

        public AccessLevel forClass() {
            return this.levels.get(CLASS);
        }

        public AccessLevel forField(String name) {
            return this.levels.get(name);
        }

        public AccessLevel forMethod(String name, String descriptor) {
            return this.levels.get(name + " " + descriptor);
        }

    }

    public static AccessMap load(Path file) throws IOException {
        var owners = new HashMap<String, ClassChanges>();
        for (var line : Files.readAllLines(file)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var split = line.indexOf(' ');
            if (split < 0) {
                throw new IOException("Malformed access line, expected '<LEVEL> <target>': " + line);
            }
            var level = AccessLevel.valueOf(line.substring(0, split));
            var target = line.substring(split + 1);
            var member = target.indexOf(' ');
            var owner = member < 0 ? target : target.substring(0, member);
            owners.computeIfAbsent(owner, ignored -> new ClassChanges()).levels.put(member < 0 ? CLASS : target.substring(member + 1), level);
        }
        return new AccessMap(owners);
    }

    private final Map<String, ClassChanges> owners;

    private AccessMap(Map<String, ClassChanges> owners) {
        this.owners = owners;
    }

    public ClassChanges get(String owner) {
        return this.owners.get(owner);
    }

}
