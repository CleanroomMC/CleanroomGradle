package com.cleanroommc.gradle.extension;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class ManifestExtension {

    public static final String NAME = "manifest";

    @Inject
    public ManifestExtension(Project project) {
        getLocation().set(CleanroomMeta.getVanillaVersionsCacheDirectory(project, "version_manifest_v2.json"));
        getMetadataCache().set(project.getObjects().mapProperty(String.class, VersionMetadata.class).empty());
    }

    public abstract RegularFileProperty getLocation();

    public abstract Property<ManifestVersion> getVersions();

    public abstract MapProperty<String, VersionMetadata> getMetadataCache();

}
