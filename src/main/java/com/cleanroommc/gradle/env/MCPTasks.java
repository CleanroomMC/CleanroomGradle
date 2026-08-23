package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.LoaderExtension;
import com.cleanroommc.gradle.api.ext.MappingsExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.ext.PatchesExtension;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.AccessTransform;
import com.cleanroommc.gradle.api.task.mcp.InjectMetadata;
import com.cleanroommc.gradle.api.task.mcp.RemapSrg2Mcp;
import com.cleanroommc.gradle.api.task.mcp.SplitJar;
import com.cleanroommc.gradle.api.task.names.ImportMcpNames;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.task.sas.ApplySAS;
import com.cleanroommc.gradle.api.task.sas.CheckSAS;
import com.cleanroommc.gradle.api.task.sas.ExtractInheritance;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.List;

public final class MCPTasks {

    private static final String GROUP_NAME = "MCP";

    public final NamedDomainObjectProvider<Configuration> initialPatches;
    public final NamedDomainObjectProvider<SourceSet> srgSource, mcpSource;
    public final TaskProvider<Copy> prepareMcpInjectedSources, extractInitialPatches, prepareApplyInitialDiffs;
    public final TaskProvider<SplitJar> splitClientJar, splitServerJar;
    public final TaskProvider<InjectMetadata> injectMetadata;
    public final TaskProvider<RunMinecraft> runSrgClient, runSrgServer, runReobfSrgClient, runReobfSrgServer, runMcpClient, runMcpServer;
    public final TaskProvider<Decompile> decompileSrg;
    public final TaskProvider<ApplyDiffs> applyInitialDiffs;
    public final TaskProvider<RemapSrg2Mcp> remapSrg2Mcp;
    public final TaskProvider<ImportMcpNames> importMcpNames;
    public final MinecraftJarPipeline jars;
    public final TaskProvider<Delete> discardInjectedJar;

