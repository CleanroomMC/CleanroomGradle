/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

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
