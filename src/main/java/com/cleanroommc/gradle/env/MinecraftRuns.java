/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.Objects;

import org.gradle.api.provider.Provider;

/**
 * Shared wiring for {@link RunMinecraft} tasks so they do not look up the project extension.
 */
public final class MinecraftRuns {

    private MinecraftRuns() { }

    public static void caches(RunMinecraft task, CachesExtension caches, Provider<VersionMeta> versionMeta,
        boolean offline
    ) {
        var assets = caches.getDirectory().dir("assets");
        var uuidCache = caches.getDirectory().file("uuid_cache.properties");
        task.getVanillaAssetsLocation().set(assets);
        task.getUuidCache().set(uuidCache);
        task.getOffline().set(offline);
        task.getVersionMeta().convention(versionMeta);
        task.getAssetIndexVersion().convention(versionMeta.map(VersionMeta::assetIndexId));
        task.getUUID()
            .convention(task.getUsername()
                    .zip(uuidCache, (user, cache) -> Objects.resolveUuid(offline, cache.getAsFile(), user).toString())
            );
    }

    public static void fml(RunMinecraft task, boolean client, Provider<String> launchClass, Provider<String> tweakClass,
        Provider<?> srgToMcp
    ) {
        task.getMainClass().set(launchClass);
        task.args("--tweakClass", tweakClass);
        task.systemProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", srgToMcp);
        task.dependsOn(srgToMcp);
        if (client) {
            task.jvmArgs("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true");
        }
    }

}
