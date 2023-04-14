package com.cleanroommc.gradle.extension;

import com.cleanroommc.gradle.util.DirectoryUtil;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;

public abstract class ManifestExtension {

    public static final String NAME = "manifest";

    @Inject
    public ManifestExtension(Project project) {
        getLocation().set(DirectoryUtil.create(project, dir -> dir.getMainVersionManifest("version_manifest_v2")));
    }

    public abstract RegularFileProperty getLocation();
}
