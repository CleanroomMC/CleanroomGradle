package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.task.ManifestTasks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtensionContainer;

public class CleanroomGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        Logger logger = project.getLogger();

        CleanroomLogging.title(logger, "Starting CleanroomGradle...");

        CleanroomLogging.step(logger, "Applying Download Gradle Plugin...");
        // Use de.undercouch.download plugin
        project.apply(a -> a.plugin("de.undercouch.download"));

        CleanroomLogging.step(logger, "Creating and injecting extensions...");
        // Inject Extensions
        ExtensionContainer extensions = project.getExtensions();
        CleanroomGradle cleanroomGradle = new CleanroomGradle(project);
        extensions.add(CleanroomGradle.class, CleanroomGradle.EXTENSION_NAME, cleanroomGradle);
        // Create Extensions
        extensions.create(ManifestExtension.NAME, ManifestExtension.class, project);

        // Create Tasks
        ManifestTasks.create(logger, project);

    }

}
