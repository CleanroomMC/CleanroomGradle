package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.DeobfExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.userdev.ExtractUserdevFile;
import com.cleanroommc.gradle.api.userdev.ExtractUserdevExtra;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevClasses;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevSources;
import com.cleanroommc.gradle.api.userdev.UserdevAttributes;
import com.cleanroommc.gradle.api.userdev.UserdevDependency;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.Platform;
import org.apache.commons.lang3.StringUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.util.List;

public final class UserDevTasks {

    private static final String RUNS_GROUP = "cleanroom runs";

    public final NamedDomainObjectProvider<Configuration> clientExtra, serverExtra, natives;
    public final TaskProvider<ExtractUserdevFile> extractMcpToSrg, extractSrgToMcp;
    public final TaskProvider<RenameJar> reobfJar;
    public final TaskProvider<RunMinecraft> runClient, runServer;

    public UserDevTasks(Project project, CachesExtension caches, MinecraftExtension minecraft,
                        UserdevDependency userdev, VanillaTasks vanilla) {
        var hierarchy = hierarchyConfiguration(project, userdev);
        registerTransforms(project, userdev, caches, hierarchy);

        this.clientExtra = sideConfiguration(project, userdev, UserdevAttributes.CLIENT_EXTRA);
        this.serverExtra = sideConfiguration(project, userdev, UserdevAttributes.SERVER_EXTRA);
        this.natives = nativesConfiguration(project, userdev);

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var main = java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);
        var jar = project.getTasks().named("jar", Jar.class);
        var renamer = project.getExtensions().getByType(RenamerExtension.class);
        var config = userdev.getConfig();

        this.extractMcpToSrg = Tasks.register(project, "extractUserdevMcpToSrg", ExtractUserdevFile.class);
        this.extractMcpToSrg.configure(task -> {
            task.getUserdevArtifact().set(userdev.getRawArtifact());
            task.getEntryPath().set(userdev.getMcpToSrgPath());
            task.getOutput().set(caches.getLocalDirectory().file("userdev/mcp2srg.tsrg"));
        });
        this.extractSrgToMcp = Tasks.register(project, "extractUserdevSrgToMcp", ExtractUserdevFile.class);
        this.extractSrgToMcp.configure(task -> {
            task.getUserdevArtifact().set(userdev.getRawArtifact());
            task.getEntryPath().set(userdev.getSrgToMcpPath());
            task.getOutput().set(caches.getLocalDirectory().file("userdev/srg2mcp.tsrg"));
        });
        var deobf = project.getExtensions().getByType(DeobfExtension.class);
        deobf.useUserdev(userdev.getRawConfiguration());
        deobf.getSrgLibraries().from(hierarchy);

        this.reobfJar = Tasks.register(project, "reobfJar", RenameJar.class, renamer);
        this.reobfJar.configure(task -> {
            task.setGroup("build");
            task.setDescription("Renames this project's jar from MCP to SRG names.");
            task.getInput().set(jar.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.extractMcpToSrg.flatMap(ExtractUserdevFile::getOutput));
            task.getLibraries().setFrom(main.map(SourceSet::getCompileClasspath));
            task.getOutput().set(jar.flatMap(value -> value.getDestinationDirectory().file(
                    value.getArchiveBaseName().zip(value.getArchiveVersion(),
                            (base, version) -> base + "-" + version + "-srg.jar"))));
        });
        project.getTasks().named("assemble").configure(task -> task.dependsOn(this.reobfJar));

        this.runClient = Tasks.register(project, "runClient", RunMinecraft.class);
        this.runServer = Tasks.register(project, "runServer", RunMinecraft.class);
        Tasks.group(RUNS_GROUP, this.runClient, this.runServer);

