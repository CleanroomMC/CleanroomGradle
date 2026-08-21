package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.source.UserdevConfigValueSource;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.AccessTransform;
import com.cleanroommc.gradle.api.task.mcp.InjectMetadata;
import com.cleanroommc.gradle.api.task.mcp.MergeJars;
import com.cleanroommc.gradle.api.task.mcp.SplitJar;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches;
import com.cleanroommc.gradle.api.task.userdev.VerifyUserdevConfig;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.List;

/**
 * Registers the tasks that set up a mod developer's environment against Cleanroom.
 */
public final class UserDevTasks {

    private static final String GROUP_NAME = "cleanroom userdev";
    private static final String RUNS_GROUP_NAME = "cleanroom runs";
    private static final String USERDEV_JAR_NAME = "cleanroom-userdev.jar";

    public static final String CONFIGURATION_NAME = "cleanroomUserdev";
    public static final String LIBRARIES_CONFIGURATION_NAME = "cleanroomLibraries";

    public static NamedDomainObjectProvider<Configuration> configuration(Project project) {
        return Objects.config(project, CONFIGURATION_NAME);
    }

    public static boolean requested(Project project, CleanroomExtension ext) {
        if (ext.getVersion().isPresent()) {
            return true;
        }
        var configuration = project.getConfigurations().findByName(CONFIGURATION_NAME);
        return configuration != null && !configuration.getDependencies().isEmpty();
    }

    public final NamedDomainObjectProvider<Configuration> userdev, libraries;
    public final TaskProvider<Copy> copyUserdev, extractUserdev;
    public final TaskProvider<VerifyUserdevConfig> verifyUserdevConfig;
    public final TaskProvider<ApplyBinPatches> applyClientBinPatches, applyServerBinPatches;
    public final TaskProvider<SplitJar> splitDevClientJar, splitDevServerJar;
    public final TaskProvider<MergeJars> mergeDevJars;
    public final TaskProvider<InjectMetadata> injectDevMetadata;
    public final TaskProvider<AccessTransform> accessTransformDevJar, accessTransformDevMcpJar;
    public final TaskProvider<RenameJar> remapDevNotch2Srg, remapDevSrg2Mcp, remapCleanroomSrg2Mcp, reobfJar;
    public final TaskProvider<Decompile> decompileDevJar;
    public final TaskProvider<WriteMappings> writeMcp2Srg;
    public final TaskProvider<DefaultTask> setup;
    public final TaskProvider<RunMinecraft> runClient, runServer;

