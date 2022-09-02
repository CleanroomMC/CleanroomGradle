package com.cleanroommc.gradle.extensions;

import com.cleanroommc.gradle.json.MinecraftVersion;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.List;

public class MinecraftExtension {

    public static MinecraftExtension get(Project project) {
        return project.getExtensions().getByType(MinecraftExtension.class);
    }

    private String runDir = "run";
    private String version = "1.12.2";
    private List<String> clientArgs = new ArrayList<>();
    private List<String> serverArgs = new ArrayList<>();
    private List<String> clientJvmArgs = new ArrayList<>();
    private List<String> serverJvmArgs = new ArrayList<>();
    private MinecraftVersion versionInfo;

    public void setRunDir(String runDir) {
        this.runDir = runDir;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void addClientArg(String arg) {
        this.clientArgs.add(arg);
    }

    public void setClientArgs(List<String> clientArgs) {
        this.clientArgs = clientArgs;
    }

    public void addServerArg(String arg) {
        this.serverArgs.add(arg);
    }

    public void setServerArgs(List<String> serverArgs) {
        this.serverArgs = serverArgs;
    }

    public void addClientJvmArg(String arg) {
        this.clientJvmArgs.add(arg);
    }

    public void setClientJvmArgs(List<String> clientJvmArgs) {
        this.clientJvmArgs = clientJvmArgs;
    }

    public void addServerJvmArg(String arg) {
        this.serverJvmArgs.add(arg);
    }

    public void setServerJvmArgs(List<String> serverJvmArgs) {
        this.serverJvmArgs = serverJvmArgs;
    }

    public void setVersionFile(MinecraftVersion versionInfo) {
        this.versionInfo = versionInfo;
    }

    public String getRunDir() {
        return runDir == null || runDir.isEmpty() ? "run" : runDir;
    }

    public String getVersion() {
        return version == null || version.isEmpty() ? "1.12.2" : version;
    }

    public List<String> getClientArgs() {
        return clientArgs;
    }

    public List<String> getServerArgs() {
        return serverArgs;
    }

    public List<String> getClientJvmArgs() {
        return clientJvmArgs;
    }

    public List<String> getServerJvmArgs() {
        return serverJvmArgs;
    }

    public MinecraftVersion getVersionInfo() {
        return versionInfo;
    }

}
