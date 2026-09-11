/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.deobf;

import org.gradle.api.attributes.Attribute;

/**
 * Marks how far an artifact has been through the SRG to MCP renaming pipeline.
 */
public final class DeobfAttributes {

    public static final Attribute<String> DEOBFUSCATED = Attribute.of("com.cleanroommc.deobfuscated", String.class);

    public static final String NONE = "none";
    public static final String MCP = "mcp";
    public static final String USERDEV_INPUTS_TYPE = "cleanroom-userdev-deobf-inputs";
    public static final String MCP_SOURCES = "mcp-sources";

    private DeobfAttributes() {}

}
