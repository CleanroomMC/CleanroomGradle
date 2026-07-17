package com.cleanroommc.gradle.api.source;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.IO;
import com.google.gson.JsonObject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import java.io.File;

/**
 * Resolves the {@link VersionMeta} of an arbitrary Minecraft version through Mojang's launcher manifest:
 * {@code version_manifest_v2.json} -> version entry (url + sha1) -> version meta json.
 */
public abstract class LauncherVersionMetaValueSource implements ValueSource<VersionMeta, LauncherVersionMetaValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        Property<String> getManifestUrl();

        Property<String> getMinecraftVersion();

        DirectoryProperty getCacheDirectory();

        Property<Boolean> getOffline();

    }

    @Override
    public VersionMeta obtain() {
        var params = this.getParameters();
        var version = params.getMinecraftVersion().get();
        var offline = params.getOffline().getOrElse(false);
        var cacheDirectory = params.getCacheDirectory().get().getAsFile();

        var manifestFile = new File(cacheDirectory, "version_manifest_v2.json");
        if (offline) {
            if (!manifestFile.isFile()) {
                throw new IllegalStateException("Gradle is offline and no cached launcher manifest exists at %s. Network access is required once to resolve Minecraft %s.".formatted(manifestFile, version));
            }
        } else {
            IO.downloadWithETag(params.getManifestUrl().get(), manifestFile);
        }

        String metaUrl = null;
        String metaSha1 = null;
        for (var element : IO.readJson(manifestFile, JsonObject.class).getAsJsonArray("versions")) {
            var entry = element.getAsJsonObject();
            if (version.equals(entry.get("id").getAsString())) {
                metaUrl = entry.get("url").getAsString();
                metaSha1 = entry.get("sha1").getAsString();
                break;
            }
        }
        if (metaUrl == null) {
            throw new IllegalArgumentException("Minecraft version '%s' was not found in the launcher manifest.".formatted(version));
        }

        var metaFile = new File(cacheDirectory, "versions/%s/meta.json".formatted(version));
        if (!IO.sha1Match(metaFile, metaSha1)) {
            if (offline) {
                throw new IllegalStateException("Gradle is offline and no valid cached version metadata exists at %s. Network access is required once to resolve Minecraft %s.".formatted(metaFile, version));
            }
            IO.downloadWithETag(metaUrl, metaFile);
            if (!IO.sha1Match(metaFile, metaSha1)) {
                throw new IllegalStateException("SHA-1 mismatch for %s: expected %s but got %s.".formatted(metaFile, metaSha1, IO.sha1(metaFile)));
            }
        }
        return IO.readJson(metaFile, VersionMeta.class);
    }

}
