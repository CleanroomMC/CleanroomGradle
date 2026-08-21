package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Nested input type for a resolved runtime library the MMC pack and installer profile publish.
 */
public abstract class LibraryArtifact {

    @Input
    public abstract Property<String> getCoordinate();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getFile();

}
