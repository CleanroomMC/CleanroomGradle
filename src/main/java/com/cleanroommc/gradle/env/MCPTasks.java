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
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.Locale;
import java.util.List;

public final class MCPTasks {

    private static final String GROUP_NAME = "MCP";
    public static final String DEFAULT_INITIAL_PATCHES = "com.cleanroommc:initial-patches:1.2.0";

    public final NamedDomainObjectProvider<Configuration> initialPatches;
    public final Provider<Directory> srgSourceDirectory, mcpSourceDirectory;
    public final TaskProvider<Copy> prepareMcpInjectedSources, extractInitialPatches, prepareApplyInitialDiffs;
    public final TaskProvider<SplitJar> splitClientJar, splitServerJar;
    public final TaskProvider<InjectMetadata> injectMetadata;
    public final TaskProvider<Decompile> decompileSrg;
    public final TaskProvider<ApplyDiffs> applyInitialDiffs;
    public final TaskProvider<RemapSrg2Mcp> remapSrg2Mcp;
    public final TaskProvider<ImportMcpNames> importMcpNames;
    public final MinecraftJarPipeline jars;
    public final IntermediateProcessor.Discard discardInjectedJar;

    public TaskProvider<CheckSAS> checkSAS;
    public IntermediateProcessor.Discard discardCheckSAS, discardUniversalSrg;
    public TaskProvider<RunMinecraft> runSrgClient, runSrgServer;

    private TaskProvider<ApplySAS> applySAS;