    public MCPTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, MappingsExtension names,
                    VanillaTasks vanilla, McpMappings mappings, IntermediateProcessor intermediates) {
        this.initialPatches = Objects.config(project, "initialPatches", "com.cleanroommc:initial-patches:1.2.0");

        var srgMapping = caches.getVersionDirectory().file("mcp_config/config/joined.tsrg");
        var mcpConfigDir = caches.getVersionDirectory().dir("mcp_config/config");
        var mcpDir = caches.getVersionDirectory().dir("mcp");
        var offline = project.getGradle().getStartParameter().isOffline();

        var spec = new MinecraftJarPipeline.Spec();
        spec.splitClientName = "splitClientJar";
        spec.splitServerName = "splitServerJar";
        spec.mergeName = "mergeJars";
        spec.remapName = "remapNotch2Srg";
        spec.injectName = "injectMetadata";
        spec.bindClientJar = property -> property.fileProvider(vanilla.downloadClientJar.map(Download::getDest));
        spec.bindServerJar = property -> property.fileProvider(vanilla.downloadServerJar.map(Download::getDest));
        spec.srgMapping = srgMapping;
        spec.mcpConfigDir = mcpConfigDir;
        spec.minecraftVersion = vanilla.minecraftVersion;
        spec.libraries = vanilla.vanillaConfig;
        spec.extractMcpConfig = mappings.extractMcpConfig;
        spec.clientSlim = caches.getVersionDirectory().map(d -> d.file("client-slim.jar"));
        spec.clientExtra = caches.getVersionDirectory().map(d -> d.file("client-extra.jar"));
        spec.serverSlim = caches.getVersionDirectory().map(d -> d.file("server-slim.jar"));
        spec.serverExtra = caches.getVersionDirectory().map(d -> d.file("server-extra.jar"));
        spec.mergedJar = mcpDir.map(d -> d.file("merged.jar"));
        spec.injectedJar = caches.getLocalDirectory().file("injectMetadata/injected.jar");
        this.jars = MinecraftJarPipeline.register(project, caches, spec);
        this.splitClientJar = this.jars.splitClient;
        this.splitServerJar = this.jars.splitServer;
        this.injectMetadata = this.jars.inject;

        var decompiler = ToolConfigs.get(project, "decompiler");
        this.srgSource = SourceSets.internal(project, "srgSource");
        this.mcpSource = SourceSets.internal(project, "mcpSource");

        this.prepareMcpInjectedSources = Tasks.register(project, "prepareMcpInjectedSources", Copy.class);
        this.runSrgClient = Tasks.register(project, "runSrgClient", RunMinecraft.class);
        this.runSrgServer = Tasks.register(project, "runSrgServer", RunMinecraft.class);
        this.decompileSrg = Tasks.tool(project, caches.getLocalDirectory(), "decompileSrg", Decompile.class, decompiler);
        this.extractInitialPatches = Tasks.unzip(project, "extractInitialPatches", this.initialPatches, caches.getVersionDirectory().dir("initial_patches"));
        this.prepareApplyInitialDiffs = Tasks.unzip(project, "prepareApplyInitialDiffs", this.decompileSrg.flatMap(Decompile::getDecompiledJar), caches.getLocalDirectory().dir("decompileSrg/files"));
        this.applyInitialDiffs = Tasks.register(project, "applyInitialDiffs", ApplyDiffs.class);
        this.runReobfSrgClient = Tasks.register(project, "runReobfSrgClient", RunMinecraft.class);
        this.runReobfSrgServer = Tasks.register(project, "runReobfSrgServer", RunMinecraft.class);
        this.remapSrg2Mcp = Tasks.register(project, "remapSrg2Mcp", RemapSrg2Mcp.class);
        this.importMcpNames = Tasks.register(project, "importMcpNames", ImportMcpNames.class);
        this.runMcpClient = Tasks.register(project, "runMcpClient", RunMinecraft.class);
        this.runMcpServer = Tasks.register(project, "runMcpServer", RunMinecraft.class);
        Tasks.group(GROUP_NAME, this.importMcpNames, this.runSrgClient, this.runSrgServer,
                this.runReobfSrgClient, this.runReobfSrgServer, this.runMcpClient, this.runMcpServer);

        SourceSets.linkSource(this.srgSource, caches.getLocalDirectory().dir("sourceSets/srg/sources"));
        SourceSets.extendFromConfiguration(project, this.srgSource, vanilla.vanillaConfig);
        SourceSets.linkSource(this.mcpSource, caches.getLocalDirectory().dir("sourceSets/mcp/sources"));
        SourceSets.extendFromConfiguration(project, this.mcpSource, vanilla.vanillaConfig);
        this.srgSource.configure(sourceSet -> {
            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
                task.dependsOn(this.applyInitialDiffs, this.prepareMcpInjectedSources);
                task.source(this.prepareMcpInjectedSources.map(Copy::getDestinationDir));
            });
        });
        this.mcpSource.configure(sourceSet -> {
            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task -> {
                task.dependsOn(this.remapSrg2Mcp, this.prepareMcpInjectedSources);
                task.source(this.prepareMcpInjectedSources.map(Copy::getDestinationDir));
            });
        });

        this.prepareMcpInjectedSources.configure(task -> {
            task.dependsOn(mappings.extractMcpConfig);
            task.from(mcpConfigDir.map(dir -> dir.file("inject/mcp/MethodsReturnNonnullByDefault.java")), copySpec -> {
                copySpec.into("mcp");
                copySpec.rename($ -> "MethodsReturnNonnullByDefault.java");
            });
            task.into(caches.getLocalDirectory().dir("sourceSets/injected/sources"));
        });
        this.runSrgClient.configure(task -> {
            task.dependsOn(vanilla.downloadAssets);
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.SRG);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        this.runSrgServer.configure(task -> {
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.SRG);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });
        this.decompileSrg.configure(task -> {
            task.getJavaLauncher().convention(Providers.javaLauncher(project, 25));
            task.getLogFile().convention(caches.getLocalDirectory().file("decompileSrg/decompile.log"));
            task.getCompiledJar().value(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getDecompiledJar().set(caches.getLocalDirectory().file("decompileSrg/decompiled.jar"));
        });
        this.applyInitialDiffs.configure(task -> {
            task.getOriginalDirectory().fileProvider(this.prepareApplyInitialDiffs.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(this.extractInitialPatches.map(Copy::getDestinationDir));
            task.getModifiedDirectory().fileProvider(SourceSets.source(this.srgSource));
        });
        this.runReobfSrgClient.configure(task -> {
            task.dependsOn(SourceSets.compile(this.srgSource), vanilla.downloadAssets);
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.srgSource), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        this.runReobfSrgServer.configure(task -> {
            task.dependsOn(SourceSets.compile(this.srgSource));
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.srgSource), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });
        this.remapSrg2Mcp.configure(task -> {
            task.getSrgSource().set(this.applyInitialDiffs.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.getMethodMappings().from(mappings.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().from(mappings.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "fields.csv")));
            task.getParameterMappings().from(mappings.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "params.csv")));
            task.getTinyMappings().fileProvider(mappings.tinyFileWhenPresent);
            task.getNamesId().set(mappings.activeNamesId);
            task.getMcpSource().fileProvider(SourceSets.source(this.mcpSource));
        });
        this.importMcpNames.configure(task -> {
            task.dependsOn(mappings.extractMcpConfig);
            task.getSrgJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getMcpNames().from(mappings.mcpMappings);
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getNamesDirectoryConfigured().set(names.getNamesDirectory().map(dir -> true).orElse(false));
            task.getTinyFile().set(names.getNamesDirectory().file("mappings.tiny")
                    .orElse(caches.getLocalDirectory().file("names/mappings.tiny")));
        });
        this.runMcpClient.configure(task -> {
            task.dependsOn(SourceSets.compile(this.mcpSource), vanilla.downloadAssets);
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.MCP);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.mcpSource), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        this.runMcpServer.configure(task -> {
            task.dependsOn(SourceSets.compile(this.mcpSource));
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.MCP);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.mcpSource), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });

        this.discardInjectedJar = intermediates.discardAfterAll("discardInjectedJar",
                List.of(this.decompileSrg, this.importMcpNames, this.runSrgClient, this.runSrgServer),
                this.injectMetadata.flatMap(InjectMetadata::getInjectedJar)
        );
        intermediates.discardAfter(this.prepareApplyInitialDiffs, this.decompileSrg.flatMap(Decompile::getDecompiledJar));
    }

    public void configureLoaderPipeline(Project project, CachesExtension caches, LoaderExtension loader,
                                        VanillaTasks vanilla, IntermediateProcessor intermediates) {
        var installerTools = ToolConfigs.get(project, "installertools");
        var accessTransformerTool = ToolConfigs.get(project, "accesstransformer");

        var extractInheritance = Tasks.tool(project, caches.getLocalDirectory(), "extractInheritance", ExtractInheritance.class, installerTools);
        var checkSAS = Tasks.register(project, "checkSAS", CheckSAS.class);
        var applySAS = Tasks.register(project, "applySAS", ApplySAS.class);
        var stripSrgClientJar = Tasks.register(project, "stripSrgClientJar", StripSideOnlyJar.class);
        var stripSrgServerJar = Tasks.register(project, "stripSrgServerJar", StripSideOnlyJar.class);
        var accessTransformSrgJar = Tasks.tool(project, caches.getLocalDirectory(), "accessTransformSrgJar", AccessTransform.class, accessTransformerTool);

        extractInheritance.configure(task -> {
            task.getInputJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getOutput().set(caches.getLocalDirectory().file("sas/inheritance.json"));
        });
        checkSAS.configure(task -> {
            task.getInheritance().set(extractInheritance.flatMap(ExtractInheritance::getOutput));
            task.getSideAnnotationStrippers().from(loader.getSideAnnotationStrippers());
            task.getOutput().set(caches.getLocalDirectory().file("sas/normalized.sas"));
        });
        applySAS.configure(task -> {
            task.getInputJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getSideAnnotationStrippers().from(checkSAS.flatMap(CheckSAS::getOutput));
            task.getOutputJar().set(caches.getLocalDirectory().file("sas/universal-srg.jar"));
        });
        stripSrgClientJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getTargetSide().set(Side.CLIENT);
            task.getOutputJar().set(caches.getLocalDirectory().file("sas/client-srg.jar"));
        });
        stripSrgServerJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getTargetSide().set(Side.SERVER);
            task.getOutputJar().set(caches.getLocalDirectory().file("sas/server-srg.jar"));
        });
        accessTransformSrgJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getAccessTransformers().from(loader.getAccessTransformers());
            task.getOutputJar().set(caches.getLocalDirectory().file("sas/srg-at.jar"));
        });

        this.decompileSrg.configure(task -> {
            task.getCompiledJar().set(accessTransformSrgJar.flatMap(AccessTransform::getOutputJar));
        });
        this.importMcpNames.configure(task -> {
            task.getSrgJar().set(accessTransformSrgJar.flatMap(AccessTransform::getOutputJar));
        });
        var objects = project.getObjects();
        this.runSrgClient.configure(task -> {
            task.setClasspath(objects.fileCollection().from(
                    stripSrgClientJar.flatMap(StripSideOnlyJar::getOutputJar),
                    vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar)));
        });
        this.runSrgServer.configure(task -> {
            task.setClasspath(objects.fileCollection().from(
                    stripSrgServerJar.flatMap(StripSideOnlyJar::getOutputJar),
                    vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar)));
        });

        intermediates.after(this.discardInjectedJar, extractInheritance, applySAS);
        intermediates.discardAfter(checkSAS, extractInheritance.flatMap(ExtractInheritance::getOutput));
        intermediates.discardAfter(applySAS, checkSAS.flatMap(CheckSAS::getOutput));
        intermediates.discardAfterAll("discardUniversalSrg",
                List.of(accessTransformSrgJar, stripSrgClientJar, stripSrgServerJar),
                applySAS.flatMap(ApplySAS::getOutputJar)
        );
        intermediates.discardAfterAll("discardSrgAtJar",
                List.of(this.decompileSrg, this.importMcpNames),
                accessTransformSrgJar.flatMap(AccessTransform::getOutputJar)
        );
        intermediates.discardAfter(this.runSrgClient, stripSrgClientJar.flatMap(StripSideOnlyJar::getOutputJar));
        intermediates.discardAfter(this.runSrgServer, stripSrgServerJar.flatMap(StripSideOnlyJar::getOutputJar));
    }

    public void configureInitialPatches(Project project, CachesExtension caches, PatchesExtension patches,
                                        VanillaTasks vanilla, McpMappings mappings) {
        mappings.configureInitialPatches(project, caches, patches, vanilla, this.prepareApplyInitialDiffs, this.applyInitialDiffs);
    }

}
