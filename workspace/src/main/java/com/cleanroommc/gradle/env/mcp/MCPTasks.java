package com.cleanroommc.gradle.env.mcp;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.extension.Properties;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.env.common.task.Decompile;
import com.cleanroommc.gradle.env.mcp.task.Deobfuscate;
import com.cleanroommc.gradle.env.mcp.task.MergeJars;
import com.cleanroommc.gradle.env.mcp.task.PolishDeobfuscation;
import com.cleanroommc.gradle.env.mcp.task.Remap;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public class MCPTasks {

    public static final String EXTRACT_MCP_CONFIG = "extractMcpConfig";
    public static final String MERGE_JARS = "mergeJars";
    public static final String DEOBFUSCATE = "deobfuscate";
    public static final String POLISH_DEOBFUSCATED_JAR = "polishDeobfuscatedJar";
    public static final String DECOMPILE = "decompile";
    public static final String EXTRACT_SRG_PATCHES = "extractSrgPatches";
    public static final String PATCH_JAR = "patchJar";
    public static final String EXTRACT_MCP_MAPPINGS = "extractMcpMappings";
    public static final String REMAP_JAR = "remapJar";
    public static final String RUN_SRG_CLIENT = "runSrgClient";

    public static MCPTasks make(Project project, String version) {
        return Properties.getOrSet(project, version.replace('.', '_') + "_MCPTasks", () -> new MCPTasks(project, version));
    }

    private final Project project;
    private final String version;
    private final VanillaTasks vanillaTasks;
    private final TaskGroup group;
    private final File cache;

    private Configuration mcpConfig, mcpMappingConfig;

    private TaskProvider<Copy> extractMcpConfig, extractMcpMappings;
    private TaskProvider<MergeJars> mergeJars;
    private TaskProvider<Deobfuscate> deobfuscate;
    private TaskProvider<PolishDeobfuscation> polishDeobfuscatedJar;
    private TaskProvider<Decompile> decompile;
    private TaskProvider<DefaultTask> extractSrgPatches;
    private TaskProvider<ApplyDiffs> patchJar;
    private TaskProvider<Remap> remapJar;

    private MCPTasks(Project project, String minecraftVersion) {
        this.project = project;
        this.version = minecraftVersion;
        vanillaTasks = VanillaTasks.make(project, minecraftVersion);
        group = TaskGroup.of("vanilla " + minecraftVersion);
        cache = Locations.global(project, Meta.CG_FOLDER, "versions", minecraftVersion, "mcp_config");
        initRepos();
        initConfigs();
        initTasks();
    }

    public String minecraftVersion() {
        return version;
    }

    public VanillaTasks vanillaTasks() {
        return vanillaTasks;
    }

    public Provider<File> srgMapping() {
        return extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "joined.tsrg"));
    }

    public Configuration mcpConfig() {
        return mcpConfig;
    }

    public Configuration mcpMappingConfig() {
        return mcpMappingConfig;
    }

    public TaskProvider<Copy> extractMcpConfig() {
        return extractMcpConfig;
    }

    public TaskProvider<MergeJars> mergeJars() {
        return mergeJars;
    }

    private void initRepos() {
        var repos = project.getRepositories();
        repos.mavenCentral();
        repos.exclusiveContent(ecr -> {
            ecr.forRepositories(repos.maven(mar -> {
                mar.setName("MinecraftForge");
                mar.setUrl(Meta.FORGE_REPO);
                mar.getMetadataSources().artifact(); // For MCP Mappings
            }));
            ecr.filter(ircd -> {
                ircd.includeGroup("net.minecraftforge");
                ircd.includeGroup("de.oceanlabs.mcp");
            });
        });
    }

    private void initConfigs() {
        mcpConfig = Configurations.of(project, "mcp");
        mcpMappingConfig = Configurations.of(project, "mcpMapping");

        project.afterEvaluate($ -> {
            Dependencies.add(project, mcpConfig, "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
            Dependencies.add(project, mcpMappingConfig, "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");
        });
    }

    private void initTasks() {
        extractMcpConfig = group.add(Tasks.unzip(project, taskName(EXTRACT_MCP_CONFIG), mcpConfig, location("20201025_185735")));

        mergeJars = group.add(Tasks.with(project, taskName(MERGE_JARS), MergeJars.class, t -> {
            t.dependsOn(extractMcpConfig);
            t.getClientJar().fileProvider(vanillaTasks.clientJar());
            t.getServerJar().fileProvider(vanillaTasks.serverJar());
            t.getSrgMappingFile().fileProvider(srgMapping());
            t.getMinecraftVersion().set(version);
            t.getMergedJar().set(location("merged.jar"));
        }));

        deobfuscate = group.add(Tasks.with(project, taskName(DEOBFUSCATE), Deobfuscate.class, t -> {
            t.getObfuscatedJar().set(mergeJars.flatMap(MergeJars::getMergedJar));
            t.getSrgMappingFile().fileProvider(srgMapping());
            t.getDeobfuscatedJar().set(location("deobfuscated.jar"));
        }));

        polishDeobfuscatedJar = group.add(Tasks.with(project, taskName(POLISH_DEOBFUSCATED_JAR), PolishDeobfuscation.class, t -> {
            t.getDeobfuscatedJar().set(deobfuscate.flatMap(Deobfuscate::getDeobfuscatedJar));
            t.getAccessFile().fileProvider(extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "access.txt")));
            t.getConstructorsFile().fileProvider(extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "constructors.txt")));
            t.getExceptionsFile().fileProvider(extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "exceptions.txt")));
            t.getPolishedJar().set(location("polished_deobfuscated.jar"));
        }));

        decompile = group.add(Tasks.with(project, taskName(DECOMPILE), Decompile.class, t -> {
            t.getCompiledJar().set(polishDeobfuscatedJar.flatMap(PolishDeobfuscation::getPolishedJar));
            t.getLibraries().from(vanillaTasks.vanillaConfig());
            t.getDecompiledJar().set(location("decompiled.jar"));
            t.getLogFile().set(Locations.temp(project, "decompile.log"));
        }));

        var srgPatchesZip = location("patches", "srg.zip");

        extractSrgPatches = group.add(Tasks.withDefault(project, taskName(EXTRACT_SRG_PATCHES),
                                                            t -> t.doLast($ -> IO.copyResource("patches/srg.zip", srgPatchesZip))));

        patchJar = group.add(Tasks.with(project, taskName(PATCH_JAR), ApplyDiffs.class, t -> {
            t.dependsOn(extractSrgPatches);
            t.getCopyOverSource().set(true);
            t.source(decompile.flatMap(Decompile::getDecompiledJar));
            t.patch(srgPatchesZip);
            t.modified(location("patched.jar"));
        }));

        var mcpMappingFolder = location("mappings", "mcp", "stable", "39");

        extractMcpMappings = group.add(Tasks.unzip(project, taskName(EXTRACT_MCP_MAPPINGS), mcpMappingConfig, mcpMappingFolder));

        remapJar = group.add(Tasks.with(project, taskName(REMAP_JAR), Remap.class, t -> {
            t.dependsOn(extractMcpMappings);
            t.getSrgJar().fileProvider(patchJar.map(ApplyDiffs::getModifiedPath));
            t.getFieldMappings().set(Locations.file(mcpMappingFolder, "fields.csv"));
            t.getMethodMappings().set(Locations.file(mcpMappingFolder, "methods.csv"));
            t.getParameterMappings().set(Locations.file(mcpMappingFolder, "params.csv"));
            t.getRemappedJar().set(location("remapped.jar"));
        }));

    }

    private String taskName(String taskName) {
        return version.replace('.', '_') + "_" + taskName;
    }

    private File location(String... paths) {
        return Locations.file(cache, paths);
    }

}
