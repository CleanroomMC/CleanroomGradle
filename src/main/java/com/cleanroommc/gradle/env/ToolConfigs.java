package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.util.Objects;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Maven tool classpaths shared by vanilla, loader, and userdev pipelines.
 */
public final class ToolConfigs {

    public static void register(Project project) {
        Meta.DEFAULT_TOOLS.forEach((name, notation) -> Objects.toolConfig(project, name, notation));
    }

    public static Configuration get(Project project, String name) {
        return project.getConfigurations().getByName(name);
    }

    private ToolConfigs() { }

}
