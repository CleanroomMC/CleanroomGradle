package com.cleanroommc.gradle.api;

import java.util.regex.Pattern;

public final class Meta {

    public static final String CG_FOLDER = "cg";

    // Useful URLs
    public static final String RESOURCES_BASE_URL = "https://resources.download.minecraft.net/";
    public static final String VERSION_MANIFEST_V2_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String MOJANG_PLAYER_API = "https://api.mojang.com/users/profiles/minecraft/";

    // Useful Repositories
    public static final String MOJANG_REPO = "https://libraries.minecraft.net/";
    public static final String CLEANROOM_REPO = "https://maven.cleanroommc.com/";

    public static final String FORGE_REPO = "https://maven.minecraftforge.net/";

    // RegEx's
    public static final Pattern NATIVES_PATTERN = Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");

    private Meta() {}

}
