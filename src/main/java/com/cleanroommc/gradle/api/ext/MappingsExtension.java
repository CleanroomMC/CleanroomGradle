package com.cleanroommc.gradle.api.ext;

import org.gradle.api.file.DirectoryProperty;

/**
 * Optional hand-edited Tiny v2 names.
 * Unset by default, which makes the pipeline use MCP CSVs from the {@code mcpMappings} dependency.
 */
public abstract class MappingsExtension {

    /**
     * Directory holding {@code mappings.tiny}.
     */
    public abstract DirectoryProperty getNamesDirectory();

}