    public UserDevTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla, MCPTasks mcp) {
        var configurations = project.getConfigurations();
        var renamer = project.getExtensions().getByType(RenamerExtension.class);

        this.userdev = configurations.named(CONFIGURATION_NAME);
        this.userdev.configure(configuration -> {
            configuration.setDescription("The Cleanroom userdev artifact this environment is built from.");
            configuration.setTransitive(false);
            configuration.defaultDependencies(dependencies -> {
                var version = ext.getVersion().getOrNull();
                if (version != null) {
                    dependencies.add(project.getDependencies().create("com.cleanroommc:cleanroom:" + version + ":userdev@jar"));
                }
            });
        });

        this.libraries = Objects.config(project, LIBRARIES_CONFIGURATION_NAME);
        var userdevConfig = project.getProviders().of(UserdevConfigValueSource.class, spec ->
                spec.getParameters().getUserdevJar().fileProvider(this.userdev.map(Configuration::getSingleFile)));
        this.libraries.configure(configuration -> {
            configuration.setDescription("Libraries the Cleanroom itself needs, taken from the userdev artifact.");
            configuration.defaultDependencies(dependencies -> {
                for (var notation : userdevConfig.get().libraries()) {
                    if (!LwjglNatives.isForCurrentPlatform(notation)) {
                        continue;
                    }
                    dependencies.add(project.getDependencies().create(notation));
                }
            });
        });

        var accessTransformerTool = Objects.toolConfig(project, "accesstransformer", Meta.DEFAULT_TOOLS.get("accesstransformer"));

        var userdevDir = ext.getLocalCacheDirectory().dir("userdev");
        var userdevJar = userdevDir.map(dir -> dir.file(USERDEV_JAR_NAME));
        var extractedDir = userdevDir.map(dir -> dir.dir("extracted"));
        var mcpConfigDir = ext.getVersionCacheDirectory().dir("mcp_config/config");
        var mcpMappingsDir = mcp.extractMcpMappings.map(Copy::getDestinationDir);
        var srgMapping = ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg");
        var srg2mcp = mcp.writeSrg2Mcp.flatMap(WriteMappings::getOutput);
        var mainSourceSet = SourceSets.container(project).named(SourceSet.MAIN_SOURCE_SET_NAME);
        var jarTask = project.getTasks().named("jar", Jar.class);

        this.copyUserdev = Tasks.register(project, "copyUserdev", Copy.class);
        this.extractUserdev = Tasks.register(project, "extractUserdev", Copy.class);
        this.verifyUserdevConfig = Tasks.register(project, "verifyUserdevConfig", VerifyUserdevConfig.class);
        this.applyClientBinPatches = Tasks.register(project, "applyClientBinPatches", ApplyBinPatches.class);
        this.applyServerBinPatches = Tasks.register(project, "applyServerBinPatches", ApplyBinPatches.class);
        this.splitDevClientJar = Tasks.register(project, "splitDevClientJar", SplitJar.class);
        this.splitDevServerJar = Tasks.register(project, "splitDevServerJar", SplitJar.class);
        this.mergeDevJars = Tasks.tool(project, ext.getLocalCacheDirectory(), "mergeDevJars", MergeJars.class, configurations.getByName("mergetool"));
        this.remapDevNotch2Srg = project.getTasks().register("remapDevNotch2Srg", RenameJar.class, renamer);
        this.injectDevMetadata = Tasks.tool(project, ext.getLocalCacheDirectory(), "injectDevMetadata", InjectMetadata.class, configurations.getByName("mcinjector"));
        this.accessTransformDevJar = Tasks.tool(project, ext.getLocalCacheDirectory(), "accessTransformDevJar", AccessTransform.class, accessTransformerTool);
        this.remapDevSrg2Mcp = project.getTasks().register("remapDevSrg2Mcp", RenameJar.class, renamer);
        this.accessTransformDevMcpJar = Tasks.tool(project, ext.getLocalCacheDirectory(), "accessTransformDevMcpJar", AccessTransform.class, accessTransformerTool);
        this.remapCleanroomSrg2Mcp = project.getTasks().register("remapCleanroomSrg2Mcp", RenameJar.class, renamer);
        this.decompileDevJar = Tasks.tool(project, ext.getLocalCacheDirectory(), "decompileDevJar", Decompile.class, configurations.getByName("decompiler"));
        this.writeMcp2Srg = Tasks.register(project, "writeMcp2Srg", WriteMappings.class);
        this.reobfJar = project.getTasks().register("reobfJar", RenameJar.class, renamer);
        this.setup = Tasks.register(project, "setup");
        this.runClient = Tasks.register(project, "runClient", RunMinecraft.class);
        this.runServer = Tasks.register(project, "runServer", RunMinecraft.class);
        Tasks.group(GROUP_NAME, this.setup, this.decompileDevJar);
        Tasks.group(RUNS_GROUP_NAME, this.runClient, this.runServer);
        Tasks.group("build", this.reobfJar);

        this.copyUserdev.configure(task -> {
            task.setDescription("Places the userdev artifact at a stable path so the rest of the pipeline can name it.");

            task.from(this.userdev);
            task.into(userdevDir);
            task.rename(name -> USERDEV_JAR_NAME);
        });
        this.extractUserdev.configure(task -> {
            task.dependsOn(this.copyUserdev);

            task.from(project.zipTree(userdevJar));
            task.into(extractedDir);
        });
        this.verifyUserdevConfig.configure(task -> {
            task.dependsOn(this.extractUserdev);

            task.getConfigFile().set(extractedDir.map(dir -> dir.file(UserdevConfig.FILE_NAME)));
            task.getMcpConfig().set(mcp.mcpConfig.map(Objects::notation));
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getStamp().set(userdevDir.map(dir -> dir.file("verified.txt")));
        });
        this.applyClientBinPatches.configure(task -> {
            task.dependsOn(this.extractUserdev);

            task.getOriginalJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getBinpatches().set(extractedDir.map(dir -> dir.file(DistributionTasks.USERDEV_BINPATCHES)));
            task.getPrefix().set(DistributionTasks.USERDEV_CLIENT_BINPATCHES);
            task.getPatchedJar().set(userdevDir.map(dir -> dir.file("client-patched.jar")));
        });
        this.applyServerBinPatches.configure(task -> {
            task.dependsOn(this.extractUserdev);

            task.getOriginalJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getBinpatches().set(extractedDir.map(dir -> dir.file(DistributionTasks.USERDEV_BINPATCHES)));
            task.getPrefix().set(DistributionTasks.USERDEV_SERVER_BINPATCHES);
            task.getPatchedJar().set(userdevDir.map(dir -> dir.file("server-patched.jar")));
        });
        this.splitDevClientJar.configure(task -> {
            task.dependsOn(mcp.extractMcpConfig);

            task.getSourceJar().set(this.applyClientBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(userdevDir.map(dir -> dir.file("client-slim.jar")));
            task.getExtraJar().set(userdevDir.map(dir -> dir.file("client-extra.jar")));
        });
        this.splitDevServerJar.configure(task -> {
            task.dependsOn(mcp.extractMcpConfig);

            task.getSourceJar().set(this.applyServerBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getSlimJar().set(userdevDir.map(dir -> dir.file("server-slim.jar")));
            task.getExtraJar().set(userdevDir.map(dir -> dir.file("server-extra.jar")));
        });
        this.mergeDevJars.configure(task -> {
            task.getClientJar().value(this.splitDevClientJar.flatMap(SplitJar::getSlimJar));
            task.getServerJar().value(this.splitDevServerJar.flatMap(SplitJar::getSlimJar));
            task.getSrgMappingFile().value(srgMapping);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getMergedJar().set(userdevDir.map(dir -> dir.file("merged.jar")));
        });
        this.remapDevNotch2Srg.configure(task -> {
            task.setDescription("Renames the patched Minecraft from obfuscated to SRG names.");
            task.dependsOn(this.verifyUserdevConfig);

            task.getInput().set(this.mergeDevJars.flatMap(MergeJars::getMergedJar));
            task.getMap().setFrom(srgMapping);
            // setFrom, not from: the renamer plugin's convention is this project's compile classpath, which the
            // environment being built is itself part of.
            task.getLibraries().setFrom(vanilla.vanillaConfig);
            task.getOutput().set(userdevDir.map(dir -> dir.file("minecraft-srg.jar")));
        });
        this.injectDevMetadata.configure(task -> {
            task.getLogFile().convention(userdevDir.map(dir -> dir.file("mcinjector.log")));
            task.getSrgJar().set(this.remapDevNotch2Srg.flatMap(RenameJar::getOutput));
            task.getAccessFile().set(mcpConfigDir.map(dir -> dir.file("access.txt")));
            task.getConstructorsFile().set(mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getExceptionsFile().set(mcpConfigDir.map(dir -> dir.file("exceptions.txt")));
            task.getInjectedJar().set(userdevDir.map(dir -> dir.file("minecraft-injected.jar")));
        });
        this.accessTransformDevJar.configure(task -> {
            task.dependsOn(this.extractUserdev);
            task.setDescription("Applies SRG-named access transformers to Minecraft.");

            task.getInputJar().set(this.injectDevMetadata.flatMap(InjectMetadata::getInjectedJar));
            task.getAccessTransformers().from(
                    project.fileTree(extractedDir.map(dir -> dir.dir(DistributionTasks.USERDEV_ATS))),
                    ext.getAccessTransformers());
            task.getOutputJar().set(userdevDir.map(dir -> dir.file("minecraft-srg-at.jar")));
        });
        this.remapDevSrg2Mcp.configure(task -> {
            task.setDescription("Renames the patched Minecraft into this project's MCP names.");

            task.getInput().set(this.accessTransformDevJar.flatMap(AccessTransform::getOutputJar));
            task.getMap().setFrom(srg2mcp);
            task.getLibraries().setFrom(vanilla.vanillaConfig);
            task.getOutput().set(userdevDir.map(dir -> dir.file("minecraft-mcp-pre-at.jar")));
        });
        this.accessTransformDevMcpJar.configure(task -> {
            task.dependsOn(this.extractUserdev);
            task.setDescription("Applies MCP-named access transformers to Minecraft.");

            task.getInputJar().set(this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput));
            task.getAccessTransformers().from(
                    project.fileTree(extractedDir.map(dir -> dir.dir(DistributionTasks.USERDEV_ATS))), ext.getAccessTransformers()
            );
            task.getOutputJar().set(userdevDir.map(dir -> dir.file("minecraft-mcp.jar")));
        });
        this.remapCleanroomSrg2Mcp.configure(task -> {
            task.setDescription("Renames the loader into this project's MCP names.");
            task.dependsOn(this.copyUserdev);

            task.getInput().set(userdevJar);
            task.getMap().setFrom(srg2mcp);
            // Minecraft is a library here, at the same naming level as the input
            task.getLibraries().setFrom(this.injectDevMetadata.flatMap(InjectMetadata::getInjectedJar), vanilla.vanillaConfig);
            task.getOutput().set(userdevDir.map(dir -> dir.file("cleanroom-mcp.jar")));
        });
        this.decompileDevJar.configure(task -> {
            task.setDescription("Decompiles the environment's Minecraft for source browsing in an IDE.");

            task.getJavaLauncher().convention(Providers.javaLauncher(project, 25));
            task.getLogFile().convention(userdevDir.map(dir -> dir.file("decompile.log")));
            task.getCompiledJar().set(this.accessTransformDevMcpJar.flatMap(AccessTransform::getOutputJar));
            task.getLibraries().from(vanilla.vanillaConfig, this.libraries);
            // IDEs look for <jar>-sources.jar next to the jar itself
            task.getDecompiledJar().fileProvider(userdevDir.map(dir -> dir.file("minecraft-mcp-sources.jar").getAsFile()));
        });
        this.writeMcp2Srg.configure(task -> {
            task.dependsOn(mcp.extractMcpConfig);

            task.getJoinedSrgFile().set(srgMapping);
            task.getMethodMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "fields.csv")));
            task.getTinyMappings().fileProvider(mcp.tinyFileWhenPresent);
            task.getNamesId().set(mcp.activeNamesId);
            task.getDirection().set(WriteMappings.Direction.MCP_TO_SRG);
            task.getFormat().set(IMappingFile.Format.TSRG);
            task.getOutput().set(ext.getLocalCacheDirectory().file("mappings/mcp2srg.tsrg"));
        });
        this.reobfJar.configure(task -> {
            task.setDescription("Renames this project's jar from MCP to SRG names, as a release build expects.");

            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Srg.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(jarTask.flatMap(jar -> jar.getDestinationDirectory()
                    .file(jar.getArchiveBaseName().zip(jar.getArchiveVersion(), (base, jarVersion) -> base + "-" + jarVersion + "-srg.jar"))));
        });
        // A mod's publishable artifact is the SRG-named one, the jar task's own output only runs in a dev environment
        project.getTasks().named("assemble").configure(task -> task.dependsOn(this.reobfJar));
        this.setup.configure(task -> {
            task.setDescription("Create the Cleanroom development environment.");
            task.dependsOn(this.accessTransformDevMcpJar, this.remapCleanroomSrg2Mcp);
        });

        var runDir = project.getLayout().getProjectDirectory().dir("run").getAsFile();
        var assetsDir = ext.getCacheDirectory().dir("assets");
        var assetIndex = ext.getVersionMeta().map(VersionMeta::assetIndexId);
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var srg2mcpMapping = mcp.writeSrg2Mcp.flatMap(WriteMappings::getOutput);
        var client = userdevConfig.map(config -> config.runs().client());
        var server = userdevConfig.map(config -> config.runs().server());

        this.runClient.configure(task -> {
            task.dependsOn(this.setup, mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMainClass().set(client.map(UserdevConfig.Run::mainClass));
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.getAssetIndexVersion().set(assetIndex);
            task.getVanillaAssetsLocation().set(assetsDir);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath));

            task.environment("target", client.map(UserdevConfig.Run::target));
            task.environment("tweakClass", client.map(UserdevConfig.Run::tweakClass));
            task.environment("mainClass", client.map(UserdevConfig.Run::launchClass));
            task.environment("assetIndex", assetIndex);
            task.environment("assetDirectory", assetsDir);
            task.environment("nativesDirectory", natives);
            task.environment("MC_VERSION", vanilla.minecraftVersion);
            task.environment("MCP_VERSION", mcp.mcpVersionId);
            task.environment("MCP_MAPPINGS", mcp.mcpMappingsId);
            task.environment("MCP_TO_SRG", srg2mcpMapping);
            task.environment("FORGE_GROUP", userdevConfig.map(UserdevConfig::group));
            task.environment("FORGE_VERSION", userdevConfig.map(UserdevConfig::forgeVersion));

            task.jvmArgs("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true");
        });
        this.runServer.configure(task -> {
            task.dependsOn(this.setup, mainSourceSet.map(SourceSet::getClassesTaskName));

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMainClass().set(server.map(UserdevConfig.Run::mainClass));
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(mainSourceSet.map(SourceSet::getRuntimeClasspath),
                    this.splitDevServerJar.flatMap(SplitJar::getExtraJar));

            task.environment("target", server.map(UserdevConfig.Run::target));
            task.environment("tweakClass", server.map(UserdevConfig.Run::tweakClass));
            task.environment("mainClass", server.map(UserdevConfig.Run::launchClass));
            task.environment("MC_VERSION", vanilla.minecraftVersion);
            task.environment("MCP_VERSION", mcp.mcpVersionId);
            task.environment("MCP_MAPPINGS", mcp.mcpMappingsId);
            task.environment("MCP_TO_SRG", srg2mcpMapping);
            task.environment("FORGE_GROUP", userdevConfig.map(UserdevConfig::group));
            task.environment("FORGE_VERSION", userdevConfig.map(UserdevConfig::forgeVersion));
        });

        SourceSets.extendFromConfiguration(project, mainSourceSet, vanilla.vanillaConfig);
        SourceSets.extendFromConfiguration(project, mainSourceSet, this.libraries);

        var dependencies = project.getDependencies();
        dependencies.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, project.files(
                this.accessTransformDevMcpJar.flatMap(AccessTransform::getOutputJar),
                this.remapCleanroomSrg2Mcp.flatMap(RenameJar::getOutput)));
        dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(
                this.splitDevClientJar.flatMap(SplitJar::getExtraJar)));

        var intermediates = IntermediateProcessor.of(project);
        intermediates.discardAfter(this.splitDevClientJar, this.applyClientBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
        intermediates.discardAfter(this.splitDevServerJar, this.applyServerBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
        intermediates.discardAfterAll("discardDevSlimJars", List.of(this.mergeDevJars),
                this.splitDevClientJar.flatMap(SplitJar::getSlimJar),
                this.splitDevServerJar.flatMap(SplitJar::getSlimJar)
        );
        intermediates.discardAfter(this.remapDevNotch2Srg, this.mergeDevJars.flatMap(MergeJars::getMergedJar));
        intermediates.discardAfter(this.injectDevMetadata, this.remapDevNotch2Srg.flatMap(RenameJar::getOutput));
        intermediates.discardAfterAll("discardDevInjectedJar",
                List.of(this.accessTransformDevJar, this.remapCleanroomSrg2Mcp),
                this.injectDevMetadata.flatMap(InjectMetadata::getInjectedJar)
        );
        intermediates.discardAfter(this.remapDevSrg2Mcp, this.accessTransformDevJar.flatMap(AccessTransform::getOutputJar));
        intermediates.discardAfter(this.accessTransformDevMcpJar, this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput));
    }

}
