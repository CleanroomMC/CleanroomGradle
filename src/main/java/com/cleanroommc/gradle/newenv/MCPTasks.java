package com.cleanroommc.gradle.newenv;

import com.cleanroommc.gradle.newapi.Meta;
import com.cleanroommc.gradle.newapi.ext.CleanroomExtension;
import com.cleanroommc.gradle.newapi.schema.VersionMeta;
import com.cleanroommc.gradle.newapi.task.Tasks;
import com.cleanroommc.gradle.newapi.task.common.Decompile;
import com.cleanroommc.gradle.newapi.task.mc.RunMinecraft;
import com.cleanroommc.gradle.newapi.task.mcp.*;
import com.cleanroommc.gradle.newapi.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.newapi.util.Environment;
import com.cleanroommc.gradle.newapi.util.Objects;
import com.cleanroommc.gradle.newapi.util.lazy.SourceSets;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public final class MCPTasks {

    private static final String GROUP_NAME = "MCP Tasks";

    public static NamedDomainObjectProvider<Configuration> MCP;
    public static NamedDomainObjectProvider<SourceSet> SRG_SOURCE, MCP_SOURCE;

    public static TaskProvider<Copy> EXTRACT_MCP_CONFIG, EXTRACT_INITIAL_PATCHES, PREPARE_APPLY_INITIAL_DIFFS, EXTRACT_MCP_MAPPINGS;
    public static TaskProvider<SplitJar> SPLIT_CLIENT_JAR, SPLIT_SERVER_JAR;
    public static TaskProvider<MergeJars> MERGE_JARS;
    public static TaskProvider<RemapNotch2Srg> REMAP_NOTCH2SRG;
    public static TaskProvider<InjectMetadata> INJECT_METADATA;
    public static TaskProvider<RunMinecraft> RUN_SRG_CLIENT, RUN_SRG_SERVER, RUN_REOBF_SRG_CLIENT, RUN_REOBF_SRG_SERVER, RUN_MCP_CLIENT, RUN_MCP_SERVER;
    public static TaskProvider<Decompile> DECOMPILE_SRG;
    public static TaskProvider<ApplyDiffs> APPLY_INITIAL_DIFFS;
    public static TaskProvider<RemapSrg2Mcp> REMAP_SRG2MCP;

    public static void init(Project project, CleanroomExtension ext) {
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

        MCP = Objects.config(project, "mcp");

        SRG_SOURCE = SourceSets.of(project, "srgSource");
        MCP_SOURCE = SourceSets.of(project, "mcpSource");

        var mcpDir = ext.getVersionCacheDirectory().dir("mcp");
        var mcpConfigDir = ext.getVersionCacheDirectory().dir("mcp_config/config");
        var srgMapping = ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg");

        EXTRACT_MCP_CONFIG = Tasks.unzip(project, GROUP_NAME, "extractMcpConfig", project.provider(() -> Objects.artifact(MCP, "mcp_config")), ext.getVersionCacheDirectory().dir("mcp_config"));
        SPLIT_CLIENT_JAR = Tasks.of(project, GROUP_NAME, "splitClientJar", SplitJar.class);
        SPLIT_SERVER_JAR = Tasks.of(project, GROUP_NAME, "splitServerJar", SplitJar.class);
        MERGE_JARS = Tasks.of(project, GROUP_NAME, "mergeJars", MergeJars.class);
        REMAP_NOTCH2SRG = Tasks.of(project, GROUP_NAME, "remapNotch2Srg", RemapNotch2Srg.class);
        INJECT_METADATA = Tasks.of(project, GROUP_NAME, "injectMetadata", InjectMetadata.class);
        RUN_SRG_CLIENT = Tasks.of(project, GROUP_NAME, "runSrgClient", RunMinecraft.class);
        RUN_SRG_SERVER = Tasks.of(project, GROUP_NAME, "runSrgServer", RunMinecraft.class);
        DECOMPILE_SRG = Tasks.of(project, GROUP_NAME, "decompileSrg", Decompile.class);
        EXTRACT_INITIAL_PATCHES = Tasks.unzip(project, GROUP_NAME, "extractInitialPatches", project.provider(() -> Objects.artifact(MCP, "initial-patches")), ext.getVersionCacheDirectory().dir("initial_patches"));
        PREPARE_APPLY_INITIAL_DIFFS = Tasks.unzip(project, GROUP_NAME, "prepareApplyInitialDiffs", DECOMPILE_SRG.map(Decompile::getDecompiledJar), DECOMPILE_SRG.map(Decompile::getDecompiledJar).map(rfd -> new File(rfd.get().getAsFile().getParent(), "files")));
        APPLY_INITIAL_DIFFS = Tasks.of(project, GROUP_NAME, "applyInitialDiffs", ApplyDiffs.class);
        RUN_REOBF_SRG_CLIENT = Tasks.of(project, GROUP_NAME, "runReobfSrgClient", RunMinecraft.class);
        RUN_REOBF_SRG_SERVER = Tasks.of(project, GROUP_NAME, "runReobfSrgServer", RunMinecraft.class);
        EXTRACT_MCP_MAPPINGS = Tasks.unzip(project, GROUP_NAME, "extractMcpMappings", project.provider(() -> Objects.artifact(MCP, "mcp_stable")), ext.getVersionCacheDirectory().dir("mcp_mappings"));
        REMAP_SRG2MCP = Tasks.of(project, GROUP_NAME, "remapSrg2Mcp", RemapSrg2Mcp.class);
        RUN_MCP_CLIENT = Tasks.of(project, GROUP_NAME, "runMcpClient", RunMinecraft.class);
        RUN_MCP_SERVER = Tasks.of(project, GROUP_NAME, "runMcpServer", RunMinecraft.class);

        SourceSets.linkSource(SRG_SOURCE, ext.getLocalCacheDirectory().dir("sourceSets/srg"));
        SourceSets.configureCompile(project, SRG_SOURCE, task -> task.dependsOn(APPLY_INITIAL_DIFFS));
        SourceSets.extendFromConfiguration(project, SRG_SOURCE, VanillaTasks.VANILLA_CONFIG);
        SourceSets.linkSource(MCP_SOURCE, ext.getLocalCacheDirectory().dir("sourceSets/mcp"));
        SourceSets.configureCompile(project, MCP_SOURCE, task -> task.dependsOn(REMAP_SRG2MCP));
        SourceSets.extendFromConfiguration(project, MCP_SOURCE, VanillaTasks.VANILLA_CONFIG);

        SPLIT_CLIENT_JAR.configure(task -> {
            task.dependsOn(VanillaTasks.DOWNLOAD_CLIENT_JAR, EXTRACT_MCP_CONFIG);

            task.getSourceJar().fileProvider(VanillaTasks.DOWNLOAD_CLIENT_JAR.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(ext.getVersionCacheDirectory().map(d -> d.file("client-slim.jar")));
            task.getExtraJar().set(ext.getVersionCacheDirectory().map(d -> d.file("client-extra.jar")));
        });
        SPLIT_SERVER_JAR.configure(task -> {
            task.dependsOn(VanillaTasks.DOWNLOAD_SERVER_JAR, EXTRACT_MCP_CONFIG);

            task.getSourceJar().fileProvider(VanillaTasks.DOWNLOAD_SERVER_JAR.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(ext.getVersionCacheDirectory().map(d -> d.file("server-slim.jar")));
            task.getExtraJar().set(ext.getVersionCacheDirectory().map(d -> d.file("server-extra.jar")));
        });
        // TODO: RenameMappings TSRG => TSRG2 by using `static_methods.txt` and inserting into srgutils' IMethod metadata when loading
        MERGE_JARS.configure(task -> {
            task.dependsOn(SPLIT_CLIENT_JAR, SPLIT_SERVER_JAR);

            task.getClientJar().value(SPLIT_CLIENT_JAR.flatMap(SplitJar::getSlimJar));
            task.getServerJar().value(SPLIT_SERVER_JAR.flatMap(SplitJar::getSlimJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getMinecraftVersion().set("1.12.2");
            task.getMergedJar().set(mcpDir.map(d -> d.file("merged.jar")));
        });
        REMAP_NOTCH2SRG.configure(task -> {
            task.dependsOn(MERGE_JARS);

            task.getNotchJar().value(MERGE_JARS.flatMap(MergeJars::getMergedJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getSrgJar().set(new File(task.getWorkingDir(), "joined-srg.jar"));
        });
        INJECT_METADATA.configure(task -> {
            task.dependsOn(REMAP_NOTCH2SRG);

            task.getSrgJar().value(REMAP_NOTCH2SRG.flatMap(RemapNotch2Srg::getSrgJar));
            task.getAccessFile().set(mcpConfigDir.map(dir -> dir.file("access.txt")));
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getExceptionsFile().set(mcpConfigDir.map(dir -> dir.file("exceptions.txt")));
            task.getInjectedJar().set(new File(task.getWorkingDir(), "injected.jar"));
        });
        RUN_SRG_CLIENT.configure(task -> {
            task.dependsOn(INJECT_METADATA);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.SRG);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().file("assets"));
            task.classpath(INJECT_METADATA.map(InjectMetadata::getInjectedJar), VanillaTasks.VANILLA_CONFIG, SPLIT_CLIENT_JAR.map(SplitJar::getExtraJar));
        });
        RUN_SRG_SERVER.configure(task -> {
            task.dependsOn(INJECT_METADATA);

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.SRG);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.classpath(INJECT_METADATA.map(InjectMetadata::getInjectedJar), VanillaTasks.VANILLA_CONFIG, SPLIT_SERVER_JAR.map(SplitJar::getExtraJar));
        });
        DECOMPILE_SRG.configure(task -> {
            task.dependsOn(INJECT_METADATA);

            task.getCompiledJar().value(INJECT_METADATA.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(VanillaTasks.VANILLA_CONFIG);
            task.getDecompiledJar().set(new File(task.getWorkingDir(), "decompiled.jar"));
            task.args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-thr=-1", "-ind=    ");
        });
        PREPARE_APPLY_INITIAL_DIFFS.configure(task -> task.dependsOn(DECOMPILE_SRG));
        APPLY_INITIAL_DIFFS.configure(task -> {
            task.dependsOn(DECOMPILE_SRG);

            task.getOriginalDirectory().fileProvider(PREPARE_APPLY_INITIAL_DIFFS.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(EXTRACT_INITIAL_PATCHES.map(Copy::getDestinationDir));
            // task.getInPlace().set(true);
            task.getModifiedDirectory().fileProvider(SourceSets.source(SRG_SOURCE));
        });
        RUN_REOBF_SRG_CLIENT.configure(task -> {
            task.dependsOn(SourceSets.compile(SRG_SOURCE));

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().file("assets"));
            task.classpath(SourceSets.classes(SRG_SOURCE), VanillaTasks.VANILLA_CONFIG, SPLIT_CLIENT_JAR.map(SplitJar::getExtraJar));
        });
        RUN_REOBF_SRG_SERVER.configure(task -> {
            task.dependsOn(SourceSets.compile(SRG_SOURCE));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.REOBF_SRG);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(SRG_SOURCE), VanillaTasks.VANILLA_CONFIG, SPLIT_SERVER_JAR.map(SplitJar::getExtraJar));
        });
        REMAP_SRG2MCP.configure(task -> {
            task.dependsOn(APPLY_INITIAL_DIFFS);

            task.getSrgSource().set(APPLY_INITIAL_DIFFS.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.getMethodMappings().from(EXTRACT_MCP_MAPPINGS.map(Copy::getDestinationDir).map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().from(EXTRACT_MCP_MAPPINGS.map(Copy::getDestinationDir).map(dir -> new File(dir, "fields.csv")));
            task.getParameterMappings().from(EXTRACT_MCP_MAPPINGS.map(Copy::getDestinationDir).map(dir -> new File(dir, "params.csv")));
            task.getMcpSource().fileProvider(SourceSets.source(MCP_SOURCE));
        });
        RUN_MCP_CLIENT.configure(task -> {
            task.dependsOn(SourceSets.compile(MCP_SOURCE));

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.MCP);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().file("assets"));
            task.classpath(SourceSets.classes(MCP_SOURCE), VanillaTasks.VANILLA_CONFIG, SPLIT_CLIENT_JAR.map(SplitJar::getExtraJar));
        });
        RUN_MCP_SERVER.configure(task -> {
            task.dependsOn(SourceSets.compile(MCP_SOURCE));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.MCP);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.classpath(SourceSets.classes(MCP_SOURCE), VanillaTasks.VANILLA_CONFIG, SPLIT_SERVER_JAR.map(SplitJar::getExtraJar));
        });
    }

    public static void afterEvaluate(Project project, CleanroomExtension ext) {
        Objects.dependency(project, MCP, "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
        Objects.dependency(project, MCP, "com.cleanroommc:initial-patches:1.1.0");
        Objects.dependency(project, MCP, "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");

        if (ext.getDevelopInitialPatches().get()) {
            var patchDevTasks = new PatchDevelopmentTasks(project, "Initial", ext, DECOMPILE_SRG.map(Decompile::getDecompiledJar), DECOMPILE_SRG);
            SourceSets.extendFromConfiguration(project, patchDevTasks.sourceSet(), VanillaTasks.VANILLA_CONFIG);
        }
    }

    private MCPTasks() { }

}
