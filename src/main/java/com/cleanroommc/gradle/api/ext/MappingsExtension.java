package com.cleanroommc.gradle.api.ext;

import org.gradle.api.file.DirectoryProperty;

/**
 * Optional hand-edited Tiny v2 names.
 * Unset by default, which makes the pipeline use MCP CSVs from the {@code mcpMappings} dependency.
 */
public abstract class MappingsExtension {

    public static final String NAMES_FILE = "mappings.tiny";

    /**
     * Directory holding {@value #NAMES_FILE}.
     */
    public abstract DirectoryProperty getNamesDirectory();

}
