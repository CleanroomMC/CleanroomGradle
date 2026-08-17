package com.cleanroommc.gradle.api;

import java.util.Map;
import java.util.regex.Pattern;

public final class Meta {

    public static final String CG_FOLDER = "cleanroom_gradle";

    // Useful URLs
    public static final String RESOURCES_BASE_URL = "https://resources.download.minecraft.net/";
    public static final String VERSION_MANIFEST_V2_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String MOJANG_PLAYER_API = "https://api.mojang.com/users/profiles/minecraft/";

    // Useful Repositories
    public static final String MOJANG_REPO = "https://libraries.minecraft.net/";
    public static final String CLEANROOM_REPO = "https://maven.cleanroommc.com/";

    public static final String FORGE_REPO = "https://maven.minecraftforge.net/";

    public static final Map<String, String> DEFAULT_TOOLS = Map.of(
            "accesstransformer", "net.minecraftforge:accesstransformers:8.2.17",
            "decompiler", "com.cleanroommc:cleanflower:1.0.0",
            "installertools", "net.minecraftforge:installertools:1.4.1:fatjar",
            "mcinjector", "de.oceanlabs.mcp:mcinjector:3.7.3",
            "mergetool", "net.minecraftforge:mergetool:1.2.2"
    );

    // RegEx's
    public static final Pattern NATIVES_PATTERN = Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");

    private Meta() { }

}
