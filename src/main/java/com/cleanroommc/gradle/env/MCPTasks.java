package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.MavenJarExec;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.*;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;

public final class MCPTasks {

    private static final String GROUP_NAME = "MCP Tasks";

    private static Configuration toolConfiguration(Project project, String name, String defaultNotation) {
        var config = project.getConfigurations().maybeCreate(name);
        config.setCanBeConsumed(false);
        config.setCanBeResolved(true);
        config.setDescription("Classpath for the " + name + " tool.");
        config.defaultDependencies(deps -> deps.add(project.getDependencies().create(defaultNotation)));
        return config;
    }

    private static <T extends MavenJarExec> TaskProvider<T> toolTask(
            Project project,
            CleanroomExtension extension,
            String name,
            Class<T> type,
            Configuration toolConfiguration) {
        var task = Tasks.of(project, GROUP_NAME, name, type);
        task.configure(value -> {
            value.getToolClasspath().from(toolConfiguration);
            value.setWorkingDir(extension.getLocalCacheDirectory().dir(name));
        });
        return task;
    }

    public final NamedDomainObjectProvider<Configuration> mcpConfig;
    public final NamedDomainObjectProvider<Configuration> initialPatches;
    public final NamedDomainObjectProvider<Configuration> mcpMappings;
    public final NamedDomainObjectProvider<SourceSet> cleanSrgSource;
    public final NamedDomainObjectProvider<SourceSet> srgSource;
    public final NamedDomainObjectProvider<SourceSet> mcpSource;
    public final TaskProvider<Copy> extractMcpConfig;
    public final TaskProvider<Copy> extractInitialPatches;
    public final TaskProvider<Copy> prepareApplyInitialDiffs;
    public final TaskProvider<Copy> prepareCleanRecompile;
    public final TaskProvider<Copy> extractMcpMappings;
    public final TaskProvider<SplitJar> splitClientJar;
    public final TaskProvider<SplitJar> splitServerJar;
    public final TaskProvider<MergeJars> mergeJars;
    public final TaskProvider<RenameJar> remapNotch2Srg;
    public final TaskProvider<InjectMetadata> injectMetadata;
    public final TaskProvider<RunMinecraft> runSrgClient;
    public final TaskProvider<RunMinecraft> runSrgServer;
    public final TaskProvider<RunMinecraft> runReobfSrgClient;
    public final TaskProvider<RunMinecraft> runReobfSrgServer;
    public final TaskProvider<RunMinecraft> runMcpClient;
    public final TaskProvider<RunMinecraft> runMcpServer;
    public final TaskProvider<Decompile> decompileSrg;
    public final TaskProvider<ApplyDiffs> applyInitialDiffs;
    public final TaskProvider<ApplyDiffs> applyCleanInitialDiffs;
    public final TaskProvider<RemapSrg2Mcp> remapSrg2Mcp;
    public final TaskProvider<Jar> srgJar;
    public final TaskProvider<Jar> cleanSrgJar;
    public final TaskProvider<GenerateBinPatches> genBinPatches;

