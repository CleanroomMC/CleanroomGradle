package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import org.gradle.api.Project;

/**
 * Registers tasks for setting up a mod developer's environment against Cleanroom.
 * Only instantiated when {@code cleanroom { loaderProject = false }}.
 */
public final class UserDevTasks {

    private static final String GROUP_NAME = "UserDev Tasks";

    public UserDevTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla, MCPTasks mcp, DistributionTasks dist) {
        // TODO: environment setup tasks for mod developers consuming the userdev artifact
    }

}
