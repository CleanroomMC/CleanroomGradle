package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.names.NamesSource;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.*;
import com.cleanroommc.gradle.api.task.names.ImportMcpNames;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.task.sas.ApplySAS;
import com.cleanroommc.gradle.api.task.sas.CheckSAS;
import com.cleanroommc.gradle.api.task.sas.ExtractInheritance;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.srgutils.IMappingFile;
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
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.List;

public final class MCPTasks {

    private static final String GROUP_NAME = "MCP";

    public final NamedDomainObjectProvider<Configuration> mcpConfig, initialPatches, mcpMappings;
    public final NamedDomainObjectProvider<SourceSet> srgSource, mcpSource;
    public final TaskProvider<Copy> extractMcpConfig, prepareMcpInjectedSources, extractInitialPatches, prepareApplyInitialDiffs, extractMcpMappings;
    public final TaskProvider<SplitJar> splitClientJar, splitServerJar;
    public final TaskProvider<MergeJars> mergeJars;
    public final TaskProvider<RenameJar> remapNotch2Srg;
    public final TaskProvider<InjectMetadata> injectMetadata;
    public final TaskProvider<RunMinecraft> runSrgClient, runSrgServer, runReobfSrgClient, runReobfSrgServer, runMcpClient, runMcpServer;
    public final TaskProvider<Decompile> decompileSrg;
    public final TaskProvider<ApplyDiffs> applyInitialDiffs;
    public final TaskProvider<RemapSrg2Mcp> remapSrg2Mcp;
    public final TaskProvider<ImportMcpNames> importMcpNames;
    public final TaskProvider<WriteMappings> writeSrg2Mcp;

    /** {@code MCP_VERSION} and {@code MCP_MAPPINGS} as the dev runtime expects them, e.g. {@code 20201025.185735} and {@code stable_39}. */
    public final Provider<String> mcpVersionId, mcpMappingsId;

    final Provider<File> tinyFileWhenPresent;
    final Provider<String> activeNamesId;
    private final Provider<String> mcpConfigVersion;

