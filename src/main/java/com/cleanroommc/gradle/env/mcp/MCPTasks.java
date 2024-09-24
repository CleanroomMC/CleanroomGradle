package com.cleanroommc.gradle.env.mcp;

import com.cleanroommc.gradle.api.Environment;
import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.SourceSets;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.env.common.task.Decompile;
import com.cleanroommc.gradle.env.common.task.RunMinecraft;
import com.cleanroommc.gradle.env.mcp.task.Deobfuscate;
import com.cleanroommc.gradle.env.mcp.task.MergeJars;
import com.cleanroommc.gradle.env.mcp.task.PolishDeobfuscation;
import com.cleanroommc.gradle.env.mcp.task.Remap;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * Fixme: 1.12.2 only. Don't care about other versions for now.
 */
public class MCPTasks {

    public static final String EXTRACT_MCP_CONFIG = "extractMcpConfig";
    public static final String EXTRACT_CLIENT_RESOURCES = "extractClientResources";
    public static final String EXTRACT_SERVER_RESOURCES = "extractServerResources";
    public static final String MERGE_JARS = "mergeJars";
    public static final String DEOBFUSCATE = "deobfuscate";
    public static final String POLISH_DEOBFUSCATED_JAR = "polishDeobfuscatedJar";
    public static final String DECOMPILE = "decompile";
    public static final String EXTRACT_SRG_PATCHES = "extractSrgPatches";
    public static final String PATCH_JAR = "patchJar";
    public static final String EXTRACT_MCP_MAPPINGS = "extractMcpMappings";
    public static final String REMAP_JAR = "remapJar";
    public static final String ADD_MINECRAFT_SOURCES = "addMinecraftSources";
    public static final String RUN_SRG_CLIENT = "runSrgClient";
    public static final String RUN_SRG_SERVER = "runSrgServer";
    public static final String RUN_MCP_CLIENT = "runMcpClient";
    public static final String RUN_MCP_SERVER = "runMcpServer";

    private final Project project;
    private final String version;
    private final VanillaTasks vanillaTasks;
    private final TaskGroup group;
    private final File cache;

    private Configuration mcpConfig, mcpMappingConfig;

    private NamedDomainObjectProvider<SourceSet> minecraft;

    private TaskProvider<Copy> extractMcpConfig, extractMcpMappings, extractClientResources, extractServerResources;
    private TaskProvider<MergeJars> mergeJars;
    private TaskProvider<Deobfuscate> deobfuscate;
    private TaskProvider<PolishDeobfuscation> polishDeobfuscatedJar;
    private TaskProvider<Decompile> decompile;
    private TaskProvider<DefaultTask> extractSrgPatches;
    private TaskProvider<ApplyDiffs> patchJar;
    private TaskProvider<Remap> remapJar;
    private TaskProvider<RunMinecraft> runSrgClient, runSrgServer, runMcpClient, runMcpServer;

    public MCPTasks(Project project, VanillaTasks vanillaTasks) {
        this.project = project;
        this.vanillaTasks = vanillaTasks;
        this.version = "1.12.2";
        this.group = TaskGroup.of("mcp");
        this.cache = Locations.build(project, "versions", "1.12.2", "mcp_config");
        this.initRepos();
        this.initConfigs();
        this.initSourceSets();
        this.initTasks();
    }

    public String minecraftVersion() {
        return version;
    }

    public VanillaTasks vanillaTasks() {
        return vanillaTasks;
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

    public Provider<File> srgMapping() {
        return this.extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "joined.tsrg"));
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
        this.mcpConfig = Configurations.of(this.project, "mcp_" + this.version.replace('.', '_'));
        this.mcpMappingConfig = Configurations.of(this.project, "mcpMapping_" + this.version.replace('.', '_'));

        this.project.afterEvaluate($ -> {
            Dependencies.add(this.project, this.mcpConfig, "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
            Dependencies.add(this.project, this.mcpMappingConfig, "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");
        });
    }

    private void initSourceSets() {
        this.minecraft = SourceSets.getOrCreate(this.project, "minecraft");
        this.minecraft.configure(set -> {
            SourceSets.addCompileClasspath(set, this.vanillaTasks().vanillaConfig());
            SourceSets.addRuntimeClasspath(set, this.vanillaTasks().vanillaConfig());
        });
    }

