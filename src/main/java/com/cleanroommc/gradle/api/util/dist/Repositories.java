package com.cleanroommc.gradle.api.util.dist;

import com.cleanroommc.gradle.api.Meta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maven repositories the published installer/MMC metadata is allowed to point at.
 */
public final class Repositories {

    public static final String CLEANROOM_REPO = "https://maven.cleanroommc.com/";
    public static final String FORGE_REPO = "https://maven.minecraftforge.net/";
    public static final String MOJANG_REPO = "https://libraries.minecraft.net/";

    // TODO
    public static Map<String, String> distribution() {
        var repositories = new LinkedHashMap<String, String>();
        repositories.put("*", "https://repo.maven.apache.org/maven2/");
        repositories.put("com.cleanroommc", CLEANROOM_REPO);
        repositories.put("top.outlands", CLEANROOM_REPO);
        repositories.put("net.minecraftforge", FORGE_REPO);
        repositories.put("de.oceanlabs.mcp", FORGE_REPO);
        repositories.put("com.mojang", MOJANG_REPO);
        // Legacy Minecraft libraries Mojang hosts
        repositories.put("java3d", MOJANG_REPO);
        repositories.put("lzma", MOJANG_REPO);
        return repositories;
    }

    private Repositories() { }

}
