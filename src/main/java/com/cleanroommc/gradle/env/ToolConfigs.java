package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.util.Objects;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.objectweb.asm.Opcodes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maven tool classpaths shared by vanilla, loader, and userdev pipelines.
 */
public final class ToolConfigs {

    private static final String ASM_VERSION = asmVersion();
    private static final Map<String, String> DEFAULTS = Map.of(
            "accesstransformer", "net.minecraftforge:accesstransformers:8.2.17", // Forge
            "decompiler", "com.cleanroommc:cleanflower:1.0.0", // Cleanroom
            "installertools", "net.minecraftforge:installertools:1.4.1:fatjar", // Forge
            "mergetool", "net.minecraftforge:mergetool:1.2.2" // Forge
    );
    /**
     * Tools whose output decides what the decompiled Minecraft tree looks like.
     */
    public static final List<String> SOURCE_TOOLS = List.of("accesstransformer", "decompiler", "mergetool");
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

    public static NamedDomainObjectProvider<Configuration> get(Project project, String name) {
        return project.getConfigurations().named(name);
    }

    public static Provider<Map<String, String>> sourceToolNotations(Project project) {
        var configurations = project.getConfigurations();
        Provider<Map<String, String>> notations = project.getProviders().provider(LinkedHashMap::new);
        for (var name : SOURCE_TOOLS) {
            var tool = configurations.named(name).map(Objects::fullNotation);
            notations = notations.zip(tool, (resolved, notation) -> {
                var merged = new LinkedHashMap<>(resolved);
                merged.put(name, notation);
                return merged;
            });
        }
        return notations;
    }

    static Provider<Map<String, String>> configured(Project project) {
        var configurations = project.getConfigurations();
        Provider<Map<String, String>> tools = project.getProviders().provider(LinkedHashMap::new);
        for (var name : DEFAULTS.keySet().stream().sorted().toList()) {
            var fallback = DEFAULTS.get(name);
            var declared = configurations.named(name).map(configuration -> configuration.getDependencies().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));
            tools = tools.zip(declared, (resolved, value) -> {
                var merged = new LinkedHashMap<>(resolved);
                merged.put(name, value.isEmpty() ? fallback : value);
                return merged;
            });
        }
        return tools;
    }

    private static void tool(Project project, String name, String defaultNotation) {
        var factory = project.getDependencyFactory();
        project.getConfigurations().register(name, config -> {
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
            config.setDescription("Classpath for the " + name + " tool");
            config.defaultDependencies(deps -> deps.add(factory.create(defaultNotation)));
            config.getResolutionStrategy().force((Object[]) PINNED_ASM_MODULES);
        });
    }

    private static String asmVersion() {
        var version = Opcodes.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("The loaded ASM library does not declare Implementation-Version");
        }
        return version;
    }

    private ToolConfigs() { }

}
