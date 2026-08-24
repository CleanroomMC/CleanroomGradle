package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.LoaderExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.ext.PatchDevEnvironment;
import com.cleanroommc.gradle.api.ext.PatchesExtension;
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

    public final TaskProvider<DefaultTask> setup;
    public final TaskProvider<RunMinecraft> runCleanroomClient, runCleanroomServer;
    public final TaskProvider<NsightExec> runCleanroomNsightClient;

    public CleanroomTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, LoaderExtension loader,
                          PatchesExtension patches, VanillaTasks vanilla, MCPTasks mcp, McpMappings mappings) {
        var mainSourceSet = project.getExtensions().getByType(SourceSetContainer.class).named(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSets.extendFromConfiguration(project, mainSourceSet, vanilla.vanillaConfig);
        var minecraftPatchDev = patches.getPatchDev().register("minecraft", env -> {
            var module = project.getLayout().getProjectDirectory().dir("module/minecraft");
            env.getInput().fileProvider(SourceSets.source(mcp.mcpSource));
            env.getPatches().set(module.dir("patches"));
            env.getOutput().set(module.dir("src/main/java"));
            env.dependsOn(mcp.remapSrg2Mcp.getName());
        });
        this.setup = Tasks.register(project, "setup");
        this.setup.configure(task -> {
            task.setDescription("Creates the loader environment by decompiling Minecraft and applying current patches.");
            task.dependsOn(minecraftPatchDev.map(PatchDevEnvironment::getApplyDiffs), mcp.prepareMcpInjectedSources);
        });

        mainSourceSet.configure(sourceSet -> {
            sourceSet.getJava().srcDir(mcp.prepareMcpInjectedSources.map(Copy::getDestinationDir));
            project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).configure(task ->
                    task.dependsOn(minecraftPatchDev.map(PatchDevEnvironment::getPrepareEnvironment),
                            mcp.prepareMcpInjectedSources));
        });
        var runDir = project.getLayout().getProjectDirectory().dir("run").getAsFile();
        var offline = project.getGradle().getStartParameter().isOffline();
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var mcpToSrg = mappings.writeSrg2Mcp.flatMap(WriteMappings::getOutput);

        var fml = new MinecraftRuns.Fml();
        fml.minecraftVersion = vanilla.minecraftVersion;
        fml.mcpVersion = mappings.mcpVersionId;
        fml.mcpMappings = mappings.mcpMappingsId;
        fml.mcpToSrg = mcpToSrg;
        fml.forgeGroup = String.valueOf(project.getGroup());
        fml.forgeVersion = loader.getForgeVersion();
        fml.assetIndex = minecraft.getVersionMeta().map(VersionMeta::assetIndexId);
        fml.assets = caches.getDirectory().dir("assets");
        fml.natives = natives;
        fml.launchClass = loader.getLaunchClass();

        this.runCleanroomClient = Tasks.register(project, "runCleanroomClient", RunMinecraft.class);
        this.runCleanroomServer = Tasks.register(project, "runCleanroomServer", RunMinecraft.class);
        this.runCleanroomNsightClient = Tasks.register(project, "runCleanroomNsightClient", NsightExec.class);
        Tasks.group(GROUP_NAME, this.setup, this.runCleanroomClient, this.runCleanroomServer, this.runCleanroomNsightClient);

        this.runCleanroomClient.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets, mappings.writeSrg2Mcp);
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getMainClass().set(loader.getClientMainClass());
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath), mcp.splitClientJar.flatMap(SplitJar::getExtraJar));
            var client = new MinecraftRuns.Fml();
            client.client = true;
            client.target = loader.getClientTarget();
            client.tweakClass = loader.getClientTweakClass();
            client.launchClass = fml.launchClass;
            client.minecraftVersion = fml.minecraftVersion;
            client.mcpVersion = fml.mcpVersion;
            client.mcpMappings = fml.mcpMappings;
            client.mcpToSrg = fml.mcpToSrg;
            client.forgeGroup = fml.forgeGroup;
            client.forgeVersion = fml.forgeVersion;
            client.assetIndex = fml.assetIndex;
            client.assets = fml.assets;
            client.natives = fml.natives;
            MinecraftRuns.fmlEnvironment(task, client);
        });

        this.runCleanroomServer.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), mappings.writeSrg2Mcp);
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getMainClass().set(loader.getServerMainClass());
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath), mcp.splitServerJar.flatMap(SplitJar::getExtraJar));
            var server = new MinecraftRuns.Fml();
            server.client = false;
            server.target = loader.getServerTarget();
            server.tweakClass = loader.getServerTweakClass();
            server.launchClass = fml.launchClass;
            server.minecraftVersion = fml.minecraftVersion;
            server.mcpVersion = fml.mcpVersion;
            server.mcpMappings = fml.mcpMappings;
            server.mcpToSrg = fml.mcpToSrg;
            server.forgeGroup = fml.forgeGroup;
            server.forgeVersion = fml.forgeVersion;
            MinecraftRuns.fmlEnvironment(task, server);
        });

        this.runCleanroomNsightClient.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets, vanilla.extractNatives, mappings.writeSrg2Mcp);
            task.getActivity().set(project.getProviders().gradleProperty("nsight_activity"));
            task.getNgfxPath().set(project.getProviders().gradleProperty("nsight_ngfx_path"));
            task.getRunTaskName().set(this.runCleanroomClient.getName());
            task.getGradleWrapperJar().set(project.getLayout().getProjectDirectory().file("gradle/wrapper/gradle-wrapper.jar"));
            task.getJavaExecutable().set(this.runCleanroomClient.flatMap(RunMinecraft::getJavaLauncher)
                    .map(launcher -> launcher.getExecutablePath().getAsFile().getAbsolutePath()));
        });
    }

}