        var offline = project.getGradle().getStartParameter().isOffline();
        var runDirectory = project.getLayout().getProjectDirectory().dir("run").getAsFile();
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var runtimeClasspath = project.getObjects().fileCollection().from(main.map(SourceSet::getRuntimeClasspath));
        var client = config.map(value -> value.runs().client());
        var server = config.map(value -> value.runs().server());
        var fml = new MinecraftRuns.Fml();
        fml.minecraftVersion = userdev.getMinecraftVersion();
        fml.mcpVersion = config.map(value -> McpMappings.mcpVersionId(value.inputs().mcpConfig()));
        fml.mcpMappings = config.map(value -> McpMappings.mcpMappingsId(value.inputs().mappings()));
        fml.srgToMcp = this.extractSrgToMcp.flatMap(ExtractUserdevFile::getOutput);
        fml.forgeGroup = config.map(value -> value.loader().group());
        fml.forgeVersion = config.map(value -> value.loader().forgeVersion());
        fml.assetIndex = minecraft.getVersionMeta().map(meta -> meta.assetIndexId());
        fml.assets = caches.getDirectory().dir("assets");
        fml.natives = natives;

        this.runClient.configure(task -> {
            task.dependsOn(main.map(SourceSet::getClassesTaskName), vanilla.downloadAssets);
            configureRun(task, Side.CLIENT, client, userdev, caches, minecraft, offline, runDirectory, natives);
            task.classpath(runtimeClasspath, this.clientExtra, this.natives);
            MinecraftRuns.fmlEnvironment(task, fml.forSide(true, client.map(UserdevConfig.Run::target),
                    client.map(UserdevConfig.Run::tweakClass), client.map(UserdevConfig.Run::launchClass)));
        });
        this.runServer.configure(task -> {
            task.dependsOn(main.map(SourceSet::getClassesTaskName));
            configureRun(task, Side.SERVER, server, userdev, caches, minecraft, offline, runDirectory, natives);
            task.classpath(runtimeClasspath, this.serverExtra, this.natives);
            MinecraftRuns.fmlEnvironment(task, fml.forSide(false, server.map(UserdevConfig.Run::target),
                    server.map(UserdevConfig.Run::tweakClass), server.map(UserdevConfig.Run::launchClass)));
        });

