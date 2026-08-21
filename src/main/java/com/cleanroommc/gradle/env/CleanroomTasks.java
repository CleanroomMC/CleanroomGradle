package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import com.cleanroommc.gradle.api.task.mc.NsightExec;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.SplitJar;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.util.Environment;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

public final class CleanroomTasks {

    private static final String GROUP_NAME = "cleanroom";
    private static final String MC_VERSION = "1.12.2";

    public final TaskProvider<DefaultTask> setup;
    public final TaskProvider<RunMinecraft> runCleanroomClient, runCleanroomServer;
    public final TaskProvider<NsightExec> runCleanroomNsightClient;

    public CleanroomTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla, MCPTasks mcp) {
        var mainSourceSet = project.getExtensions().getByType(SourceSetContainer.class).named(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSets.extendFromConfiguration(project, mainSourceSet, vanilla.vanillaConfig);
        var minecraftPatchDev = ext.getPatchDev().register("minecraft", env -> {
            var module = project.getLayout().getProjectDirectory().dir("module/minecraft");
            env.getInput().set(ext.getLocalCacheDirectory().dir("sourceSets/mcp/sources"));
            env.getPatches().set(module.dir("patches"));
            env.getOutput().set(module.dir("src/main/java"));
            env.dependsOn(mcp.remapSrg2Mcp.getName());
        });
        this.setup = Tasks.register(project, "setup");
        this.setup.configure(task -> {
            task.setDescription("Creates the loader environment by decompiling Minecraft and applying current patches.");
            task.dependsOn(minecraftPatchDev.map(CleanroomExtension.PatchDevEnvironment::getApplyDiffs), mcp.prepareMcpInjectedSources);
        });

        mainSourceSet.configure(sourceSet -> {
            sourceSet.getJava().srcDir(mcp.prepareMcpInjectedSources.map(Copy::getDestinationDir));
            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task ->
                    task.dependsOn(minecraftPatchDev.map(CleanroomExtension.PatchDevEnvironment::getPrepareEnvironment),
                            mcp.prepareMcpInjectedSources));
        });
        var runDir = project.getLayout().getProjectDirectory().dir("run").getAsFile();
        var forgeGroup = String.valueOf(project.getGroup());

        var assetsDir = ext.getCacheDirectory().dir("assets");
        var assetIndex = ext.getVersionMeta().map(VersionMeta::assetIndexId);
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var mcpToSrg = mcp.writeSrg2Mcp.flatMap(WriteMappings::getOutput);
        var mcpVersion = mcp.mcpVersionId;
        var mcpMappings = mcp.mcpMappingsId;

        this.runCleanroomClient = Tasks.register(project, "runCleanroomClient", RunMinecraft.class);
        this.runCleanroomServer = Tasks.register(project, "runCleanroomServer", RunMinecraft.class);
        this.runCleanroomNsightClient = Tasks.register(project, "runCleanroomNsightClient", NsightExec.class);
        Tasks.group(GROUP_NAME, this.setup, this.runCleanroomClient, this.runCleanroomServer, this.runCleanroomNsightClient);

        this.runCleanroomClient.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets, mcp.writeSrg2Mcp);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMainClass().set("com.cleanroommc.boot.MainClient");
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.getAssetIndexVersion().set(assetIndex);
            task.getVanillaAssetsLocation().set(assetsDir);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath), mcp.splitClientJar.flatMap(SplitJar::getExtraJar));

            task.environment("target", "fmldevclient");
            task.environment("tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker");
            task.environment("mainClass", "top.outlands.foundation.boot.Foundation");
            task.environment("assetIndex", assetIndex);
            task.environment("assetDirectory", assetsDir);
            task.environment("nativesDirectory", natives);
            task.environment("MC_VERSION", MC_VERSION);
            task.environment("MCP_VERSION", mcpVersion);
            task.environment("MCP_MAPPINGS", mcpMappings);
            task.environment("MCP_TO_SRG", mcpToSrg);
            task.environment("FORGE_GROUP", forgeGroup);
            task.environment("FORGE_VERSION", ext.getForgeVersion());

            task.jvmArgs("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true");
        });

        this.runCleanroomServer.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), mcp.writeSrg2Mcp);

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMainClass().set("com.cleanroommc.boot.MainServer");
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath), mcp.splitServerJar.flatMap(SplitJar::getExtraJar));

            task.environment("target", "fmldevserver");
            task.environment("tweakClass", "net.minecraftforge.fml.common.launcher.FMLServerTweaker");
            task.environment("mainClass", "top.outlands.foundation.boot.Foundation");
            task.environment("MC_VERSION", MC_VERSION);
            task.environment("MCP_VERSION", mcpVersion);
            task.environment("MCP_MAPPINGS", mcpMappings);
            task.environment("MCP_TO_SRG", mcpToSrg);
            task.environment("FORGE_GROUP", forgeGroup);
            task.environment("FORGE_VERSION", ext.getForgeVersion());
        });

        this.runCleanroomNsightClient.configure(task -> {
            // Mirror the run task's dependencies (ngfx re-launches it through the Gradle wrapper).
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets, vanilla.extractNatives, mcp.writeSrg2Mcp);

            task.getActivity().set(project.getProviders().gradleProperty("nsight_activity"));
            task.getNgfxPath().set(project.getProviders().gradleProperty("nsight_ngfx_path"));
            task.getRunTaskName().set(this.runCleanroomClient.getName());
            task.getGradleWrapperJar().set(project.getLayout().getProjectDirectory().file("gradle/wrapper/gradle-wrapper.jar"));
            task.getJavaExecutable().set(this.runCleanroomClient.flatMap(RunMinecraft::getJavaLauncher)
                    .map(launcher -> launcher.getExecutablePath().getAsFile().getAbsolutePath()));
        });
    }


}
