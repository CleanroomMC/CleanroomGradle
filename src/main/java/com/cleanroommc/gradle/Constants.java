package com.cleanroommc.gradle;

import com.cleanroommc.gradle.util.json.deserialization.mcversion.OS;
import com.google.common.base.Charsets;
import groovy.lang.Closure;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.function.Function;

import static com.cleanroommc.gradle.CleanroomGradlePlugin.GRADLE_USER_HOME_DIR;

public class Constants {

    // System Defaults
    public static final String USER_DIR = System.getProperty("user.home");

    // Mavens
    public static final String MINECRAFT_MAVEN = "https://libraries.minecraft.net/";
    public static final String CLEANROOM_MAVEN = "https://maven.cleanroommc.com/";

    // URLs
    public static final String MINECRAFT_MANIFEST_LINK = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    // Resources
    public static final String MANIFEST_RESOURCE = "1.12.2.json";

    /*
        public static final String DIR_LOCAL_CACHE  = REPLACE_PROJECT_CACHE_DIR + "/minecraft";
    public static final String DIR_MCP_DATA     = REPLACE_CACHE_DIR + "/de/oceanlabs/mcp/mcp/" + REPLACE_MC_VERSION;
    public static final String DIR_MCP_MAPPINGS = REPLACE_CACHE_DIR + "/de/oceanlabs/mcp/mcp_" + REPLACE_MCP_CHANNEL + "/" + REPLACE_MCP_VERSION;
    public static final String JAR_CLIENT_FRESH = REPLACE_CACHE_DIR + "/net/minecraft/minecraft/" + REPLACE_MC_VERSION + "/minecraft-" + REPLACE_MC_VERSION + ".jar";
    public static final String JAR_SERVER_FRESH = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_server/" + REPLACE_MC_VERSION + "/minecraft_server-" + REPLACE_MC_VERSION + ".jar";
    public static final String JAR_MERGED       = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_merged/" + REPLACE_MC_VERSION + "/minecraft_merged-" + REPLACE_MC_VERSION + ".jar";
    public static final String JAR_SERVER_PURE  = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_server/" + REPLACE_MC_VERSION + "/minecraft_server-" + REPLACE_MC_VERSION + "-pure.jar";
    public static final String JAR_SERVER_DEPS  = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_server/" + REPLACE_MC_VERSION + "/minecraft_server-" + REPLACE_MC_VERSION + "-deps.jar";
    public static final String DIR_NATIVES      = REPLACE_CACHE_DIR + "/net/minecraft/natives/" + REPLACE_MC_VERSION + "/";
    public static final String JAR_FERNFLOWER   = REPLACE_CACHE_DIR + "/fernflower-fixed.jar";
     */

