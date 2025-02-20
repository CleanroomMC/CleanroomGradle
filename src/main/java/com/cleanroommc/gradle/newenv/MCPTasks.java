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
import com.cleanroommc.gradle.newapi.util.IO;
import com.cleanroommc.gradle.newapi.util.Objects;
import com.cleanroommc.gradle.newapi.util.lazy.Providers;
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
    public static NamedDomainObjectProvider<SourceSet> SRG;

    public static TaskProvider<Copy> EXTRACT_CLIENT_RESOURCES, EXTRACT_SERVER_RESOURCES, EXTRACT_MCP_CONFIG, EXTRACT_INITIAL_PATCHES, PREPARE_APPLY_INITIAL_DIFFS, EXTRACT_MCP_MAPPINGS;
    public static TaskProvider<SplitJar> SPLIT_CLIENT_JAR, SPLIT_SERVER_JAR;
    public static TaskProvider<MergeJars> MERGE_JARS;
    public static TaskProvider<RemapNotch2Srg> REMAP_NOTCH2SRG;
    public static TaskProvider<InjectMetadata> INJECT_METADATA;
    public static TaskProvider<Decompile> DECOMPILE;
    public static TaskProvider<ApplyDiffs> APPLY_INITIAL_DIFFS;
    public static TaskProvider<RunMinecraft> RUN_SRG_CLIENT, RUN_SRG_SERVER;
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

        SRG = SourceSets.of(project, "srg");

        var mcpDir = ext.getVersionCacheDirectory().dir("mcp");
        var mcpConfigDir = ext.getVersionCacheDirectory().dir("mcp_config/config");
        var srgMapping = ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg");

        EXTRACT_CLIENT_RESOURCES = Tasks.unzip(project, GROUP_NAME, "extractClientResources", VanillaTasks.DOWNLOAD_CLIENT_JAR.map(Download::getDest), ext.getVersionCacheDirectory().dir("resources/client"));
        EXTRACT_SERVER_RESOURCES = Tasks.unzip(project, GROUP_NAME, "extractServerResources", VanillaTasks.DOWNLOAD_SERVER_JAR.map(Download::getDest), ext.getVersionCacheDirectory().dir("resources/server"));
        EXTRACT_MCP_CONFIG = Tasks.unzip(project, GROUP_NAME, "extractMcpConfig", Providers.of(() -> Objects.artifact(MCP, "mcp_config")), ext.getVersionCacheDirectory().dir("mcp_config"));
        SPLIT_CLIENT_JAR = Tasks.of(project, GROUP_NAME, "splitClientJar", SplitJar.class);
        SPLIT_SERVER_JAR = Tasks.of(project, GROUP_NAME, "splitServerJar", SplitJar.class);
        MERGE_JARS = Tasks.of(project, GROUP_NAME, "mergeJars", MergeJars.class);
        REMAP_NOTCH2SRG = Tasks.of(project, GROUP_NAME, "remapNotch2Srg", RemapNotch2Srg.class);
        INJECT_METADATA = Tasks.of(project, GROUP_NAME, "injectMetadata", InjectMetadata.class);
        DECOMPILE = Tasks.of(project, GROUP_NAME, "decompile", Decompile.class);
        EXTRACT_INITIAL_PATCHES = Tasks.unzip(project, GROUP_NAME, "extractInitialPatches", Providers.of(() -> Objects.artifact(MCP, "initial-patches")), ext.getVersionCacheDirectory().dir("initial_patches"));
        PREPARE_APPLY_INITIAL_DIFFS = Tasks.unzip(project, GROUP_NAME, "prepareApplyInitialDiffs", DECOMPILE.map(Decompile::getDecompiledJar), DECOMPILE.map(Decompile::getDecompiledJar).map(rfd -> new File(rfd.get().getAsFile().getParent(), "files")));
        APPLY_INITIAL_DIFFS = Tasks.of(project, GROUP_NAME, "applyInitialDiffs", ApplyDiffs.class);
        RUN_SRG_CLIENT = Tasks.of(project, GROUP_NAME, "runSrgClient", RunMinecraft.class);
        RUN_SRG_SERVER = Tasks.of(project, GROUP_NAME, "runSrgServer", RunMinecraft.class);
        EXTRACT_MCP_MAPPINGS = Tasks.unzip(project, GROUP_NAME, "extractMcpMappings", Providers.of(() -> Objects.artifact(MCP, "mcp_stable")), ext.getVersionCacheDirectory().dir("mcp_mappings"));
        REMAP_SRG2MCP = Tasks.of(project, GROUP_NAME, "remapSrg2Mcp", RemapSrg2Mcp.class);

        EXTRACT_CLIENT_RESOURCES.configure(task -> {
            task.dependsOn(VanillaTasks.DOWNLOAD_CLIENT_JAR);
            task.exclude("**/*.class").setIncludeEmptyDirs(false);
        });
        EXTRACT_SERVER_RESOURCES.configure(task -> {
            task.dependsOn(VanillaTasks.DOWNLOAD_SERVER_JAR);
            task.exclude("**/*.class").setIncludeEmptyDirs(false);
        });
        SPLIT_CLIENT_JAR.configure(task -> {
            task.dependsOn(VanillaTasks.DOWNLOAD_CLIENT_JAR, EXTRACT_MCP_CONFIG);
            task.checkExistence(task, task.getSlimJar(), task.getExtraJar());
            task.checkHash(task, task.getSlimJar(), "592ef6dfa5cb6bb3755f30cd9f58e1a80ba920c0");
            task.checkHash(task, task.getExtraJar(), "fc62b882c1b1bf781898210eaf6568fd8a678eeb");

            task.getSourceJar().fileProvider(VanillaTasks.DOWNLOAD_CLIENT_JAR.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(mcpDir.map(d -> d.file("client-slim.jar")));
            task.getExtraJar().set(mcpDir.map(d -> d.file("client-extra.jar")));
        });
        SPLIT_SERVER_JAR.configure(task -> {
            task.dependsOn(VanillaTasks.DOWNLOAD_SERVER_JAR, EXTRACT_MCP_CONFIG);
            task.checkExistence(task, task.getSlimJar(), task.getExtraJar());
            task.checkHash(task, task.getSlimJar(), "ea8957a4a5ba493e676c0516ecaa9124a42384ff");
            task.checkHash(task, task.getExtraJar(), "9c40dc98a1119a6f9201c74d091aa981bf438517");

            task.getSourceJar().fileProvider(VanillaTasks.DOWNLOAD_SERVER_JAR.map(Download::getDest));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(mcpDir.map(d -> d.file("server-slim.jar")));
            task.getExtraJar().set(mcpDir.map(d -> d.file("server-extra.jar")));
        });
        // TODO: RenameMappings TSRG => TSRG2 by using `static_methods.txt` and inserting into srgutils' IMethod metadata when loading
        MERGE_JARS.configure(task -> {
            task.dependsOn(SPLIT_CLIENT_JAR, SPLIT_SERVER_JAR);
            task.checkExistence(task, task.getMergedJar());
            task.checkHash(task, task.getMergedJar(), "ad6b290140f516db222a2bee4ea79c58e9974700");

            task.getClientJar().value(SPLIT_CLIENT_JAR.flatMap(SplitJar::getSlimJar));
            task.getServerJar().value(SPLIT_SERVER_JAR.flatMap(SplitJar::getSlimJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getMinecraftVersion().set("1.12.2");
            task.getMergedJar().set(mcpDir.map(d -> d.file("merged.jar")));
        });
        // TODO: take AT into consideration
        REMAP_NOTCH2SRG.configure(task -> {
            task.dependsOn(MERGE_JARS);
            task.checkExistence(task, task.getSrgJar());
            task.checkHash(task, task.getSrgJar(), "07f0f729bf65ee521f8b60760344b7319e3cd3cd"); // No AT hash

            task.getNotchJar().value(MERGE_JARS.flatMap(MergeJars::getMergedJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getSrgJar().set(new File(task.getWorkingDir(), "joined-srg.jar"));
        });
        INJECT_METADATA.configure(task -> {
            task.dependsOn(REMAP_NOTCH2SRG);
            task.checkExistence(task, task.getInjectedJar());
            task.checkHash(task, task.getInjectedJar(), "26bcf4e0bef052850fc36d9d3fe8f4c242432757");

            task.getSrgJar().value(REMAP_NOTCH2SRG.flatMap(RemapNotch2Srg::getSrgJar));
            task.getAccessFile().set(mcpConfigDir.map(dir -> dir.file("access.txt")));
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getExceptionsFile().set(mcpConfigDir.map(dir -> dir.file("exceptions.txt")));
            task.getInjectedJar().set(new File(task.getWorkingDir(), "injected.jar"));
        });
        DECOMPILE.configure(task -> {
            task.dependsOn(INJECT_METADATA);
            task.checkExistence(task, task.getDecompiledJar());
            task.checkHash(task, task.getDecompiledJar(), "95afe3e810f8713c3adb5873df51baea21de59a6");

            task.getCompiledJar().value(INJECT_METADATA.flatMap(InjectMetadata::getInjectedJar));
            task.getLibraries().from(VanillaTasks.VANILLA_CONFIG);
            task.getDecompiledJar().set(new File(task.getWorkingDir(), "decompiled.jar"));
            task.args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-ind=    ");
        });
        PREPARE_APPLY_INITIAL_DIFFS.configure(task -> task.dependsOn(DECOMPILE));
        APPLY_INITIAL_DIFFS.configure(task -> {
            task.dependsOn(DECOMPILE);

            task.getOriginalDirectory().fileProvider(PREPARE_APPLY_INITIAL_DIFFS.map(Copy::getDestinationDir));
            task.getPatchesDirectory().fileProvider(EXTRACT_INITIAL_PATCHES.map(Copy::getDestinationDir));
            task.getInPlace().set(true);
        });
        RUN_SRG_CLIENT.configure(task -> {
            task.dependsOn(APPLY_INITIAL_DIFFS);
            task.getMinecraftVersion().set("1.12.2");
            task.getSide().set(Side.CLIENT);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().file("assets"));
            task.setWorkingDir(IO.runDir(project, "1.12.2", Environment.SRG, Side.CLIENT));
            task.classpath(APPLY_INITIAL_DIFFS.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.classpath(VanillaTasks.VANILLA_CONFIG);
            task.getMainClass().set("net.minecraft.client.main.Main");
        });
        RUN_SRG_SERVER.configure(task -> {
            task.dependsOn(APPLY_INITIAL_DIFFS);
            task.getMinecraftVersion().set("1.12.2");
            task.getSide().set(Side.SERVER);
            task.getNatives().fileProvider(VanillaTasks.EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.setWorkingDir(IO.runDir(project, "1.12.2", Environment.SRG, Side.SERVER));
            task.classpath(APPLY_INITIAL_DIFFS.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.classpath(VanillaTasks.VANILLA_CONFIG);
            task.getMainClass().set("net.minecraft.server.MinecraftServer");
        });
        REMAP_SRG2MCP.configure(task -> {
            task.dependsOn(APPLY_INITIAL_DIFFS);

            task.getSrgSource().set(APPLY_INITIAL_DIFFS.flatMap(applyDiffs -> applyDiffs.getInPlace().get() ? applyDiffs.getOriginalDirectory() : applyDiffs.getModifiedDirectory()));
            task.getMethodMappings().from(EXTRACT_MCP_MAPPINGS.map(Copy::getDestinationDir).map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().from(EXTRACT_MCP_MAPPINGS.map(Copy::getDestinationDir).map(dir -> new File(dir, "fields.csv")));
            task.getParameterMappings().from(EXTRACT_MCP_MAPPINGS.map(Copy::getDestinationDir).map(dir -> new File(dir, "params.csv")));
            task.getMcpSource().set(ext.getLocalCacheDirectory().dir("remapSrg2Mcp/source"));
        });
    }

    public static void afterEvaluate(Project project, CleanroomExtension ext) {
        Objects.dependency(project, MCP, "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
        Objects.dependency(project, MCP, "com.cleanroommc:initial-patches:1.1.0");
        Objects.dependency(project, MCP, "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");

        if (ext.getDevelopInitialPatches().get()) {
            var patchDevTasks = new PatchDevelopmentTasks(project, "Initial", ext, DECOMPILE.map(Decompile::getDecompiledJar), DECOMPILE);
            patchDevTasks.sourceSet().configure(sourceSet -> {
                var config = Objects.resolvedConfig(project, sourceSet.getImplementationConfigurationName());
                config.extendsFrom(VanillaTasks.VANILLA_CONFIG.get());
            });
        }
    }

    private MCPTasks() { }

}