    public MCPTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla) {
        var repos = project.getRepositories();
        repos.maven(mar -> {
            mar.setName("CleanroomMC");
            mar.setUrl(Meta.CLEANROOM_REPO);
            mar.getMetadataSources().artifact(); // For patches
        });
        repos.maven(mar -> {
            mar.setName("MinecraftForge");
            mar.setUrl(Meta.FORGE_REPO);
            mar.getMetadataSources().artifact(); // For MCP Mappings
        });

        this.mcpConfig = Objects.config(project, "mcpConfig", "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
        this.initialPatches = Objects.config(project, "initialPatches", "com.cleanroommc:initial-patches:1.1.0");
        this.mcpMappings = Objects.config(project, "mcpMappings", "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");

        var mergeTool = toolConfiguration(project, "mergetool", "net.minecraftforge:mergetool:1.2.2");
        var metadataInjector = toolConfiguration(project, "mcinjector", "de.oceanlabs.mcp:mcinjector:3.7.3");
        var decompiler = toolConfiguration(project, "decompiler", "com.cleanroommc:cleanflower:1.0.0");

        this.cleanSrgSource = SourceSets.of(project, "cleanSrgSource");
        this.srgSource = SourceSets.of(project, "srgSource");
        this.mcpSource = SourceSets.of(project, "mcpSource");

        var mcpDir = ext.getVersionCacheDirectory().dir("mcp");
        var mcpConfigDir = ext.getVersionCacheDirectory().dir("mcp_config/config");
        var srgMapping = ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg");

        this.extractMcpConfig = Tasks.unzip(project, GROUP_NAME, "extractMcpConfig", this.mcpConfig, ext.getVersionCacheDirectory().dir("mcp_config"));
        this.splitClientJar = Tasks.of(project, GROUP_NAME, "splitClientJar", SplitJar.class);
        this.splitServerJar = Tasks.of(project, GROUP_NAME, "splitServerJar", SplitJar.class);
        this.mergeJars = toolTask(project, ext, "mergeJars", MergeJars.class, mergeTool);
        this.remapNotch2Srg = project.getTasks().register("remapNotch2Srg", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.remapNotch2Srg.configure(task -> task.setGroup(GROUP_NAME));
        this.injectMetadata = toolTask(project, ext, "injectMetadata", InjectMetadata.class, metadataInjector);
        this.runSrgClient = Tasks.of(project, GROUP_NAME, "runSrgClient", RunMinecraft.class);
        this.runSrgServer = Tasks.of(project, GROUP_NAME, "runSrgServer", RunMinecraft.class);
        this.decompileSrg = toolTask(project, ext, "decompileSrg", Decompile.class, decompiler);
        this.extractInitialPatches = Tasks.unzip(project, GROUP_NAME, "extractInitialPatches", this.initialPatches, ext.getVersionCacheDirectory().dir("initial_patches"));
        this.prepareApplyInitialDiffs = Tasks.unzip(project, GROUP_NAME, "prepareApplyInitialDiffs", this.decompileSrg.flatMap(Decompile::getDecompiledJar), ext.getLocalCacheDirectory().dir("decompileSrg/files"));
        this.prepareCleanRecompile = Tasks.unzip(project, GROUP_NAME, "prepareCleanRecompile", this.decompileSrg.flatMap(Decompile::getDecompiledJar), ext.getLocalCacheDirectory().dir("cleanSrg/raw"));
        this.applyInitialDiffs = Tasks.of(project, GROUP_NAME, "applyInitialDiffs", ApplyDiffs.class);
        this.applyCleanInitialDiffs = Tasks.of(project, GROUP_NAME, "applyCleanInitialDiffs", ApplyDiffs.class);
        this.runReobfSrgClient = Tasks.of(project, GROUP_NAME, "runReobfSrgClient", RunMinecraft.class);
        this.runReobfSrgServer = Tasks.of(project, GROUP_NAME, "runReobfSrgServer", RunMinecraft.class);
        this.extractMcpMappings = Tasks.unzip(project, GROUP_NAME, "extractMcpMappings", this.mcpMappings, ext.getVersionCacheDirectory().dir("mcp_mappings"));
        this.remapSrg2Mcp = Tasks.of(project, GROUP_NAME, "remapSrg2Mcp", RemapSrg2Mcp.class);
        this.srgJar = Tasks.jar(project, GROUP_NAME, "srgSourceJar", this.srgSource.map(SourceSet::getOutput), ext.getLocalCacheDirectory().file("sourceSets/srg/srg.jar"));
        this.cleanSrgJar = Tasks.jar(project, GROUP_NAME, "cleanSrgSourceJar", this.cleanSrgSource.map(SourceSet::getOutput), ext.getLocalCacheDirectory().file("sourceSets/cleanSrg/clean.jar"));
        this.genBinPatches = Tasks.of(project, GROUP_NAME, "genBinPatches", GenerateBinPatches.class);
        this.runMcpClient = Tasks.of(project, GROUP_NAME, "runMcpClient", RunMinecraft.class);
        this.runMcpServer = Tasks.of(project, GROUP_NAME, "runMcpServer", RunMinecraft.class);

        SourceSets.linkSource(this.cleanSrgSource, ext.getLocalCacheDirectory().dir("sourceSets/cleanSrg/sources"));
        SourceSets.extendFromConfiguration(project, this.cleanSrgSource, vanilla.vanillaConfig);
        SourceSets.linkSource(this.srgSource, ext.getLocalCacheDirectory().dir("sourceSets/srg/sources"));
        SourceSets.extendFromConfiguration(project, this.srgSource, vanilla.vanillaConfig);
        SourceSets.linkSource(this.mcpSource, ext.getLocalCacheDirectory().dir("sourceSets/mcp/sources"));
        SourceSets.extendFromConfiguration(project, this.mcpSource, vanilla.vanillaConfig);
        this.cleanSrgSource.configure(sourceSet -> {
            Tasks.<JavaCompile>named(project, sourceSet.getCompileJavaTaskName()).configure(task -> {
                task.dependsOn(this.applyCleanInitialDiffs);
                task.setGroup(GROUP_NAME);
            });
        });
        this.srgSource.configure(sourceSet -> {
            Tasks.<JavaCompile>named(project, sourceSet.getCompileJavaTaskName()).configure(task -> {
                task.dependsOn(this.applyInitialDiffs);
                task.setGroup(GROUP_NAME);
            });
        });
        this.mcpSource.configure(sourceSet -> {
            Tasks.<JavaCompile>named(project, sourceSet.getCompileJavaTaskName()).configure(task -> {
                task.dependsOn(this.remapSrg2Mcp);
                task.setGroup(GROUP_NAME);
            });
            Tasks.jar(project, GROUP_NAME, sourceSet.getJarTaskName(), sourceSet.getOutput(), ext.getLocalCacheDirectory().file("sourceSets/mcp/mcp.jar"));
        });

        this.splitClientJar.configure(task -> {
            task.dependsOn(vanilla.downloadClientJar, this.extractMcpConfig);

            task.getSourceJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(ext.getVersionCacheDirectory().map(d -> d.file("client-slim.jar")));
            task.getExtraJar().set(ext.getVersionCacheDirectory().map(d -> d.file("client-extra.jar")));
        });
        this.splitServerJar.configure(task -> {
            task.dependsOn(vanilla.downloadServerJar, this.extractMcpConfig);

            task.getSourceJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(ext.getVersionCacheDirectory().map(d -> d.file("server-slim.jar")));
            task.getExtraJar().set(ext.getVersionCacheDirectory().map(d -> d.file("server-extra.jar")));
        });
        // TODO: RenameMappings TSRG => TSRG2 by using `static_methods.txt` and inserting into srgutils' IMethod metadata when loading
        this.mergeJars.configure(task -> {
            task.dependsOn(this.splitClientJar, this.splitServerJar);

            task.getClientJar().value(this.splitClientJar.flatMap(SplitJar::getSlimJar));
            task.getServerJar().value(this.splitServerJar.flatMap(SplitJar::getSlimJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getMinecraftVersion().set("1.12.2");
            task.getMergedJar().set(mcpDir.map(d -> d.file("merged.jar")));
        });
        this.remapNotch2Srg.configure(task -> {
            task.dependsOn(this.mergeJars);

            task.getInput().set(this.mergeJars.flatMap(MergeJars::getMergedJar));
            task.getMap().from(srgMapping);
            task.getLibraries().from(vanilla.vanillaConfig);
        });
        this.injectMetadata.configure(task -> {
            task.dependsOn(this.remapNotch2Srg);

            task.getLogFile().convention(ext.getLocalCacheDirectory().file("injectMetadata/mcinjector.log"));
            task.getSrgJar().set(this.remapNotch2Srg.flatMap(RenameJar::getOutput));
            task.getAccessFile().set(mcpConfigDir.map(dir -> dir.file("access.txt")));
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getExceptionsFile().set(mcpConfigDir.map(dir -> dir.file("exceptions.txt")));
            task.getInjectedJar().set(new File(task.getWorkingDir(), "injected.jar"));
        });
        this.runSrgClient.configure(task -> {
            task.dependsOn(this.injectMetadata);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().dir("assets"));
            task.classpath(this.injectMetadata.map(InjectMetadata::getInjectedJar), vanilla.vanillaConfig, this.splitClientJar.map(SplitJar::getExtraJar));
        });
        this.runSrgServer.configure(task -> {
            task.dependsOn(this.injectMetadata);

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(this.injectMetadata.map(InjectMetadata::getInjectedJar), vanilla.vanillaConfig, this.splitServerJar.map(SplitJar::getExtraJar));
        });
        this.decompileSrg.configure(task -> {
            task.dependsOn(this.injectMetadata);

            task.getJavaLauncher().convention(Providers.javaLauncher(project, 25));
            task.getLogFile().convention(ext.getLocalCacheDirectory().file("decompileSrg/decompile.log"));
            task.getCompiledJar().value(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getDecompiledJar().set(new File(task.getWorkingDir(), "decompiled.jar"));
        });
        this.prepareApplyInitialDiffs.configure(task -> task.dependsOn(this.decompileSrg));
        this.prepareCleanRecompile.configure(task -> task.dependsOn(this.decompileSrg));
        this.applyInitialDiffs.configure(task -> {
            task.dependsOn(this.decompileSrg);

            task.getOriginalDirectory().fileProvider(this.prepareApplyInitialDiffs.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(this.extractInitialPatches.map(Copy::getDestinationDir));
            // task.getInPlace().set(true);
            task.getModifiedDirectory().fileProvider(SourceSets.source(this.srgSource));
        });
        this.applyCleanInitialDiffs.configure(task -> {
            task.dependsOn(this.prepareCleanRecompile, this.extractInitialPatches);

            task.getOriginalDirectory().fileProvider(this.prepareCleanRecompile.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(this.extractInitialPatches.map(Copy::getDestinationDir));
            task.getModifiedDirectory().fileProvider(SourceSets.source(this.cleanSrgSource));
        });
        this.runReobfSrgClient.configure(task -> {
            task.dependsOn(SourceSets.compile(this.srgSource));

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().dir("assets"));
            task.classpath(SourceSets.classes(this.srgSource), vanilla.vanillaConfig, this.splitClientJar.map(SplitJar::getExtraJar));
        });
        this.runReobfSrgServer.configure(task -> {
            task.dependsOn(SourceSets.compile(this.srgSource));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.srgSource), vanilla.vanillaConfig, this.splitServerJar.map(SplitJar::getExtraJar));
        });
        this.remapSrg2Mcp.configure(task -> {
            task.dependsOn(this.applyInitialDiffs);

            task.getSrgSource().set(this.applyInitialDiffs.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.getMethodMappings().from(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().from(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "fields.csv")));
            task.getParameterMappings().from(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "params.csv")));
            task.getMcpSource().fileProvider(SourceSets.source(this.mcpSource));
        });
        this.genBinPatches.configure(task -> {
            task.dependsOn(this.cleanSrgJar, this.srgJar);

            task.getOriginalJar().set(this.cleanSrgJar.flatMap(Jar::getArchiveFile));
            task.getModifiedJar().set(this.srgJar.flatMap(Jar::getArchiveFile));
            task.getBinpatches().set(ext.getLocalCacheDirectory().file("binpatches.bin"));
        });
        this.runMcpClient.configure(task -> {
            task.dependsOn(SourceSets.compile(this.mcpSource));

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.MCP);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().dir("assets"));
            task.classpath(SourceSets.classes(this.mcpSource), vanilla.vanillaConfig, this.splitClientJar.map(SplitJar::getExtraJar));
        });
        this.runMcpServer.configure(task -> {
            task.dependsOn(SourceSets.compile(this.mcpSource));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.MCP);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.mcpSource), vanilla.vanillaConfig, this.splitServerJar.map(SplitJar::getExtraJar));
        });
    }

    public void afterEvaluate(Project project, CleanroomExtension ext, VanillaTasks vanilla) {
        if (ext.getDevelopInitialPatches().get()) {
            var initial = ext.getPatchDev().register("initial", env -> {
                env.getSource().set(this.decompileSrg.flatMap(Decompile::getDecompiledJar).map(RegularFile::getAsFile));
                env.dependsOn("decompileSrg");
            });
            SourceSets.extendFromConfiguration(project, initial.get().getSourceSet(), vanilla.vanillaConfig);
        }
    }

}