    public MCPTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, MappingsExtension names,
                    VanillaTasks vanilla, McpMappings mappings, IntermediateProcessor intermediates) {
        this.initialPatches = Objects.archive(project, "initialPatches", "Patches applied to freshly decompiled SRG Minecraft.", DEFAULT_INITIAL_PATCHES);

        var mcpDir = caches.getVersionDirectory().dir("mcp");

        var spec = new MinecraftJarPipeline.Spec();
        spec.bindClientJar = property -> property.fileProvider(vanilla.downloadClientJar.map(Download::getDest));
        spec.bindServerJar = property -> property.fileProvider(vanilla.downloadServerJar.map(Download::getDest));
        spec.srgMapping = mappings.joinedSrg;
        spec.access = mappings.access;
        spec.constructors = mappings.constructors;
        spec.exceptions = mappings.exceptions;
        spec.minecraftVersion = vanilla.minecraftVersion;
        spec.libraries = vanilla.vanillaConfig;
        spec.clientSlim = caches.getVersionDirectory().map(d -> d.file(MinecraftJarPipeline.CLIENT_SLIM_JAR));
        spec.clientExtra = caches.getVersionDirectory().map(d -> d.file(MinecraftJarPipeline.CLIENT_EXTRA_JAR));
        spec.serverSlim = caches.getVersionDirectory().map(d -> d.file(MinecraftJarPipeline.SERVER_SLIM_JAR));
        spec.serverExtra = caches.getVersionDirectory().map(d -> d.file(MinecraftJarPipeline.SERVER_EXTRA_JAR));
        spec.mergedJar = mcpDir.map(d -> d.file(MinecraftJarPipeline.MERGED_JAR));
        spec.injectedJar = caches.getLocalDirectory().file("injectMetadata/injected.jar");
        this.jars = MinecraftJarPipeline.register(project, caches, spec);
        this.splitClientJar = this.jars.splitClient;
        this.splitServerJar = this.jars.splitServer;
        this.injectMetadata = this.jars.inject;

        var decompiler = ToolConfigs.get(project, "decompiler");
        this.srgSourceDirectory = caches.getLocalDirectory().dir("sourceSets/srg/sources");
        this.mcpSourceDirectory = caches.getLocalDirectory().dir("sourceSets/mcp/sources");

        this.prepareMcpInjectedSources = Tasks.register(project, "prepareMcpInjectedSources", Copy.class);
        this.decompileSrg = Tasks.tool(project, caches.getLocalDirectory(), "decompileSrg", Decompile.class, decompiler);
        this.extractInitialPatches = Tasks.unzip(project, "extractInitialPatches", this.initialPatches, caches.getVersionDirectory().dir("initial_patches"));
        this.prepareApplyInitialDiffs = Tasks.unzip(project, "prepareApplyInitialDiffs", this.decompileSrg.flatMap(Decompile::getDecompiledJar), caches.getLocalDirectory().dir("decompileSrg/files"));
        this.applyInitialDiffs = Tasks.register(project, "applyInitialDiffs", ApplyDiffs.class);
        this.remapSrg2Mcp = Tasks.register(project, "remapSrg2Mcp", RemapSrg2Mcp.class);
        this.importMcpNames = Tasks.register(project, "importMcpNames", ImportMcpNames.class);
        Tasks.group(GROUP_NAME, this.importMcpNames);

        this.prepareMcpInjectedSources.configure(task -> {
            task.from(mappings.mcpConfigDirectory.map(dir -> dir.file("inject/mcp/MethodsReturnNonnullByDefault.java")), copySpec -> {
                copySpec.into("mcp");
                copySpec.rename($ -> "MethodsReturnNonnullByDefault.java");
            });
            task.into(caches.getLocalDirectory().dir("sourceSets/injected/sources"));
        });
        this.decompileSrg.configure(task -> {
            task.getJavaLauncher().convention(Providers.javaLauncher(project));
            task.getLogFile().convention(caches.getLocalDirectory().file("decompileSrg/decompile.log"));
            task.getCompiledJar().value(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getDecompiledJar().set(caches.getLocalDirectory().file("decompileSrg/decompiled.jar"));
        });
        this.applyInitialDiffs.configure(task -> {
            task.getOriginalDirectory().fileProvider(this.prepareApplyInitialDiffs.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(this.extractInitialPatches.map(Copy::getDestinationDir));
            task.getModifiedDirectory().set(this.srgSourceDirectory);
        });
        this.remapSrg2Mcp.configure(task -> {
            task.getSrgSource().set(this.applyInitialDiffs.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.getMethodMappings().from(mappings.methodMappings);
            task.getFieldMappings().from(mappings.fieldMappings);
            task.getParameterMappings().from(mappings.parameterMappings);
            task.getTinyMappings().fileProvider(mappings.tinyFileWhenPresent);
            task.getNamesId().set(mappings.activeNamesId);
            task.getMcpSource().set(this.mcpSourceDirectory);
        });
        this.importMcpNames.configure(task -> {
            task.getSrgJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getMcpNames().from(mappings.mcpMappings);
            task.getConstructorsFile().set(mappings.constructors);
            task.getNamesDirectoryConfigured().set(names.getNamesDirectory().map(dir -> true).orElse(false));
            task.getTinyFile().set(names.getNamesDirectory().file(MappingsExtension.NAMES_FILE)
                    .orElse(caches.getLocalDirectory().file("names/" + MappingsExtension.NAMES_FILE)));
        });

        this.discardInjectedJar = intermediates.discardAfterAll("discardInjectedJar",
                List.of(this.decompileSrg, this.importMcpNames),
                this.injectMetadata.flatMap(InjectMetadata::getInjectedJar)
        );
        intermediates.discardAfter(this.prepareApplyInitialDiffs, this.decompileSrg.flatMap(Decompile::getDecompiledJar));
    }

    public void configureIntermediateRuns(Project project, CachesExtension caches, MinecraftExtension minecraft,
                                          LoaderExtension loader, VanillaTasks vanilla,
                                          IntermediateProcessor intermediates) {
        if (!loader.getIntermediateRuns().get()) {
            return;
        }
        registerIntermediateRuns(project, caches, minecraft, vanilla,
                project.getGradle().getStartParameter().isOffline());
        intermediates.after(this.discardInjectedJar, this.runSrgClient, this.runSrgServer);
        intermediates.after(this.discardUniversalSrg,
                stripForSrgRun(project, caches, vanilla, intermediates, this.applySAS,
                        this.runSrgClient, Side.CLIENT, this.splitClientJar),
                stripForSrgRun(project, caches, vanilla, intermediates, this.applySAS,
                        this.runSrgServer, Side.SERVER, this.splitServerJar));
    }

    private void registerIntermediateRuns(Project project, CachesExtension caches, MinecraftExtension minecraft,
                                          VanillaTasks vanilla, boolean offline) {
        var srgSource = SourceSets.internal(project, "srgSource");
        var mcpSource = SourceSets.internal(project, "mcpSource");
        SourceSets.linkSource(srgSource, this.srgSourceDirectory);
        SourceSets.extendFromConfiguration(project, srgSource, vanilla.vanillaConfig);
        SourceSets.linkSource(mcpSource, this.mcpSourceDirectory);
        SourceSets.extendFromConfiguration(project, mcpSource, vanilla.vanillaConfig);
        srgSource.configure(sourceSet -> project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .configure(task -> {
                    task.dependsOn(this.applyInitialDiffs, this.prepareMcpInjectedSources);
                    task.source(this.prepareMcpInjectedSources.map(Copy::getDestinationDir));
                }));
        mcpSource.configure(sourceSet -> project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .configure(task -> {
                    task.dependsOn(this.remapSrg2Mcp, this.prepareMcpInjectedSources);
                    task.source(this.prepareMcpInjectedSources.map(Copy::getDestinationDir));
                }));

        this.runSrgClient = stageRun(project, "runSrgClient", Side.CLIENT, Environment.SRG, caches, minecraft, vanilla, offline);
        this.runSrgServer = stageRun(project, "runSrgServer", Side.SERVER, Environment.SRG, caches, minecraft, vanilla, offline);
        var runReobfSrgClient = stageRun(project, "runReobfSrgClient", Side.CLIENT, Environment.REOBF_SRG, caches, minecraft, vanilla, offline);
        var runReobfSrgServer = stageRun(project, "runReobfSrgServer", Side.SERVER, Environment.REOBF_SRG, caches, minecraft, vanilla, offline);
        var runMcpClient = stageRun(project, "runMcpClient", Side.CLIENT, Environment.MCP, caches, minecraft, vanilla, offline);
        var runMcpServer = stageRun(project, "runMcpServer", Side.SERVER, Environment.MCP, caches, minecraft, vanilla, offline);

        this.runSrgClient.configure(task -> task.classpath(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar),
                vanilla.vanillaConfig, this.splitClientJar.flatMap(SplitJar::getExtraJar)));
        this.runSrgServer.configure(task -> task.classpath(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar),
                vanilla.vanillaConfig, this.splitServerJar.flatMap(SplitJar::getExtraJar)));
        runReobfSrgClient.configure(task -> {
            task.dependsOn(SourceSets.compile(srgSource));
            task.classpath(SourceSets.classes(srgSource), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        runReobfSrgServer.configure(task -> {
            task.dependsOn(SourceSets.compile(srgSource));
            task.classpath(SourceSets.classes(srgSource), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });
        runMcpClient.configure(task -> {
            task.dependsOn(SourceSets.compile(mcpSource));
            task.classpath(SourceSets.classes(mcpSource), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        runMcpServer.configure(task -> {
            task.dependsOn(SourceSets.compile(mcpSource));
            task.classpath(SourceSets.classes(mcpSource), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });
    }

    private TaskProvider<RunMinecraft> stageRun(Project project, String name, Side side, Environment environment,
                                                CachesExtension caches, MinecraftExtension minecraft,
                                                VanillaTasks vanilla, boolean offline) {
        var run = Tasks.register(project, name, RunMinecraft.class);
        run.configure(task -> {
            task.setGroup(GROUP_NAME);
            if (side.isClient()) {
                task.dependsOn(vanilla.downloadAssets);
            }
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(side);
            task.getEnv().set(environment);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
        });
        return run;
    }

    public void configureLoaderPipeline(Project project, CachesExtension caches, LoaderExtension loader,
                                        VanillaTasks vanilla, IntermediateProcessor intermediates) {
        var installerTools = ToolConfigs.get(project, "installertools");
        var accessTransformerTool = ToolConfigs.get(project, "accesstransformer");

        var extractInheritance = Tasks.tool(project, caches.getLocalDirectory(), "extractInheritance", ExtractInheritance.class, installerTools);
        this.checkSAS = Tasks.register(project, "checkSAS", CheckSAS.class);
        var applySAS = Tasks.register(project, "applySAS", ApplySAS.class);
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
        accessTransformSrgJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getAccessTransformers().from(loader.getAccessTransformers());
            task.getOutputJar().set(caches.getLocalDirectory().file("sas/srg-at.jar"));
        });

        this.decompileSrg.configure(task -> task.getCompiledJar().set(accessTransformSrgJar.flatMap(AccessTransform::getOutputJar)));
        this.importMcpNames.configure(task -> task.getSrgJar().set(accessTransformSrgJar.flatMap(AccessTransform::getOutputJar)));

        this.applySAS = applySAS;

        intermediates.after(this.discardInjectedJar, extractInheritance, applySAS);
        intermediates.discardAfter(checkSAS, extractInheritance.flatMap(ExtractInheritance::getOutput));
        this.discardCheckSAS = intermediates.discardAfter(applySAS, checkSAS.flatMap(CheckSAS::getOutput));
        this.discardUniversalSrg = intermediates.discardAfter(accessTransformSrgJar,
                applySAS.flatMap(ApplySAS::getOutputJar));
        intermediates.discardAfterAll("discardAccessTransformedSrgJar",
                List.of(this.decompileSrg, this.importMcpNames),
                accessTransformSrgJar.flatMap(AccessTransform::getOutputJar));
    }

    private TaskProvider<StripSideOnlyJar> stripForSrgRun(Project project, CachesExtension caches, VanillaTasks vanilla,
                                                          IntermediateProcessor intermediates,
                                                          TaskProvider<ApplySAS> applySAS,
                                                          TaskProvider<RunMinecraft> run, Side side,
                                                          TaskProvider<SplitJar> split) {
        var sideName = side.name().toLowerCase(Locale.ENGLISH);
        var strip = Tasks.register(project, "stripSrg" + StringUtils.capitalize(sideName) + "Jar", StripSideOnlyJar.class);
        strip.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getTargetSide().set(side);
            task.getOutputJar().set(caches.getLocalDirectory().file("sas/" + sideName + "-srg.jar"));
        });
        var objects = project.getObjects();
        run.configure(task -> task.setClasspath(objects.fileCollection().from(
                strip.flatMap(StripSideOnlyJar::getOutputJar),
                vanilla.vanillaConfig,
                split.flatMap(SplitJar::getExtraJar))));
        intermediates.discardAfter(run, strip.flatMap(StripSideOnlyJar::getOutputJar));
        return strip;
    }

    public void configureInitialPatches(Project project, CachesExtension caches, PatchesExtension patches,
                                        VanillaTasks vanilla, McpMappings mappings) {
        mappings.configureInitialPatches(project, patches, vanilla, this.prepareApplyInitialDiffs, this.applyInitialDiffs);
    }

}
