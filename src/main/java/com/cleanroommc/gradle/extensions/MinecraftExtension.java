package com.cleanroommc.gradle.extensions;

import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinecraftExtension {

    public static MinecraftExtension get(Project project) {
        return project.getExtensions().getByType(MinecraftExtension.class);
    }

    private String runDir = "run";
    private String version = "1.12.2";
    private List<String> clientJvmArgs = new ArrayList<>();
    private List<String> serverJvmArgs = new ArrayList<>();

    public void setRunDir(String runDir) {
        this.runDir = runDir;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void addClientJvmArg(String arg) {
        this.clientJvmArgs.add(arg);
    }

    public void setClientJvmArg(List<String> clientJvmArgs) {
        this.clientJvmArgs = clientJvmArgs;
    }

    public void addServerJvmArg(String arg) {
        this.serverJvmArgs.add(arg);
    }

    public void setServerJvmArgs(List<String> serverJvmArgs) {
        this.serverJvmArgs = serverJvmArgs;
    }

    public String getRunDir() {
        return runDir == null || runDir.isEmpty() ? "run" : runDir;
    }

    public String getVersion() {
        return version == null || version.isEmpty() ? "1.12.2" : version;
    }

    public List<String> getClientJvmArgs() {
        return clientJvmArgs == null ? Collections.emptyList() : clientJvmArgs;
    }

    public List<String> getServerJvmArgs() {
        return serverJvmArgs == null ? Collections.emptyList() : serverJvmArgs;
    }

}
