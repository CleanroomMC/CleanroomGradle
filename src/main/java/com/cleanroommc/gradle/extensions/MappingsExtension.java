package com.cleanroommc.gradle.extensions;

import org.gradle.api.Project;

public class MappingsExtension {

    public static MappingsExtension get(Project project) {
        return project.getExtensions().getByType(MappingsExtension.class);
    }

    private String channel = "stable";
    private String version = "39";
    private String mcVersion = "1.12";

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setVersion(String version) {
        if (version.equals("1.12.2")) {
            version = "1.12"; // Stupid
        }
        this.version = version;
    }

    public void setMCVersion(String mcVersion) {
        this.mcVersion = mcVersion;
    }

    public String getChannel() {
        return channel == null ? "stable" : channel;
    }

    public String getVersion() {
        return version == null ? "39" : version;
    }

    public String getMCVersion() {
        return mcVersion == null ? "1.12.2" : mcVersion;
    }

}