    public MCPTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla) {
        this.mcpConfig = Objects.config(project, "mcpConfig", "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
        this.initialPatches = Objects.config(project, "initialPatches", "com.cleanroommc:initial-patches:1.2.0");
        this.mcpMappings = Objects.config(project, "mcpMappings", "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");

        var tinyFile = ext.getNamesDirectory().file("mappings.tiny");
        this.tinyFileWhenPresent = tinyFile.map(RegularFile::getAsFile).filter(File::isFile);
        var mcpNamesId = this.mcpMappings.map(cfg -> {
            var dep = Objects.firstDependency(cfg);
            return NamesSource.mcpId(dep.getName(), dep.getVersion());
        });
        this.activeNamesId = this.tinyFileWhenPresent.map(NamesSource::tiny2Id).orElse(mcpNamesId);
        this.mcpConfigVersion = this.mcpConfig.map(cfg -> Objects.firstDependency(cfg).getVersion());
        this.mcpVersionId = this.mcpConfig.map(MCPTasks::deriveMcpVersion);
        this.mcpMappingsId = this.mcpMappings.map(MCPTasks::deriveMcpMappings);

        var mergeTool = Objects.toolConfig(project, "mergetool", "net.minecraftforge:mergetool:1.2.2");
        var metadataInjector = Objects.toolConfig(project, "mcinjector", "de.oceanlabs.mcp:mcinjector:3.7.3");
        var decompiler = Objects.toolConfig(project, "decompiler", "com.cleanroommc:cleanflower:1.0.0");

        this.srgSource = SourceSets.internal(project, "srgSource");
        this.mcpSource = SourceSets.internal(project, "mcpSource");

        var mcpDir = ext.getVersionCacheDirectory().dir("mcp");
        var mcpConfigDir = ext.getVersionCacheDirectory().dir("mcp_config/config");
        var srgMapping = ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg");

        this.extractMcpConfig = Tasks.unzip(project, "extractMcpConfig", this.mcpConfig, ext.getVersionCacheDirectory().dir("mcp_config"));
        this.prepareMcpInjectedSources = Tasks.register(project, "prepareMcpInjectedSources", Copy.class);
        this.splitClientJar = Tasks.register(project, "splitClientJar", SplitJar.class);
        this.splitServerJar = Tasks.register(project, "splitServerJar", SplitJar.class);
        this.mergeJars = Tasks.tool(project, ext.getLocalCacheDirectory(), "mergeJars", MergeJars.class, mergeTool);
        this.remapNotch2Srg = project.getTasks().register("remapNotch2Srg", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.injectMetadata = Tasks.tool(project, ext.getLocalCacheDirectory(), "injectMetadata", InjectMetadata.class, metadataInjector);
        this.runSrgClient = Tasks.register(project, "runSrgClient", RunMinecraft.class);
        this.runSrgServer = Tasks.register(project, "runSrgServer", RunMinecraft.class);
        this.decompileSrg = Tasks.tool(project, ext.getLocalCacheDirectory(), "decompileSrg", Decompile.class, decompiler);
        this.extractInitialPatches = Tasks.unzip(project, "extractInitialPatches", this.initialPatches, ext.getVersionCacheDirectory().dir("initial_patches"));
        this.prepareApplyInitialDiffs = Tasks.unzip(project, "prepareApplyInitialDiffs", this.decompileSrg.flatMap(Decompile::getDecompiledJar), ext.getLocalCacheDirectory().dir("decompileSrg/files"));
        this.applyInitialDiffs = Tasks.register(project, "applyInitialDiffs", ApplyDiffs.class);
        this.runReobfSrgClient = Tasks.register(project, "runReobfSrgClient", RunMinecraft.class);
        this.runReobfSrgServer = Tasks.register(project, "runReobfSrgServer", RunMinecraft.class);
        this.extractMcpMappings = Tasks.unzip(project, "extractMcpMappings", this.mcpMappings, ext.getVersionCacheDirectory().dir("mcp_mappings"));
        this.remapSrg2Mcp = Tasks.register(project, "remapSrg2Mcp", RemapSrg2Mcp.class);
        this.importMcpNames = Tasks.register(project, "importMcpNames", ImportMcpNames.class);
        this.writeSrg2Mcp = Tasks.register(project, "writeSrg2Mcp", WriteMappings.class);
        this.runMcpClient = Tasks.register(project, "runMcpClient", RunMinecraft.class);
        this.runMcpServer = Tasks.register(project, "runMcpServer", RunMinecraft.class);
        Tasks.group(GROUP_NAME, this.importMcpNames, this.runSrgClient, this.runSrgServer,
                this.runReobfSrgClient, this.runReobfSrgServer, this.runMcpClient, this.runMcpServer);

        SourceSets.linkSource(this.srgSource, ext.getLocalCacheDirectory().dir("sourceSets/srg/sources"));
        SourceSets.extendFromConfiguration(project, this.srgSource, vanilla.vanillaConfig);
        SourceSets.linkSource(this.mcpSource, ext.getLocalCacheDirectory().dir("sourceSets/mcp/sources"));
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
            task.dependsOn(this.extractMcpConfig);
            task.from(mcpConfigDir.map(dir -> dir.file("inject/mcp/MethodsReturnNonnullByDefault.java")), spec -> {
                spec.into("mcp");
                spec.rename($ -> "MethodsReturnNonnullByDefault.java");
            });
            task.into(ext.getLocalCacheDirectory().dir("sourceSets/injected/sources"));
        });

        this.splitClientJar.configure(task -> {
            task.dependsOn(this.extractMcpConfig);

            task.getSourceJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(ext.getVersionCacheDirectory().map(d -> d.file("client-slim.jar")));
            task.getExtraJar().set(ext.getVersionCacheDirectory().map(d -> d.file("client-extra.jar")));
        });
        this.splitServerJar.configure(task -> {
            task.dependsOn(this.extractMcpConfig);

            task.getSourceJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(ext.getVersionCacheDirectory().map(d -> d.file("server-slim.jar")));
            task.getExtraJar().set(ext.getVersionCacheDirectory().map(d -> d.file("server-extra.jar")));
        });
        // TODO: RenameMappings TSRG => TSRG2 by using `static_methods.txt` and inserting into srgutils' IMethod metadata when loading
        this.mergeJars.configure(task -> {
            task.getClientJar().value(this.splitClientJar.flatMap(SplitJar::getSlimJar));
            task.getServerJar().value(this.splitServerJar.flatMap(SplitJar::getSlimJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getMinecraftVersion().set("1.12.2");
            task.getMergedJar().set(mcpDir.map(d -> d.file("merged.jar")));
        });
        this.remapNotch2Srg.configure(task -> {
            task.getInput().set(this.mergeJars.flatMap(MergeJars::getMergedJar));
            task.getMap().from(srgMapping);
            task.getLibraries().from(vanilla.vanillaConfig);
        });
        this.injectMetadata.configure(task -> {
            task.getLogFile().convention(ext.getLocalCacheDirectory().file("injectMetadata/mcinjector.log"));
            task.getSrgJar().set(this.remapNotch2Srg.flatMap(RenameJar::getOutput));
            task.getAccessFile().set(mcpConfigDir.map(dir -> dir.file("access.txt")));
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getExceptionsFile().set(mcpConfigDir.map(dir -> dir.file("exceptions.txt")));
            task.getInjectedJar().set(ext.getLocalCacheDirectory().file("injectMetadata/injected.jar"));
        });
        this.runSrgClient.configure(task -> {
            task.dependsOn(vanilla.downloadAssets);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().dir("assets"));
            task.classpath(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        this.runSrgServer.configure(task -> {
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });
        this.decompileSrg.configure(task -> {
            task.getJavaLauncher().convention(Providers.javaLauncher(project, 25));
            task.getLogFile().convention(ext.getLocalCacheDirectory().file("decompileSrg/decompile.log"));
            task.getCompiledJar().value(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getDecompiledJar().set(ext.getLocalCacheDirectory().file("decompileSrg/decompiled.jar"));
        });
        this.applyInitialDiffs.configure(task -> {
            task.getOriginalDirectory().fileProvider(this.prepareApplyInitialDiffs.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(this.extractInitialPatches.map(Copy::getDestinationDir));
            // task.getInPlace().set(true);
            task.getModifiedDirectory().fileProvider(SourceSets.source(this.srgSource));
        });
        this.runReobfSrgClient.configure(task -> {
            task.dependsOn(SourceSets.compile(this.srgSource), vanilla.downloadAssets);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().dir("assets"));
            task.classpath(SourceSets.classes(this.srgSource), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        this.runReobfSrgServer.configure(task -> {
            task.dependsOn(SourceSets.compile(this.srgSource));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.srgSource), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });
        this.remapSrg2Mcp.configure(task -> {
            task.getSrgSource().set(this.applyInitialDiffs.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.getMethodMappings().from(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().from(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "fields.csv")));
            task.getParameterMappings().from(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "params.csv")));
            task.getTinyMappings().fileProvider(this.tinyFileWhenPresent);
            task.getNamesId().set(this.activeNamesId);
            task.getMcpSource().fileProvider(SourceSets.source(this.mcpSource));
        });
        this.importMcpNames.configure(task -> {
            task.dependsOn(this.extractMcpConfig);

            task.getSrgJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getMcpNames().from(this.mcpMappings);
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getNamesDirectoryConfigured().set(ext.getNamesDirectory().map(dir -> true).orElse(false));
            task.getTinyFile().set(tinyFile.orElse(ext.getLocalCacheDirectory().file("names/mappings.tiny")));
        });
        this.writeSrg2Mcp.configure(task -> {
            task.dependsOn(this.extractMcpConfig);

            task.getJoinedSrgFile().set(srgMapping);
            task.getMethodMappings().fileProvider(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().fileProvider(this.extractMcpMappings.map(Copy::getDestinationDir).map(dir -> new File(dir, "fields.csv")));
            task.getTinyMappings().fileProvider(this.tinyFileWhenPresent);
            task.getNamesId().set(this.activeNamesId);
            task.getDirection().set(WriteMappings.Direction.SRG_TO_MCP);
            // TSRG, not SRG
            task.getFormat().set(IMappingFile.Format.TSRG);
            task.getOutput().set(ext.getLocalCacheDirectory().file("mappings/srg2mcp.tsrg"));
        });
        this.runMcpClient.configure(task -> {
            task.dependsOn(SourceSets.compile(this.mcpSource), vanilla.downloadAssets);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.MCP);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().dir("assets"));
            task.classpath(SourceSets.classes(this.mcpSource), vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar));
        });
        this.runMcpServer.configure(task -> {
            task.dependsOn(SourceSets.compile(this.mcpSource));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.MCP);
            task.getNatives().fileProvider(vanilla.extractNatives.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(this.mcpSource), vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar));
        });

        var intermediates = IntermediateProcessor.of(project);
        intermediates.discardAfterAll("discardInjectedJar",
                List.of(this.decompileSrg, this.importMcpNames, this.runSrgClient, this.runSrgServer),
                this.injectMetadata.flatMap(InjectMetadata::getInjectedJar)
        );
        intermediates.discardAfter(this.prepareApplyInitialDiffs, this.decompileSrg.flatMap(Decompile::getDecompiledJar));
    }

    private static String deriveMcpVersion(Configuration config) {
        var version = Objects.firstDependency(config).getVersion();
        if (version == null) {
            throw new IllegalStateException("mcpConfig dependency has no version to derive MCP_VERSION from.");
        }
        // e.g. "1.12.2-20260220.202731" -> "20260220.202731"
        var dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(dash + 1);
    }

    private static String deriveMcpMappings(Configuration config) {
        var dependency = Objects.firstDependency(config);
        var name = dependency.getName();
        // e.g. "mcp_stable" -> "stable"
        var channel = name.startsWith("mcp_") ? name.substring("mcp_".length()) : name;
        var version = dependency.getVersion();
        if (version == null) {
            throw new IllegalStateException("mcpMappings dependency has no version to derive MCP_MAPPINGS from.");
        }
        // e.g. "39-1.12" -> "39"
        var dash = version.indexOf('-');
        var mappingVersion = dash < 0 ? version : version.substring(0, dash);
        return channel + "_" + mappingVersion;
    }

    TaskProvider<WriteMappings> registerMcp2Notch(Project project, CleanroomExtension ext) {
        var task = Tasks.register(project, "writeMcp2Notch", WriteMappings.class);
        var mcpMappingsDir = this.extractMcpMappings.map(Copy::getDestinationDir);

        task.configure(writeMappings -> {
            writeMappings.dependsOn(this.extractMcpConfig);
            writeMappings.getJoinedSrgFile().set(ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg"));
            writeMappings.getMethodMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "methods.csv")));
            writeMappings.getFieldMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "fields.csv")));
            writeMappings.getTinyMappings().fileProvider(this.tinyFileWhenPresent);
            writeMappings.getNamesId().set(this.activeNamesId);
            writeMappings.getDirection().set(WriteMappings.Direction.MCP_TO_NOTCH);
            writeMappings.getFormat().set(IMappingFile.Format.TSRG);
            writeMappings.getOutput().set(ext.getLocalCacheDirectory().file("mappings/mcp2notch.tsrg"));
        });
        return task;
    }

    public void configurePatchDevelopment(Project project, CleanroomExtension ext, VanillaTasks vanilla) {
        ext.getPatchDev().configureEach(env -> {
            if (env.getName().equals("initial")) {
                return;
            }
            env.getGenerateDiffs().configure(task -> {
                task.getMappingsId().set(this.activeNamesId);
                task.getMcpConfigVersion().set(this.mcpConfigVersion);
            });
            env.getApplyDiffs().configure(task -> task.getMappingsId().set(this.activeNamesId));
            env.getInitializeDiffs().configure(task -> task.getMappingsId().set(this.activeNamesId));
        });

        if (ext.getDevelopInitialPatches().get()) {
            var initial = ext.getPatchDev().register("initial", env -> {
                env.getInput().set(ext.getLocalCacheDirectory().dir("decompileSrg/files"));
                env.dependsOn("prepareApplyInitialDiffs");
            });
            initial.configure(env -> SourceSets.extendFromConfiguration(project, env.getSourceSet(), vanilla.vanillaConfig));
            this.applyInitialDiffs.configure(task -> task.getPatchesDirectory().set(
                    initial.flatMap(CleanroomExtension.PatchDevEnvironment::getPatches)));
        }
    }

    public void configureLoaderPipeline(Project project, CleanroomExtension ext, VanillaTasks vanilla) {
        var installerTools = Objects.toolConfig(project, "installertools", "net.minecraftforge:installertools:1.4.1:fatjar");
        var accessTransformerTool = Objects.toolConfig(project, "accesstransformer", "net.minecraftforge:accesstransformers:8.2.17");

        var extractInheritance = Tasks.tool(project, ext.getLocalCacheDirectory(), "extractInheritance", ExtractInheritance.class, installerTools);
        var checkSAS = Tasks.register(project, "checkSAS", CheckSAS.class);
        var applySAS = Tasks.register(project, "applySAS", ApplySAS.class);
        var stripSrgClientJar = Tasks.register(project, "stripSrgClientJar", StripSideOnlyJar.class);
        var stripSrgServerJar = Tasks.register(project, "stripSrgServerJar", StripSideOnlyJar.class);
        var accessTransformSrgJar = Tasks.tool(project, ext.getLocalCacheDirectory(), "accessTransformSrgJar", AccessTransform.class, accessTransformerTool);

        extractInheritance.configure(task -> {
            task.getInputJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getOutput().set(ext.getLocalCacheDirectory().file("sas/inheritance.json"));
        });
        checkSAS.configure(task -> {
            task.getInheritance().set(extractInheritance.flatMap(ExtractInheritance::getOutput));
            task.getSideAnnotationStrippers().from(ext.getSideAnnotationStrippers());
            task.getOutput().set(ext.getLocalCacheDirectory().file("sas/normalized.sas"));
        });
        applySAS.configure(task -> {
            task.getInputJar().set(this.injectMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getSideAnnotationStrippers().from(checkSAS.flatMap(CheckSAS::getOutput));
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("sas/universal-srg.jar"));
        });
        stripSrgClientJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getTargetSide().set(Side.CLIENT);
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("sas/client-srg.jar"));
        });
        stripSrgServerJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getTargetSide().set(Side.SERVER);
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("sas/server-srg.jar"));
        });
        // The AT runs after SAS and feeds the decompiler: the loader's own code accesses Minecraft
        // members the access transformers widen, so the workspace source has to be the widened one.
        accessTransformSrgJar.configure(task -> {
            task.getInputJar().set(applySAS.flatMap(ApplySAS::getOutputJar));
            task.getAccessTransformers().from(ext.getAccessTransformers());
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("sas/srg-at.jar"));
        });

        this.decompileSrg.configure(task -> {
            task.getCompiledJar().set(accessTransformSrgJar.flatMap(AccessTransform::getOutputJar));
        });
        this.importMcpNames.configure(task -> {
            task.getSrgJar().set(accessTransformSrgJar.flatMap(AccessTransform::getOutputJar));
        });
        this.runSrgClient.configure(task -> {
            task.setClasspath(project.files(
                    stripSrgClientJar.flatMap(StripSideOnlyJar::getOutputJar),
                    vanilla.vanillaConfig,
                    this.splitClientJar.flatMap(SplitJar::getExtraJar)));
        });
        this.runSrgServer.configure(task -> {
            task.setClasspath(project.files(
                    stripSrgServerJar.flatMap(StripSideOnlyJar::getOutputJar),
                    vanilla.vanillaConfig,
                    this.splitServerJar.flatMap(SplitJar::getExtraJar)));
        });

        var intermediates = IntermediateProcessor.of(project);
        intermediates.alsoAfter(project.getTasks().named("discardInjectedJar", Delete.class), extractInheritance, applySAS);
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

}
