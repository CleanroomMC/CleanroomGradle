package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.mc.NsightExec;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.util.Environment;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public final class CleanroomTasks {

    private static final String GROUP_NAME = "Cleanroom Tasks";
    private static final String MC_VERSION = "1.12.2";

    public final TaskProvider<RunMinecraft> runCleanroomClient, runCleanroomServer;
    public final TaskProvider<NsightExec> runCleanroomNsightClient;

    public CleanroomTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla, MCPTasks mcp) {
        var mainSourceSet = project.getExtensions().getByType(SourceSetContainer.class).named(SourceSet.MAIN_SOURCE_SET_NAME);
        var runDir = project.getLayout().getProjectDirectory().dir("run").getAsFile();
        var forgeGroup = String.valueOf(project.getGroup());

        var assetsDir = ext.getCacheDirectory().dir("assets");
        var assetIndex = ext.getVersionMeta().map(VersionMeta::assetIndexId);
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var mcpToSrg = mcp.writeSrg2Mcp.flatMap(WriteMappings::getOutput);
        var mcpVersion = mcp.mcpConfig.map(CleanroomTasks::deriveMcpVersion);
        var mcpMappings = mcp.mcpMappings.map(CleanroomTasks::deriveMcpMappings);

        this.runCleanroomClient = Tasks.of(project, GROUP_NAME, "runCleanroomClient", RunMinecraft.class);
        this.runCleanroomServer = Tasks.of(project, GROUP_NAME, "runCleanroomServer", RunMinecraft.class);
        this.runCleanroomNsightClient = Tasks.of(project, GROUP_NAME, "runCleanroomNsightClient", NsightExec.class);

        this.runCleanroomClient.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets, vanilla.extractNatives, mcp.writeSrg2Mcp);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMainClass().set("com.cleanroommc.boot.MainClient");
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.getAssetIndexVersion().set(assetIndex);
            task.getVanillaAssetsLocation().set(assetsDir);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath));

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

            // Match the old ForgeGradle-era client run: extra mixin debugging flags.
            task.jvmArgs("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true");
        });

        this.runCleanroomServer.configure(task -> {
            task.dependsOn(mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets, vanilla.extractNatives, mcp.writeSrg2Mcp);

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMainClass().set("com.cleanroommc.boot.MainServer");
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath));

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

    private static String deriveMcpVersion(Configuration config) {
        var version = firstDependency(config).getVersion();
        if (version == null) {
            throw new IllegalStateException("mcpConfig dependency has no version to derive MCP_VERSION from.");
        }
        // e.g. "1.12.2-20260220.202731" -> "20260220.202731"
        var dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(dash + 1);
    }

    private static String deriveMcpMappings(Configuration config) {
        var dependency = firstDependency(config);
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

    private static Dependency firstDependency(Configuration config) {
        var dependencies = config.getAllDependencies();
        if (dependencies.isEmpty()) {
            // Realise any default dependencies declared via Configuration.defaultDependencies.
            config.getIncoming().getDependencies();
            dependencies = config.getAllDependencies();
        }
        if (dependencies.isEmpty()) {
            throw new IllegalStateException("Configuration '" + config.getName() + "' has no dependencies to derive mappings metadata from.");
        }
        return dependencies.iterator().next();
    }

}
