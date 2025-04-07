package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.env.MCPTasks;
import com.cleanroommc.gradle.env.VanillaTasks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CleanroomGradle implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("Running CleanroomGradle v" + this.getClass().getPackage().getImplementationVersion());

        final var cleanroomExtension = Objects.extension(project, "cleanroom", CleanroomExtension.class);

        VanillaTasks.init(project, cleanroomExtension);

        MCPTasks.init(project, cleanroomExtension);

        project.afterEvaluate($ -> {
            VanillaTasks.afterEvaluate($, cleanroomExtension);
            MCPTasks.afterEvaluate($, cleanroomExtension);
        });
    }

}
