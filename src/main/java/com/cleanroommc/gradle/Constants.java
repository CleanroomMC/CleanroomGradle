package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extensions.MappingsExtension;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.OS;
import com.google.common.base.Charsets;
import groovy.lang.Closure;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.cleanroommc.gradle.CleanroomGradlePlugin.GRADLE_PROJECT_DIR;
import static com.cleanroommc.gradle.CleanroomGradlePlugin.GRADLE_USER_HOME_DIR;

public class Constants {

    // OS Related
    public static final OS OPERATING_SYSTEM = OS.CURRENT;
    public static final SystemArch SYSTEM_ARCH = getArch();
    public static final Charset CHARSET = Charsets.UTF_8;
    public static final String HASH_FUNC = "MD5";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11";

    // System Defaults
    public static final String USER_DIR = System.getProperty("user.home");

    public static final File MINECRAFT_FOLDER = getMinecraftDirectory();
    public static final File MINECRAFT_ASSET_OBJECTS_FOLDER = new File(MINECRAFT_FOLDER, "assets/objects/");
    public static final File PROJECT_TEMP_FOLDER = new File(GRADLE_PROJECT_DIR, "temp/");

    // Mavens
    public static final String MINECRAFT_MAVEN = "https://libraries.minecraft.net/";
    public static final String CLEANROOM_MAVEN = "https://maven.cleanroommc.com/";

    // URLs
    public static final String MCP_ARCHIVES_REPO = "https://raw.githubusercontent.com/CleanroomMC/MCPMappingsArchive/master/";
    public static final String MINECRAFT_MANIFEST_LINK = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public static final Function<String, String> MINECRAFT_ASSETS_LINK_FORMAT = path -> "http://resources.download.minecraft.net/" + path;
    public static final BiFunction<MappingsExtension, String, String> MCP_ARCHIVES_LINK_MCP_DATA_FORMAT = (ext, suffix) ->
            MCP_ARCHIVES_REPO + "mcp/" + ext.getMCVersion() + "/mcp-" + ext.getMCVersion() + "-" + suffix;
    public static final Function<MappingsExtension, String> MCP_ARCHIVES_LINK_MCP_MAPPINGS_FORMAT = ext -> {
        String prefix = "mcp_" + ext.getChannel(), versionCombi = String.join("-", ext.getVersion(), ext.getMCVersion());
        return MCP_ARCHIVES_REPO + prefix + "/" + versionCombi + "/" + prefix + "-" + versionCombi + ".zip";
    };

    // Resources
    public static final String MANIFEST_RESOURCE = "1.12.2.json";

    // Caches
    public static final File CACHE_FOLDER = new File(GRADLE_USER_HOME_DIR, "caches/");
    public static final File MINECRAFT_CACHE_FOLDER = new File(CACHE_FOLDER, "minecraft/");
    public static final File ASSETS_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "assets/");
    public static final File VERSIONS_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "versionJsons/");
    public static final File MCP_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "de/oceanlabs/mcp/");
    public static final File MINECRAFT_MANIFEST_FILE = new File(MINECRAFT_CACHE_FOLDER, "McManifest.json");
    public static final File MINECRAFT_MANIFEST_ETAG = new File(MINECRAFT_CACHE_FOLDER, "McManifest.json.etag");
    public static final File MCP_MAPPINGS_FILE = new File(MINECRAFT_CACHE_FOLDER, "McpMappings.json");
    public static final File MCP_MAPPINGS_ETAG = new File(MINECRAFT_CACHE_FOLDER, "McpMappings.json.etag");
    public static final File FERNFLOWER_FILE = new File(MINECRAFT_CACHE_FOLDER, "fernflower-fixed.jar");

    public static final Function<String, File> NATIVES_FOLDER = version -> new File(ASSETS_CACHE_FOLDER, "net/minecraft/natives/" + version + "/");
    public static final Function<String, File> MCP_DATA_CACHE_FOLDER = version -> new File(MCP_CACHE_FOLDER, "mcp/" + version + "/");
    public static final BiFunction<String, String, File> MCP_MAPPINGS_CACHE_FOLDER = (mcpChannel, mcpVersion) ->
            new File(MCP_CACHE_FOLDER, "mcp_" + mcpChannel + "/" + mcpVersion + "/");
    public static final Function<MappingsExtension, File> MCP_MAPPINGS_CACHE_FOLDER_FROM_EXT = ext -> MCP_MAPPINGS_CACHE_FOLDER.apply(ext.getChannel(), ext.getVersion());
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
    public static final String CLEANROOM_GRADLE_TASK_GROUP_KEY = "CleanroomGradle";
    public static final String EXTRACT_NATIVES_TASK = "extractNatives";
    public static final String EXTRACT_MCP_DATA_TASK = "extractMCPData";
    public static final String EXTRACT_MCP_MAPPINGS_TASK = "extractMCPMappings";
    public static final String CLEAR_CACHE_TASK = "clearCache";
    public static final String DL_MINECRAFT_VERSIONS_TASK = "downloadVersions";
    public static final String DL_MINECRAFT_ASSET_INDEX_TASK = "downloadAssetIndex";
    public static final String DL_MINECRAFT_ASSETS_TASK = "downloadAssets";
    public static final String DL_MINECRAFT_CLIENT_TASK = "downloadClient";
    public static final String DL_MINECRAFT_SERVER_TASK = "downloadServer";
    public static final String SPLIT_SERVER_JAR_TASK = "splitServerJar";
    public static final String MERGE_JARS_TASK = "mergeJars";
    public static final String RUN_MINECRAFT_CLIENT_TASK = "runClient";
    public static final String RUN_MINECRAFT_SERVER_TASK = "runServer";

    // Extension Keys
    public static final String MINECRAFT_EXTENSION_KEY = "minecraft";
    public static final String MAPPINGS_EXTENSION_KEY = "mappings";

    // Config Keys
    // public static final String CONFIG_MCP_DATA = "cleanroomMcpData";
    // public static final String CONFIG_MCP_MAPPINGS = "cleanroomMcpMappings";
    public static final String CONFIG_NATIVES = "cleanroomNatives";
    public static final String CONFIG_FFI_DEPS = "cleanroomFernFlowerInvokerDeps";
    public static final String CONFIG_MC_DEPS = "cleanroomMinecraftDeps";
    public static final String CONFIG_MC_DEPS_CLIENT = "cleanroomMinecraftClientDeps";

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

    private static File getMinecraftDirectory() {
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
