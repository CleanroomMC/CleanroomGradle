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

    private DeobfAttributes() { }

}
