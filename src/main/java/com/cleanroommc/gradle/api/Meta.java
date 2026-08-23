package com.cleanroommc.gradle.api;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Meta {

    public static final String CG_FOLDER = "cleanroom_gradle";

    // Useful URLs
    public static final String RESOURCES_BASE_URL = "https://resources.download.minecraft.net/";
    public static final String VERSION_MANIFEST_V2_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String MOJANG_PLAYER_API = "https://api.mojang.com/users/profiles/minecraft/";

    public static final Map<String, String> DEFAULT_TOOLS = Map.of(
            "accesstransformer", "net.minecraftforge:accesstransformers:8.2.17",
            "decompiler", "com.cleanroommc:cleanflower:1.0.0",
            "installertools", "net.minecraftforge:installertools:1.4.1:fatjar",
            "mergetool", "net.minecraftforge:mergetool:1.2.2"
    );

    // Dependencies Shenanigans
    public static final String ASM_VERSION = "9.10.1";

    /**
     * Module versions forced onto every tool classpath.
     * The published tool artifacts pulls in ASM releases thatare too old.
     */
    public static final String[] FORCED_TOOL_MODULES = new String[] {
            "org.ow2.asm:asm:" + ASM_VERSION,
            "org.ow2.asm:asm-analysis:" + ASM_VERSION,
            "org.ow2.asm:asm-commons:" + ASM_VERSION,
            "org.ow2.asm:asm-tree:" + ASM_VERSION,
            "org.ow2.asm:asm-util:" + ASM_VERSION
    };

    // RegEx's
    public static final Pattern NATIVES_PATTERN = Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");

    private Meta() { }

}