        project.getPluginManager().withPlugin("idea", _ -> project.getExtensions()
                .getByType(IdeaModel.class).getModule().setDownloadSources(true));
    }

    private static void configureRun(RunMinecraft task, Side side,
                                     Provider<UserdevConfig.Run> run,
                                     UserdevDependency userdev, CachesExtension caches, MinecraftExtension minecraft,
                                     boolean offline, File runDirectory, Provider<File> natives) {
        MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
        task.getSide().set(side);
        task.getEnv().set(Environment.CLEANROOM);
        task.getMinecraftVersion().set(userdev.getMinecraftVersion());
        task.getMainClass().set(run.map(UserdevConfig.Run::mainClass));
        task.setWorkingDir(runDirectory);
        task.getNatives().fileProvider(natives);
    }

    private static NamedDomainObjectProvider<Configuration> sideConfiguration(Project project,
                                                                               UserdevDependency userdev,
                                                                               String role) {
        var configuration = Objects.config(project, "_cleanroomUserdev" + capitalized(role),
                "Internal " + role + " userdev resources.");
        configuration.configure(value -> value.attributes(attributes -> {
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED);
            attributes.attribute(UserdevAttributes.ROLE, role);
        }));
        var dependency = (ExternalModuleDependency) userdev.getModuleDependency().copy();
        dependency.attributes(attributes -> attributes.attribute(UserdevAttributes.ROLE, role));
        project.getDependencies().add(configuration.getName(), dependency);
        return configuration;
    }

    /**
     * The natives the published module lists for this machine's platform. LWJGL 3 loads them off the
     * classpath, and the module publishes one variant per classifier so only this platform's are resolved.
     */
    private static NamedDomainObjectProvider<Configuration> nativesConfiguration(Project project, UserdevDependency userdev) {
        var platform = Platform.CURRENT.canonicalNativePlatform();
        var objects = project.getObjects();
        var configuration = Objects.config(project, "_cleanroomUserdevNatives",
                "Internal native libraries for " + platform.lwjglNativesClassifier() + ".");
        configuration.configure(value -> value.attributes(attributes -> {
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, UserdevAttributes.NATIVES);
            attributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                    objects.named(OperatingSystemFamily.class, platform.operatingSystemFamily()));
            attributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE,
                    objects.named(MachineArchitecture.class, platform.machineArchitecture()));
        }));
        project.getDependencies().add(configuration.getName(),
                rawDependency(userdev, UserdevAttributes.NATIVES));
        return configuration;
    }

    /**
     * The libraries the userdev module declares, which is the type hierarchy
     * the renamer resolves a published mod against. The module's own MCP-named jar is filtered out.
     * The SRG-named Minecraft hierarchy travels inside the artifact instead, extracted by {@code useUserdev}.
     */
    private static FileCollection hierarchyConfiguration(Project project, UserdevDependency userdev) {
        var objects = project.getObjects();
        var configuration = Objects.config(project, "_cleanroomUserdevHierarchy",
                "Internal libraries the deobf renamer resolves types against.");
        configuration.configure(value -> value.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES);
        }));
        project.getDependencies().add(configuration.getName(),
                rawDependency(userdev, UserdevAttributes.CLASSES));
        var declared = userdev.getModuleDependency();
        return objects.fileCollection().from(configuration.map(value -> value.getIncoming()
                .artifactView(view -> view.componentFilter(id -> !(id instanceof ModuleComponentIdentifier module)
                        || !(declared.getName().equals(module.getModule())
                                && declared.getGroup().equals(module.getGroup()))))
                .getFiles()));
    }

    private static ExternalModuleDependency rawDependency(UserdevDependency userdev, String role) {
        var dependency = (ExternalModuleDependency) userdev.getModuleDependency().copy();
        dependency.attributes(attributes -> {
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, role);
        });
        return dependency;
    }

    private static String capitalized(String role) {
        var name = new StringBuilder();
        for (var part : role.split("-")) {
            name.append(StringUtils.capitalize(part));
        }
        return name.toString();
    }

    public static void registerTransforms(Project project, UserdevDependency userdev, CachesExtension caches,
                                          FileCollection libraries) {
        var dependencies = project.getDependencies();
        dependencies.getArtifactTypes().named(ArtifactTypeDefinition.JAR_TYPE, type ->
                type.getAttributes().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW));
        var deobf = project.getExtensions().getByType(DeobfExtension.class);
        var accessTransformer = ToolConfigs.get(project, "accesstransformer");
        var mergeTool = ToolConfigs.get(project, "mergetool");
        var decompiler = ToolConfigs.get(project, "decompiler");
        dependencies.registerTransform(MaterializeUserdevClasses.class, transform -> {
            transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW)
                    .attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED)
                    .attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getParameters().getAccessTransformers().from(userdev.getAccessTransformers());
            transform.getParameters().getRenamerClasspath().from(deobf.getRenamerClasspath());
            transform.getParameters().getAccessTransformerClasspath().from(accessTransformer);
            transform.getParameters().getMergeToolClasspath().from(mergeTool);
            transform.getParameters().getSharedCacheDirectory().set(caches.getDirectory());
            transform.getParameters().getOffline().set(project.getGradle().getStartParameter().isOffline());
        });
        dependencies.registerTransform(MaterializeUserdevSources.class, transform -> {
            transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW)
                    .attribute(UserdevAttributes.ROLE, UserdevAttributes.SOURCES)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED)
                    .attribute(UserdevAttributes.ROLE, UserdevAttributes.SOURCES)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getParameters().getDecompilerClasspath().from(decompiler);
            transform.getParameters().getLibraries().from(libraries);
        });
        for (var side : List.of("client", "server")) {
            var role = side + "-extra";
            dependencies.registerTransform(ExtractUserdevExtra.class, transform -> {
                transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW)
                        .attribute(UserdevAttributes.ROLE, role)
                        .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED)
                        .attribute(UserdevAttributes.ROLE, role)
                        .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                transform.getParameters().getSide().set(side);
            });
        }
    }

}
