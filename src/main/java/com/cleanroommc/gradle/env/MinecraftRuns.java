package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.Objects;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Shared wiring for {@link RunMinecraft} tasks so they do not look up the project extension.
 */
public final class MinecraftRuns {

    public static void caches(RunMinecraft task, CachesExtension caches, Provider<VersionMeta> versionMeta, boolean offline) {
        var assets = caches.getDirectory().dir("assets");
        var uuidCache = caches.getDirectory().file("uuid_cache.properties");
        task.getVanillaAssetsLocation().set(assets);
        task.getUuidCache().set(uuidCache);
        task.getOffline().set(offline);
        task.getVersionMeta().convention(versionMeta);
        task.getAssetIndexVersion().convention(versionMeta.map(VersionMeta::assetIndexId));
        task.getUUID().convention(task.getUsername().zip(uuidCache, (user, cache) ->
                Objects.resolveUuid(offline, cache.getAsFile(), user).toString()));
    }

    public static void fmlEnvironment(RunMinecraft task, Fml env) {
        task.environment("target", env.target);
        task.environment("tweakClass", env.tweakClass);
        task.environment("mainClass", env.launchClass);
        task.environment("MC_VERSION", env.minecraftVersion);
        task.environment("MCP_VERSION", env.mcpVersion);
        task.environment("MCP_MAPPINGS", env.mcpMappings);
        task.environment("MCP_TO_SRG", env.srgToMcp);
        task.dependsOn(env.srgToMcp);
        task.environment("FORGE_GROUP", env.forgeGroup);
        task.environment("FORGE_VERSION", env.forgeVersion);
        if (env.client) {
            task.environment("assetIndex", env.assetIndex);
            task.environment("assetDirectory", env.assets);
            task.environment("nativesDirectory", env.natives);
            task.jvmArgs("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true");
        }
    }

    public static final class Fml {

        public boolean client;
        public Object target;
        public Object tweakClass;
        public Object launchClass;
        public Object minecraftVersion;
        public Object mcpVersion;
        public Object mcpMappings;
        public Object srgToMcp;
        public Object forgeGroup;
        public Object forgeVersion;
        public Object assetIndex;
        public Provider<Directory> assets;
        public Provider<File> natives;

        public Fml forSide(boolean client, Object target, Object tweakClass, Object launchClass) {
            var side = new Fml();
            side.client = client;
            side.target = target;
            side.tweakClass = tweakClass;
            side.launchClass = launchClass;
            side.minecraftVersion = this.minecraftVersion;
            side.mcpVersion = this.mcpVersion;
            side.mcpMappings = this.mcpMappings;
            side.srgToMcp = this.srgToMcp;
            side.forgeGroup = this.forgeGroup;
            side.forgeVersion = this.forgeVersion;
            side.assetIndex = this.assetIndex;
            side.assets = this.assets;
            side.natives = this.natives;
            return side;
        }

    }

    private MinecraftRuns() { }

}
