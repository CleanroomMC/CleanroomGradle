package com.cleanroommc.gradle.api.ext;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Loader-workspace and distribution settings. Used when {@link ProjectMode#LOADER}.
 */
public abstract class LoaderExtension {

    public abstract Property<String> getForgeVersion();

    public abstract Property<String> getInstallerVersion();

    public abstract ListProperty<String> getInstallerJvmArgs();

    public abstract ConfigurableFileCollection getAccessTransformers();

    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    public abstract ListProperty<String> getLwjglNativesClassifiers();

}
