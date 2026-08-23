package com.cleanroommc.gradle.env;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.util.Map;

/**
 * Maven tool classpaths shared by vanilla, loader, and userdev pipelines.
 */
public final class ToolConfigs {

    // TODO: configurable
    private static final String ASM_VERSION = "9.10.1";
    private static final Map<String, String> DEFAULTS = Map.of(
            "accesstransformer", "net.minecraftforge:accesstransformers:8.2.17", // Forge
            "decompiler", "com.cleanroommc:cleanflower:1.0.0", // Cleanroom
            "installertools", "net.minecraftforge:installertools:1.4.1:fatjar", // Forge
            "mergetool", "net.minecraftforge:mergetool:1.2.2" // Forge
    );
    /**
     * Pinned ASM modules for tools that may depend on older ASM versions
     */
    private static final String[] PINNED_ASM_MODULES = new String[] {
            "org.ow2.asm:asm:" + ASM_VERSION,
            "org.ow2.asm:asm-analysis:" + ASM_VERSION,
            "org.ow2.asm:asm-commons:" + ASM_VERSION,
            "org.ow2.asm:asm-tree:" + ASM_VERSION,
            "org.ow2.asm:asm-util:" + ASM_VERSION
    };

    public static void register(Project project) {
        DEFAULTS.forEach((name, notation) -> tool(project, name, notation));
    }

    public static Configuration get(Project project, String name) {
        return project.getConfigurations().getByName(name);
    }

    private static Configuration tool(Project project, String name, String defaultNotation) {
        var config = project.getConfigurations().maybeCreate(name);
        config.setCanBeConsumed(false);
        config.setCanBeResolved(true);
        config.setDescription("Classpath for the " + name + " tool");
        var factory = project.getDependencyFactory();
        config.defaultDependencies(deps -> deps.add(factory.create(defaultNotation)));
        config.getResolutionStrategy().force((Object[]) PINNED_ASM_MODULES);
        return config;
    }

    private ToolConfigs() { }

}
