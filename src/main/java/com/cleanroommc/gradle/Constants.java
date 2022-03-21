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

    // Caches
    public static final File MINECRAFT_CACHE_FOLDER = new File(GRADLE_USER_HOME_DIR, "caches/minecraft/");
    public static final File ASSETS_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "assets/");
    public static final File VERSIONS_CACHE_FOLDER = new File(MINECRAFT_CACHE_FOLDER, "versionJsons/");
    public static final File MINECRAFT_MANIFEST_FILE = new File(MINECRAFT_CACHE_FOLDER, "McManifest.json");
    public static final File MINECRAFT_MANIFEST_ETAG = new File(MINECRAFT_CACHE_FOLDER, "McManifest.json.etag");
    public static final File MCP_MAPPINGS_FILE = new File(MINECRAFT_CACHE_FOLDER, "McpMappings.json");
    public static final File MCP_MAPPINGS_ETAG = new File(MINECRAFT_CACHE_FOLDER, "McpMappings.json.etag");
    public static final Function<String, File> JSON_ASSET_INDEX = version -> new File(ASSETS_CACHE_FOLDER, "indexes/" + version + ".json");
    public static final Function<String, File> JSON_VERSION = version -> new File(VERSIONS_CACHE_FOLDER, version + ".json");

    // Extension Keys
    public static final String MINECRAFT_EXTENSION_KEY = "minecraft";

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
