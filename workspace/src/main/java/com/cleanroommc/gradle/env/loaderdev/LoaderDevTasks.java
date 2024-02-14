package com.cleanroommc.gradle.env.loaderdev;

import com.cleanroommc.gradle.env.mcp.MCPTasks;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class LoaderDevTasks {

    @Inject
    public abstract Project getProject();

    public abstract Property<MCPTasks> getMcpComponent();

    public LoaderDevTasks() {
        getMcpComponent().convention(MCPTasks.make(getProject(), "1.12.2"));
    }

}