    // Caches
    public static final File CACHE_FOLDER = new File(GRADLE_USER_HOME_DIR, "caches/");
    public static final File MINECRAFT_CACHE_FOLDER = new File(CACHE_FOLDER, "minecraft/");
    public static final File ASSETS_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "assets/");
    public static final File VERSIONS_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "versionJsons/");
    public static final File MINECRAFT_MANIFEST_FILE = new File(MINECRAFT_CACHE_FOLDER, "McManifest.json");
    public static final File MINECRAFT_MANIFEST_ETAG = new File(MINECRAFT_CACHE_FOLDER, "McManifest.json.etag");
    public static final File MCP_MAPPINGS_FILE = new File(MINECRAFT_CACHE_FOLDER, "McpMappings.json");
    public static final File MCP_MAPPINGS_ETAG = new File(MINECRAFT_CACHE_FOLDER, "McpMappings.json.etag");
    public static final File FERNFLOWER_FILE = new File(MINECRAFT_CACHE_FOLDER, "fernflower-fixed.jar");

    public static final Function<String, File> NATIVES_FOLDER = version -> new File(ASSETS_CACHE_FOLDER, "net/minecraft/natives/" + version + "/");
    public static final Function<String, File> VERSION_FILE = version -> new File(VERSIONS_CACHE_FOLDER, version + ".json");
    public static final Function<String, File> ASSET_INDEX_FILE = version -> new File(ASSETS_CACHE_FOLDER, "indexes/" + version + ".json");
    public static final Function<String, File> MINECRAFT_CLIENT_FILE = version ->
            new File(MINECRAFT_CACHE_FOLDER, "net/minecraft/minecraft/" + version + "/minecraft-" + version + ".jar");
    public static final Function<String, File> MINECRAFT_SERVER_FILE = version ->
            new File(MINECRAFT_CACHE_FOLDER, "net/minecraft/minecraft_server/" + version + "/minecraft_server-" + version + ".jar");
    public static final Function<String, File> MINECRAFT_SERVER_PURE_FILE = version ->
            new File(MINECRAFT_CACHE_FOLDER, "net/minecraft/minecraft_server/" + version + "/minecraft_server-" + version + "-pure.jar");
    public static final Function<String, File> MINECRAFT_SERVER_FILE_WITH_DEPS = version ->
            new File(MINECRAFT_CACHE_FOLDER, "net/minecraft/minecraft_server/" + version + "/minecraft_server-" + version + "-deps.jar");
    public static final Function<String, File> MINECRAFT_MERGED_FILE = version ->
            new File(MINECRAFT_CACHE_FOLDER, "net/minecraft/minecraft_merged/" + version + "/minecraft_merged-" + version + ".jar");

    // Task Keys
    public static final String DL_MINECRAFT_CLIENT = "downloadClient";
    public static final String DL_MINECRAFT_SERVER = "downloadServer";

    // Extension Keys
    public static final String MINECRAFT_EXTENSION_KEY = "minecraft";

    // Config Keys
    public static final String CONFIG_MCP_DATA       = "cleanroomMcpData";
    public static final String CONFIG_MAPPINGS       = "cleanroomMcpMappings";
    public static final String CONFIG_NATIVES        = "cleanroomMinecraftNatives";
    public static final String CONFIG_FFI_DEPS        = "cleanroomFernFlowerInvokerDeps";
    public static final String CONFIG_MC_DEPS        = "cleanroomMinecraftDeps";
    public static final String CONFIG_MC_DEPS_CLIENT = "cleanroomMinecraftClientDeps";

    // OS Related
    public static final OS OPERATING_SYSTEM = OS.CURRENT;
    public static final SystemArch SYSTEM_ARCH = getArch();
    public static final Charset CHARSET = Charsets.UTF_8;
    public static final String HASH_FUNC = "MD5";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11";

    // Groovy
    public static final Closure<Boolean> TRUE_CLOSURE = new Closure<Boolean>(Constants.class) {
        @Override
        public Boolean call(Object... args) {
            return Boolean.TRUE;
        }
    };
    public static final Closure<Boolean> FALSE_CLOSURE = new Closure<Boolean>(Constants.class) {
        @Override
        public Boolean call(Object... args) {
            return Boolean.FALSE;
        }
    };

    public static File getMinecraftDirectory() {
        switch (OPERATING_SYSTEM) {
            case LINUX:
                return new File(USER_DIR, ".minecraft");
            case WINDOWS:
                String appData = System.getenv("APPDATA");
                String folder = appData != null ? appData : USER_DIR;
                return new File(folder, ".minecraft");
            case OSX:
                return new File(USER_DIR, "Library/Application Support/minecraft");
            default:
                return new File(USER_DIR, "minecraft");
        }
    }

    private static SystemArch getArch() {
        String name = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        if (name.contains("64")) {
            return SystemArch.BIT_64;
        } else {
            return SystemArch.BIT_32;
        }
    }

    public enum SystemArch {

        BIT_32, BIT_64;

        public String toString() {
            return name().replace("bit_", "").toLowerCase(Locale.ENGLISH);
        }

    }

}
