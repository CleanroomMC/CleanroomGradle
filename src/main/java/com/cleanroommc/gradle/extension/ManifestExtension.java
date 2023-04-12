package com.cleanroommc.gradle.extension;

import com.cleanroommc.gradle.json.schema.AssetIndexObjects;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.util.DirectoryUtil;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class ManifestExtension {

    public static final String NAME = "manifest";

    @Inject
    public ManifestExtension(Project project) {
        getLocation().set(DirectoryUtil.create(project, dir -> dir.getMainVersionManifest("version_manifest_v2")));
        getMetadataCache().set(project.getObjects().mapProperty(String.class, VersionMetadata.class).empty());
        getAssetCache().set(project.getObjects().mapProperty(String.class, AssetIndexObjects.class).empty());
    }

    public abstract RegularFileProperty getLocation();

    public abstract Property<ManifestVersion> getVersions();

    public abstract MapProperty<String, VersionMetadata> getMetadataCache();

    public abstract MapProperty<String, AssetIndexObjects> getAssetCache();
}
