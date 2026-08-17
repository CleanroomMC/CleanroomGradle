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
                var message = ("Gradle is offline and no cached launcher manifest exists at %s. "
                        + "Run the requested task once without --offline to resolve Minecraft %s from %s.")
                        .formatted(manifestFile, version, params.getManifestUrl().get());
                throw new IllegalStateException(message);
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
            var message = "Minecraft version '%s' was not found in launcher manifest %s.".formatted(version, manifestFile);
            throw new IllegalArgumentException(message);
        }

        var metaFile = new File(cacheDirectory, "versions/%s/meta.json".formatted(version));
        if (!IO.sha1Match(metaFile, metaSha1)) {
            if (offline) {
                var message = ("Gradle is offline and cached metadata for Minecraft %s is missing or corrupt at %s. "
                        + "Run the requested task once without --offline to download %s.").formatted(version, metaFile, metaUrl);
                throw new IllegalStateException(message);
            }
            IO.downloadWithETag(metaUrl, metaFile);
            var actualSha1 = IO.sha1(metaFile);
            if (!actualSha1.equalsIgnoreCase(metaSha1)) {
                var message = "Downloaded metadata failed SHA-1 verification at %s: expected %s but got %s."
                        .formatted(metaFile, metaSha1, actualSha1);
                throw new IllegalStateException(message);
            }
        }
        return IO.readJson(metaFile, VersionMeta.class);
    }

}
