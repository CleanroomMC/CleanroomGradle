package com.cleanroommc.gradle;

import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.task.ManifestTasks;
import com.cleanroommc.gradle.task.artifact.ArtifactTasks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;

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
        extensions.add(CleanroomGradle.class, CleanroomGradle.EXTENSION_NAME, new CleanroomGradle());
        // Create Extensions
        extensions.create(ManifestExtension.NAME, ManifestExtension.class, project);

        // Create Tasks
        TaskContainer tasks = project.getTasks();
        CleanroomLogging.step(logger, "Registering manifest tasks...");
        ManifestTasks.register(tasks);

        // After Project Evaluation
        CleanroomLogging.step(logger, "Registering dynamic manifest tasks...");
        project.afterEvaluate(ManifestTasks::registerAfterEvaluation);
        CleanroomLogging.step(logger, "Registering dynamic artifact tasks...");
        project.afterEvaluate(ArtifactTasks::register);

    }

}