    private void initTasks() {
        var project = this.project;
        var group = this.group;

        this.extractMcpConfig = group.add(Tasks.unzip(project, this.taskName(EXTRACT_MCP_CONFIG), this.mcpConfig, this.location("20201025_185735")));

        this.extractClientResources = group.add(Tasks.unzip(project, this.taskName(EXTRACT_CLIENT_RESOURCES), this.vanillaTasks.clientJar(),
                location("build", "client_resources", version), spec -> spec.exclude("**/*.class").setIncludeEmptyDirs(false)));

        this.extractServerResources = group.add(Tasks.unzip(project, this.taskName(EXTRACT_SERVER_RESOURCES), this.vanillaTasks.serverJar(),
                this.location("build", "server_resources", version), spec -> spec.exclude("**/*.class").setIncludeEmptyDirs(false)));

        this.mergeJars = group.add(Tasks.with(project, this.taskName(MERGE_JARS), MergeJars.class, t -> {
            t.dependsOn(this.extractMcpConfig);
            t.getClientJar().fileProvider(this.vanillaTasks.clientJar());
            t.getServerJar().fileProvider(this.vanillaTasks.serverJar());
            t.getSrgMappingFile().fileProvider(this.srgMapping());
            t.getMinecraftVersion().set(this.version);
            t.getMergedJar().set(this.location("merged.jar"));
        }));

        this.deobfuscate = group.add(Tasks.with(project, this.taskName(DEOBFUSCATE), Deobfuscate.class, t -> {
            t.getObfuscatedJar().set(this.mergeJars.flatMap(MergeJars::getMergedJar));
            t.getSrgMappingFile().fileProvider(this.srgMapping());
            t.getDeobfuscatedJar().set(this.location("deobfuscated.jar"));
        }));

        this.polishDeobfuscatedJar = group.add(Tasks.with(project, this.taskName(POLISH_DEOBFUSCATED_JAR), PolishDeobfuscation.class, t -> {
            t.getDeobfuscatedJar().set(this.deobfuscate.flatMap(Deobfuscate::getDeobfuscatedJar));
            t.getAccessFile().fileProvider(this.extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "access.txt")));
            t.getConstructorsFile().fileProvider(this.extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "constructors.txt")));
            t.getExceptionsFile().fileProvider(this.extractMcpConfig.map(Copy::getDestinationDir).map(f -> Locations.file(f, "config", "exceptions.txt")));
            t.getPolishedJar().set(this.location("polished_deobfuscated.jar"));
        }));

        this.decompile = group.add(Tasks.with(project, this.taskName(DECOMPILE), Decompile.class, t -> {
            t.getCompiledJar().set(this.polishDeobfuscatedJar.flatMap(PolishDeobfuscation::getPolishedJar));
            t.getLibraries().from(this.vanillaTasks.vanillaConfig());
            t.args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-ind=    ");
            t.getDecompiledJar().set(this.location("decompiled.jar"));
            t.getLogFile().set(Locations.temp(project, "decompile.log"));
        }));

        var srgPatchesZip = this.location("patches", "srg.zip");

        this.extractSrgPatches = group.add(Tasks.withDefault(project, taskName(EXTRACT_SRG_PATCHES),
                t -> t.doLast($ -> IO.copyResource("patches/srg.zip", srgPatchesZip))));

        this.patchJar = group.add(Tasks.with(project, this.taskName(PATCH_JAR), ApplyDiffs.class, t -> {
            t.dependsOn(this.extractSrgPatches, this.decompile);
            t.getCopyOverSource().set(true);
            t.source(this.decompile.flatMap(Decompile::getDecompiledJar));
            t.patch(srgPatchesZip);
            t.modified(this.location("patched.jar"));
        }));

        var mcpMappingFolder = this.location("mappings", "mcp", "stable", "39");

        this.extractMcpMappings = group.add(Tasks.unzip(project, this.taskName(EXTRACT_MCP_MAPPINGS), this.mcpMappingConfig, mcpMappingFolder));

        this.remapJar = group.add(Tasks.with(project, this.taskName(REMAP_JAR), Remap.class, t -> {
            t.dependsOn(this.extractMcpMappings);
            t.getSrgJar().fileProvider(this.patchJar.map(ApplyDiffs::getModifiedPath));
            t.getFieldMappings().set(Locations.file(mcpMappingFolder, "fields.csv"));
            t.getMethodMappings().set(Locations.file(mcpMappingFolder, "methods.csv"));
            t.getParameterMappings().set(Locations.file(mcpMappingFolder, "params.csv"));
            t.getRemappedJar().set(this.location("remapped.jar"));
        }));

        var addMinecraftSources = group.add(Tasks.unzip(project, this.taskName(ADD_MINECRAFT_SOURCES),
                this.remapJar.map(Remap::getRemappedJar), SourceSets.sourceFrom(minecraft)));

        this.runSrgClient = group.add(Tasks.with(project, this.taskName(RUN_SRG_CLIENT), RunMinecraft.class, t -> {
            t.dependsOn(this.vanillaTasks.downloadAssets());
            t.getMinecraftVersion().set(this.version);
            t.getSide().set(Side.CLIENT);
            t.getNatives().fileProvider(this.vanillaTasks.extractNatives().map(Copy::getDestinationDir));
            t.getAssetIndexVersion().set(this.vanillaTasks.assetIndexId());
            t.getVanillaAssetsLocation().set(Locations.build(project, "assets"));
            t.setWorkingDir(Locations.run(project, this.version, Environment.SRG, Side.CLIENT));
            t.classpath(this.polishDeobfuscatedJar.map(PolishDeobfuscation::getPolishedJar));
            t.classpath(this.extractClientResources.map(Copy::getDestinationDir));
            t.classpath(this.extractServerResources.map(Copy::getDestinationDir));
            t.classpath(this.vanillaTasks.vanillaConfig());
            t.getMainClass().set("net.minecraft.client.main.Main");
        }));

        this.runSrgServer = group.add(Tasks.with(project, this.taskName(RUN_SRG_SERVER), RunMinecraft.class, t -> {
            t.getMinecraftVersion().set(this.version);
            t.getSide().set(Side.SERVER);
            t.getNatives().fileProvider(this.vanillaTasks.extractNatives().map(Copy::getDestinationDir));
            t.setWorkingDir(Locations.run(project, this.version, Environment.SRG, Side.SERVER));
            t.classpath(this.polishDeobfuscatedJar.map(PolishDeobfuscation::getPolishedJar));
            t.classpath(this.extractServerResources.map(Copy::getDestinationDir));
            t.classpath(this.vanillaTasks.vanillaConfig());
            t.getMainClass().set("net.minecraft.server.MinecraftServer");
        }));

        this.minecraft.configure(sources -> {
            Tasks.<JavaCompile>configure(project, sources.getCompileJavaTaskName(), t -> {
                t.dependsOn(addMinecraftSources);
                t.setGroup(group.getName());
                t.getJavaCompiler().set(Providers.javaCompiler(project, 8));
                t.getModularity().getInferModulePath().set(false);
                t.getDestinationDirectory().set(this.location("build", "classes", sources.getName()));
            });
            Tasks.configure(project, sources.getClassesTaskName(), t -> t.setGroup(group.getName()));
            Tasks.configure(project, sources.getProcessResourcesTaskName(), t -> t.setGroup(group.getName()));

            var minecraftJar = group.add(Tasks.with(project, sources.getJarTaskName(), Jar.class, t -> {
                t.dependsOn(sources.getClassesTaskName());
                t.from(Tasks.named(project, sources.getCompileJavaTaskName(), JavaCompile.class).map(JavaCompile::getDestinationDirectory));
                t.getDestinationDirectory().set(this.location("build", "libs", sources.getName()));
                t.getArchiveFileName().set("minecraft-srg-1.12.2.jar");
            }));

            this.runMcpClient = group.add(Tasks.with(project, this.taskName(RUN_MCP_CLIENT), RunMinecraft.class, t -> {
                t.getMinecraftVersion().set(this.version);
                t.getSide().set(Side.CLIENT);
                t.getNatives().fileProvider(this.vanillaTasks.extractNatives().map(Copy::getDestinationDir));
                t.getAssetIndexVersion().set(this.vanillaTasks.assetIndexId());
                t.getVanillaAssetsLocation().set(Locations.build(project, "assets"));
                t.setWorkingDir(Locations.run(project, this.version, Environment.MCP, Side.CLIENT));
                t.classpath(minecraftJar.map(Jar::getArchiveFile));
                t.classpath(this.extractClientResources.map(Copy::getDestinationDir));
                t.classpath(this.extractServerResources.map(Copy::getDestinationDir));
                t.classpath(this.vanillaTasks.vanillaConfig());
                t.getMainClass().set("net.minecraft.client.main.Main");
            }));

            this.runMcpServer = group.add(Tasks.with(project, taskName(RUN_MCP_SERVER), RunMinecraft.class, t -> {
                t.getMinecraftVersion().set(this.version);
                t.getSide().set(Side.SERVER);
                t.getNatives().fileProvider(this.vanillaTasks.extractNatives().map(Copy::getDestinationDir));
                t.setWorkingDir(Locations.run(project, this.version, Environment.MCP, Side.SERVER));
                t.classpath(minecraftJar.map(Jar::getArchiveFile));
                t.classpath(this.extractServerResources.map(Copy::getDestinationDir));
                t.classpath(this.vanillaTasks.vanillaConfig());
                t.getMainClass().set("net.minecraft.server.MinecraftServer");
            }));
        });

    }

    private String taskName(String taskName) {
        // return this.version.replace('.', '_') + "_" + taskName;
        return taskName;
    }

    private File location(String... paths) {
        return Locations.file(this.cache, paths);
    }

}
