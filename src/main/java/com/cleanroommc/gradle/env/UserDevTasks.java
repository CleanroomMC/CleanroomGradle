package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.ext.UserdevExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.source.UserdevConfigValueSource;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.*;
import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
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
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

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

    public static boolean requested(Project project, UserdevExtension userdev) {
        if (userdev.getVersion().isPresent()) {
            return true;
        }
        var configuration = project.getConfigurations().findByName(CONFIGURATION_NAME);
        return configuration != null && !configuration.getDependencies().isEmpty();
    }

    public final NamedDomainObjectProvider<Configuration> userdev, libraries;
    public final TaskProvider<Copy> copyUserdev, extractUserdev;
    public final TaskProvider<VerifyUserdevConfig> verifyUserdevConfig;
    public final TaskProvider<ApplyBinPatches> applyClientBinPatches, applyServerBinPatches;
    public final MinecraftJarPipeline jars;
    public final TaskProvider<AccessTransform> accessTransformDevJar;
    public final TaskProvider<StripSideOnlyJar> stripClientDevMinecraftJar, stripServerDevMinecraftJar;
    public final TaskProvider<RenameJar> remapCleanroomSrg2Notch, remapDevSrg2Mcp, remapCleanroomSrg2Mcp, reobfJar;
    public final TaskProvider<Decompile> decompileDevJar;
    public final TaskProvider<WriteMappings> writeMcp2Srg;
    public final TaskProvider<DefaultTask> setup;
    public final TaskProvider<RunMinecraft> runClient, runServer;

    public UserDevTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, UserdevExtension userdevExt,
                        VanillaTasks vanilla, McpMappings mappings, IntermediateProcessor intermediates) {
        var configurations = project.getConfigurations();
        var renamer = project.getExtensions().getByType(RenamerExtension.class);
        var offline = project.getGradle().getStartParameter().isOffline();

        this.userdev = configurations.named(CONFIGURATION_NAME);
        var factory = project.getDependencyFactory();
        this.userdev.configure(configuration -> {
            configuration.setDescription("The Cleanroom userdev artifact this environment is built from.");
            configuration.setTransitive(false);
            var version = userdevExt.getVersion();
            configuration.defaultDependencies(dependencies -> {
                if (version.isPresent()) {
                    dependencies.add(factory.create("com.cleanroommc:cleanroom:" + version.get() + ":userdev@jar"));
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
                    dependencies.add(factory.create(notation));
                }
            });
        });

        var accessTransformerTool = ToolConfigs.get(project, "accesstransformer");
        var userdevDir = caches.getLocalDirectory().dir("userdev");
        var userdevJar = userdevDir.map(dir -> dir.file(USERDEV_JAR_NAME));
        var extractedDir = userdevDir.map(dir -> dir.dir("extracted"));
        var srg2mcp = mappings.writeSrg2Mcp.flatMap(WriteMappings::getOutput);
        var mainSourceSet = SourceSets.container(project).named(SourceSet.MAIN_SOURCE_SET_NAME);
        var jarTask = project.getTasks().named("jar", Jar.class);

        this.copyUserdev = Tasks.register(project, "copyUserdev", Copy.class);
        this.extractUserdev = Tasks.register(project, "extractUserdev", Copy.class);
        this.verifyUserdevConfig = Tasks.register(project, "verifyUserdevConfig", VerifyUserdevConfig.class);
        this.applyClientBinPatches = Tasks.register(project, "applyClientBinPatches", ApplyBinPatches.class);
        this.applyServerBinPatches = Tasks.register(project, "applyServerBinPatches", ApplyBinPatches.class);

        var spec = new MinecraftJarPipeline.Spec();
        spec.splitClientName = "splitDevClientJar";
        spec.splitServerName = "splitDevServerJar";
        spec.mergeName = "mergeDevJars";
        spec.remapName = "remapDevNotch2Srg";
        spec.injectName = "injectDevMetadata";
        spec.bindClientJar = property -> property.set(this.applyClientBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
        spec.bindServerJar = property -> property.set(this.applyServerBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
        spec.srgMapping = mappings.joinedSrg;
        spec.access = mappings.access;
        spec.constructors = mappings.constructors;
        spec.exceptions = mappings.exceptions;
        spec.minecraftVersion = vanilla.minecraftVersion;
        spec.libraries = vanilla.vanillaConfig;
        spec.extractMcpConfig = mappings.extractMcpConfig;
        spec.clientSlim = userdevDir.map(dir -> dir.file(MinecraftJarPipeline.CLIENT_SLIM_JAR));
        spec.clientExtra = userdevDir.map(dir -> dir.file(MinecraftJarPipeline.CLIENT_EXTRA_JAR));
        spec.serverSlim = userdevDir.map(dir -> dir.file(MinecraftJarPipeline.SERVER_SLIM_JAR));
        spec.serverExtra = userdevDir.map(dir -> dir.file(MinecraftJarPipeline.SERVER_EXTRA_JAR));
        spec.mergedJar = userdevDir.map(dir -> dir.file(MinecraftJarPipeline.MERGED_JAR));
        spec.srgJar = userdevDir.map(dir -> dir.file("minecraft-srg.jar"));
        spec.injectedJar = userdevDir.map(dir -> dir.file("minecraft-injected.jar"));
        this.jars = MinecraftJarPipeline.register(project, caches, spec);

        this.accessTransformDevJar = Tasks.tool(project, caches.getLocalDirectory(), "accessTransformDevJar", AccessTransform.class, accessTransformerTool);
        this.stripClientDevMinecraftJar = Tasks.register(project, "stripClientDevMinecraftJar", StripSideOnlyJar.class);
        this.stripServerDevMinecraftJar = Tasks.register(project, "stripServerDevMinecraftJar", StripSideOnlyJar.class);
        this.remapCleanroomSrg2Notch = Tasks.register(project, "remapCleanroomSrg2Notch", RenameJar.class, renamer);
        this.remapDevSrg2Mcp = Tasks.register(project, "remapDevSrg2Mcp", RenameJar.class, renamer);
        this.remapCleanroomSrg2Mcp = Tasks.register(project, "remapCleanroomSrg2Mcp", RenameJar.class, renamer);
        this.decompileDevJar = Tasks.tool(project, caches.getLocalDirectory(), "decompileDevJar", Decompile.class, ToolConfigs.get(project, "decompiler"));
        this.writeMcp2Srg = mappings.write(project, caches, "writeMcp2Srg", WriteMappings.Direction.MCP_TO_SRG,
                UserdevConfig.MCP2SRG);
        this.reobfJar = Tasks.register(project, "reobfJar", RenameJar.class, renamer);
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
            task.from(Tasks.archives(project).zipTree(userdevJar));
            task.into(extractedDir);
        });
        this.verifyUserdevConfig.configure(task -> {
            task.dependsOn(this.extractUserdev);
            task.getConfigFile().set(extractedDir.map(dir -> dir.file(UserdevConfig.meta(UserdevConfig.FILE_NAME))));
            task.getMcpConfig().set(mappings.mcpConfig.map(Objects::notation));
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getStamp().set(userdevDir.map(dir -> dir.file("verified.txt")));
        });
        this.applyClientBinPatches.configure(task -> {
            task.dependsOn(this.extractUserdev);
            task.getOriginalJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getBinpatches().set(extractedDir.map(dir -> dir.file(UserdevConfig.meta(UserdevConfig.BINPATCHES))));
            task.getPrefix().set(UserdevConfig.CLIENT_BINPATCHES);
            task.getPatchedJar().set(userdevDir.map(dir -> dir.file("client-patched.jar")));
        });
        this.applyServerBinPatches.configure(task -> {
            task.dependsOn(this.extractUserdev);
            task.getOriginalJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getBinpatches().set(extractedDir.map(dir -> dir.file(UserdevConfig.meta(UserdevConfig.BINPATCHES))));
            task.getPrefix().set(UserdevConfig.SERVER_BINPATCHES);
            task.getPatchedJar().set(userdevDir.map(dir -> dir.file("server-patched.jar")));
        });
        this.jars.remapNotch2Srg.configure(task -> {
            task.setDescription("Renames the patched Minecraft from obfuscated to SRG names.");
            task.dependsOn(this.verifyUserdevConfig, this.remapCleanroomSrg2Notch);
            task.getLibraries().from(this.remapCleanroomSrg2Notch.flatMap(RenameJar::getOutput));
        });
        this.remapCleanroomSrg2Notch.configure(task -> {
            task.setDescription("Renames the loader into obfuscated names for hierarchy-aware Minecraft remapping.");
            task.dependsOn(this.copyUserdev, mappings.extractMcpConfig);
            task.getInput().set(userdevJar);
            task.getMap().setFrom(mappings.joinedSrg);
            task.getLibraries().setFrom();
            task.getReverse().set(true);
            task.getNaiveSrg().set(true);
            task.getOutput().set(userdevDir.map(dir -> dir.file("cleanroom-notch.jar")));
        });
        this.accessTransformDevJar.configure(task -> {
            task.dependsOn(this.extractUserdev);
            task.setDescription("Applies SRG-named access transformers to Minecraft before it is remapped to MCP names.");
            task.getInputJar().set(this.jars.inject.flatMap(InjectMetadata::getInjectedJar));
            task.getAccessTransformers().from(
                    extractedDir.map(dir -> dir.dir(UserdevConfig.meta(UserdevConfig.ATS)).getAsFileTree()),
                    userdevExt.getAccessTransformers());
            task.getOutputJar().set(userdevDir.map(dir -> dir.file("minecraft-srg-at.jar")));
        });
        this.remapDevSrg2Mcp.configure(task -> {
            task.setDescription("Renames the patched Minecraft into this project's MCP names.");
            task.getInput().set(this.accessTransformDevJar.flatMap(AccessTransform::getOutputJar));
            task.getMap().setFrom(srg2mcp);
            task.getLibraries().setFrom(vanilla.vanillaConfig, this.userdev);
            task.getOutput().set(userdevDir.map(dir -> dir.file("minecraft-mcp.jar")));
        });
        this.stripClientDevMinecraftJar.configure(task -> {
            task.getInputJar().set(this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.CLIENT);
            task.getOutputJar().set(userdevDir.map(dir -> dir.file("minecraft-client-mcp.jar")));
        });
        this.stripServerDevMinecraftJar.configure(task -> {
            task.getInputJar().set(this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.SERVER);
            task.getOutputJar().set(userdevDir.map(dir -> dir.file("minecraft-server-mcp.jar")));
        });
        this.remapCleanroomSrg2Mcp.configure(task -> {
            task.setDescription("Renames the loader into this project's MCP names.");
            task.dependsOn(this.copyUserdev);
            task.getInput().set(userdevJar);
            task.getMap().setFrom(srg2mcp);
            task.getLibraries().setFrom(this.jars.inject.flatMap(InjectMetadata::getInjectedJar), vanilla.vanillaConfig);
            task.getOutput().set(userdevDir.map(dir -> dir.file("cleanroom-mcp.jar")));
        });
        this.decompileDevJar.configure(task -> {
            task.setDescription("Decompiles the environment's Minecraft for source browsing in an IDE.");
            task.getJavaLauncher().convention(Providers.javaLauncher(project));
            task.getLogFile().convention(userdevDir.map(dir -> dir.file("decompile.log")));
            task.getCompiledJar().set(this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput));
            task.getLibraries().from(vanilla.vanillaConfig, this.libraries);
            task.getDecompiledJar().fileProvider(userdevDir.map(dir -> dir.file("minecraft-mcp-sources.jar").getAsFile()));
        });
        this.reobfJar.configure(task -> {
            task.setDescription("Renames this project's jar from MCP to SRG names, as a release build expects.");
            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Srg.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(jarTask.flatMap(jar -> jar.getDestinationDirectory()
                    .file(jar.getArchiveBaseName().zip(jar.getArchiveVersion(), (base, jarVersion) -> base + "-" + jarVersion + "-srg.jar"))));
        });
        project.getTasks().named("assemble").configure(task -> task.dependsOn(this.reobfJar));
        this.setup.configure(task -> {
            task.setDescription("Create the Cleanroom development environment.");
            task.dependsOn(this.remapDevSrg2Mcp, this.remapCleanroomSrg2Mcp);
        });

        var objects = project.getObjects();
        var mainRuntimeClasspath = objects.fileCollection().from(mainSourceSet.map(SourceSet::getRuntimeClasspath));
        var minecraftMcpClasspath = objects.fileCollection().from(this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput));
        var clientExtraClasspath = objects.fileCollection().from(this.jars.splitClient.flatMap(SplitJar::getExtraJar));
        var commonRunClasspath = mainRuntimeClasspath.minus(minecraftMcpClasspath);
        var runDir = project.getLayout().getProjectDirectory().dir("run").getAsFile();
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var client = userdevConfig.map(config -> config.runs().client());
        var server = userdevConfig.map(config -> config.runs().server());
        var fml = new MinecraftRuns.Fml();
        fml.minecraftVersion = vanilla.minecraftVersion;
        fml.mcpVersion = mappings.mcpVersionId;
        fml.mcpMappings = mappings.mcpMappingsId;
        fml.mcpToSrg = srg2mcp;
        fml.forgeGroup = userdevConfig.map(UserdevConfig::group);
        fml.forgeVersion = userdevConfig.map(UserdevConfig::forgeVersion);
        fml.assetIndex = minecraft.getVersionMeta().map(VersionMeta::assetIndexId);
        fml.assets = caches.getDirectory().dir("assets");
        fml.natives = natives;

        this.runClient.configure(task -> {
            task.dependsOn(this.setup, this.stripClientDevMinecraftJar,
                    mainSourceSet.map(SourceSet::getClassesTaskName), vanilla.downloadAssets);
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getMainClass().set(client.map(UserdevConfig.Run::mainClass));
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(commonRunClasspath, this.stripClientDevMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            fml.client = true;
            fml.target = client.map(UserdevConfig.Run::target);
            fml.tweakClass = client.map(UserdevConfig.Run::tweakClass);
            fml.launchClass = client.map(UserdevConfig.Run::launchClass);
            MinecraftRuns.fmlEnvironment(task, fml);
        });
        this.runServer.configure(task -> {
            task.dependsOn(this.setup, this.stripServerDevMinecraftJar, mainSourceSet.map(SourceSet::getClassesTaskName));
            MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.CLEANROOM);
            task.getMinecraftVersion().set(vanilla.minecraftVersion);
            task.getMainClass().set(server.map(UserdevConfig.Run::mainClass));
            task.setWorkingDir(runDir);
            task.getNatives().fileProvider(natives);
            task.classpath(commonRunClasspath.minus(clientExtraClasspath),
                    this.stripServerDevMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar),
                    this.jars.splitServer.flatMap(SplitJar::getExtraJar));
            var serverEnv = new MinecraftRuns.Fml();
            serverEnv.client = false;
            serverEnv.target = server.map(UserdevConfig.Run::target);
            serverEnv.tweakClass = server.map(UserdevConfig.Run::tweakClass);
            serverEnv.launchClass = server.map(UserdevConfig.Run::launchClass);
            serverEnv.minecraftVersion = vanilla.minecraftVersion;
            serverEnv.mcpVersion = mappings.mcpVersionId;
            serverEnv.mcpMappings = mappings.mcpMappingsId;
            serverEnv.mcpToSrg = srg2mcp;
            serverEnv.forgeGroup = userdevConfig.map(UserdevConfig::group);
            serverEnv.forgeVersion = userdevConfig.map(UserdevConfig::forgeVersion);
            MinecraftRuns.fmlEnvironment(task, serverEnv);
        });

        SourceSets.extendFromConfiguration(project, mainSourceSet, vanilla.vanillaConfig);
        SourceSets.extendFromConfiguration(project, mainSourceSet, this.libraries);

        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, objects.fileCollection().from(
                this.remapDevSrg2Mcp.flatMap(RenameJar::getOutput),
                this.remapCleanroomSrg2Mcp.flatMap(RenameJar::getOutput)));
        project.getDependencies().add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, objects.fileCollection().from(
                this.jars.splitClient.flatMap(SplitJar::getExtraJar)));

        intermediates.discardAfter(this.jars.splitClient, this.applyClientBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
        intermediates.discardAfter(this.jars.splitServer, this.applyServerBinPatches.flatMap(ApplyBinPatches::getPatchedJar));
        intermediates.discardAfterAll("discardDevSlimJars", List.of(this.jars.merge),
                this.jars.splitClient.flatMap(SplitJar::getSlimJar),
                this.jars.splitServer.flatMap(SplitJar::getSlimJar)
        );
        intermediates.discardAfter(this.jars.remapNotch2Srg,
                this.jars.merge.flatMap(MergeJars::getMergedJar),
                this.remapCleanroomSrg2Notch.flatMap(RenameJar::getOutput));
        intermediates.discardAfter(this.jars.inject, this.jars.remapNotch2Srg.flatMap(RenameJar::getOutput));
        intermediates.discardAfterAll("discardDevInjectedJar",
                List.of(this.accessTransformDevJar, this.remapCleanroomSrg2Mcp),
                this.jars.inject.flatMap(InjectMetadata::getInjectedJar)
        );
        intermediates.discardAfter(this.remapDevSrg2Mcp, this.accessTransformDevJar.flatMap(AccessTransform::getOutputJar));
    }

}
