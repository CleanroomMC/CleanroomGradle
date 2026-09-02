package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.util.LwjglNatives;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.List;

/**
 * Loader-workspace and distribution settings. Used when {@link ProjectMode#LOADER}.
 */
public abstract class LoaderExtension {

    public abstract Property<String> getForgeVersion();

    public abstract Property<String> getInstallerVersion();

    public abstract Property<String> getClientMainClass();

    public abstract Property<String> getServerMainClass();

    public abstract Property<String> getLaunchClass();

    public abstract Property<String> getClientTweakClass();

    public abstract Property<String> getServerTweakClass();

    public abstract Property<String> getClientTarget();

    public abstract Property<String> getServerTarget();

    public abstract ListProperty<String> getInstallerJvmArgs();

    public abstract ConfigurableFileCollection getAccessTransformers();

    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    public abstract ListProperty<String> getLwjglNativesClassifiers();

    public abstract Property<Boolean> getIntermediateRuns();

    public LoaderExtension() {
        getForgeVersion().convention("14.23.5.2864");
        getInstallerVersion().convention("0.1.2");
        getClientMainClass().convention("com.cleanroommc.boot.MainClient");
        getServerMainClass().convention("com.cleanroommc.boot.MainServer");
        getLaunchClass().convention("top.outlands.foundation.boot.Foundation");
        getClientTweakClass().convention("net.minecraftforge.fml.common.launcher.FMLTweaker");
        getServerTweakClass().convention("net.minecraftforge.fml.common.launcher.FMLServerTweaker");
        getClientTarget().convention("fmldevclient");
        getServerTarget().convention("fmldevserver");
        getInstallerJvmArgs().convention(List.of());
        getLwjglNativesClassifiers().convention(LwjglNatives.CLASSIFIERS);
        getIntermediateRuns().convention(false);
    }

}
