package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * The nested input type buildscripts and {@code DistributionTasks} instantiate for every resolved runtime dependency.
 */
public abstract class LibraryArtifact {

    @Input
    public abstract Property<String> getCoordinate();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getFile();

}
