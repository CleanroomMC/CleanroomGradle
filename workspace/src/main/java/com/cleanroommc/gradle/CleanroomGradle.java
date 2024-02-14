package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.named.extension.CleanroomExtension;
import com.cleanroommc.gradle.api.named.extension.Extensions;
import com.cleanroommc.gradle.api.named.extension.Properties;
import com.cleanroommc.gradle.env.common.Preparation;
import com.cleanroommc.gradle.env.loaderdev.LoaderDevExtension;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CleanroomGradle implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Extensions
        var cleanroomExt = Extensions.create(project, CleanroomExtension.EXT_NAME, CleanroomExtension.class);
        registerExtensions(project, cleanroomExt);

        // Environments
        Preparation.initialize(project);

        // After Evaluation
        project.afterEvaluate($ -> {
            if (Properties.getBoolean($, VanillaTasks.DOWNLOAD_VERSION_MANIFEST_PROPERTY)) {
                VanillaTasks.downloadVersionManifest($);
            }
        });
    }

    private void registerExtensions(Project project, CleanroomExtension ext) {
        project.getLogger().lifecycle("Checking property '{}'", LoaderDevExtension.PROPERTY_NAME);
        if (Properties.getBoolean(project, LoaderDevExtension.PROPERTY_NAME)) {
            project.getLogger().lifecycle("Enabling LoaderDev Extension.");
            Extensions.create(ext, LoaderDevExtension.EXT_NAME, LoaderDevExtension.class);
        }
    }

}
