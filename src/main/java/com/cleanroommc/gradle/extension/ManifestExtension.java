package com.cleanroommc.gradle.extension;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class ManifestExtension {

    public static final String NAME = "manifest";

    @Inject
    public ManifestExtension(Project project) {
        getLocation().set(CleanroomMeta.getVanillaVersionsCacheDirectory(project, "version_manifest_v2.json"));
    }

    public abstract RegularFileProperty getLocation();

    public abstract Property<ManifestVersion> getVersions();

}
