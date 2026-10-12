/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.DeobfExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.userdev.ExtractUserdevFile;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevClasses;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevSources;
import com.cleanroommc.gradle.api.userdev.PrepareUserdev;
import com.cleanroommc.gradle.api.userdev.SelectPrepared;
import com.cleanroommc.gradle.api.userdev.UserdevAttributes;
import com.cleanroommc.gradle.api.userdev.UserdevDependency;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.Platform;
import com.cleanroommc.gradle.api.util.Property;
import com.cleanroommc.gradle.api.util.dist.Repository;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.lang3.StringUtils;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

public final class UserDevTasks {

    private static final String RUNS_GROUP = "cleanroom runs";

    public final NamedDomainObjectProvider<Configuration> clientExtra;
    public final NamedDomainObjectProvider<Configuration> serverExtra;
    public final NamedDomainObjectProvider<Configuration> natives;
    public final TaskProvider<ExtractUserdevFile> extractMcpToSrg;
    public final TaskProvider<ExtractUserdevFile> extractSrgToMcp;
    public final TaskProvider<RenameJar> reobfJar;
    public final TaskProvider<RunMinecraft> runClient;
    public final TaskProvider<RunMinecraft> runServer;

    public UserDevTasks(Project project, CachesExtension caches, MinecraftExtension minecraft,
        UserdevDependency userdev, VanillaTasks vanilla
    ) {
        var hierarchy = hierarchyConfiguration(project, userdev);
        registerTransforms(project, userdev, caches, hierarchy);

        this.clientExtra = sideConfiguration(project, userdev, UserdevAttributes.CLIENT_EXTRA);
        this.serverExtra = sideConfiguration(project, userdev, UserdevAttributes.SERVER_EXTRA);
        this.natives = nativesConfiguration(project, userdev);

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var main = java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);

        project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, project.files(this.clientExtra));

        var jar = project.getTasks().named("jar", Jar.class);
        jar.configure(task -> task.getArchiveClassifier().convention("mcp"));
        for (var name : new String[] { "apiElements", "runtimeElements" }) {
            project.getConfigurations().named(name).configure(configuration -> {
                // Maven's main artifact is MCP even though the local archive has an MCP classifier.
                configuration.getOutgoing().getArtifacts().clear();
                configuration.getOutgoing().artifact(jar, artifact -> artifact.setClassifier(null));
            });
        }
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
        deobf.getSrgLibraries()
            .from(userdev.getRawConfiguration()
                .getIncoming()
                .artifactView(view -> view.getAttributes()
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.SRG_TYPE))
                .getFiles());

        this.reobfJar = Tasks.register(project, "reobfJar", RenameJar.class, renamer);
        this.reobfJar.configure(task -> {
            task.setGroup("build");
            task.setDescription("Renames this project's jar from MCP to SRG names.");
            task.from(jar);
            task.getArchiveClassifier().set("");
            task.getMap().setFrom(this.extractMcpToSrg.flatMap(ExtractUserdevFile::getOutput));
            task.getLibraries().setFrom(main.map(SourceSet::getCompileClasspath));
        });
        project.getTasks().named("assemble").configure(task -> task.dependsOn(this.reobfJar));

        this.runClient = RunRegistry.register(project, "runClient", RunMinecraft.class);
        this.runServer = RunRegistry.register(project, "runServer", RunMinecraft.class);

        var offline = project.getGradle().getStartParameter().isOffline();
        var runDirectory = project.getLayout().getProjectDirectory().dir("run");
        var natives = vanilla.extractNatives.map(Copy::getDestinationDir);
        var runtimeClasspath = project.getObjects().fileCollection().from(main.map(SourceSet::getRuntimeClasspath));
        var client = config.map(value -> value.runs().client());
        var server = config.map(value -> value.runs().server());
        var srgToMcp = this.extractSrgToMcp.flatMap(ExtractUserdevFile::getOutput);

        RunRegistry.configure(project, this.runClient, task -> {
            task.setGroup(RUNS_GROUP);
            task.dependsOn(main.map(SourceSet::getClassesTaskName), vanilla.downloadAssets);
            configureRun(task, Side.CLIENT, userdev, caches, minecraft, offline, runDirectory.dir("client"), natives);
            task.classpath(runtimeClasspath, this.clientExtra, this.natives);
            MinecraftRuns.fml(task, true, client.map(UserdevConfig.Run::launchClass), client.map(UserdevConfig.Run::tweakClass), srgToMcp);
        });
        RunRegistry.configure(project, this.runServer, task -> {
            task.setGroup(RUNS_GROUP);
            task.dependsOn(main.map(SourceSet::getClassesTaskName));
            configureRun(task, Side.SERVER, userdev, caches, minecraft, offline, runDirectory.dir("server"), natives);
            task.classpath(runtimeClasspath, this.serverExtra, this.natives);
            MinecraftRuns.fml(task, false, server.map(UserdevConfig.Run::launchClass), server.map(UserdevConfig.Run::tweakClass), srgToMcp);
        });

        // The transforms cannot reach an IDE, which takes sources by classifier from a repository
        var wantsSources = project.getProviders()
            .systemProperty("idea.sync.active")
            .map(Boolean::parseBoolean)
            .orElse(Property.USERDEV_SOURCES.bool(project.getProviders()));
        project.afterEvaluate(_ -> {
            var raw = userdev.getRawArtifact().get().getAsFile();
            var name = raw.getName().substring(0, raw.getName().length() - ".jar".length());
            var mirror = Repository.userdevSourcesMirror(project)
                .dir("com/cleanroommc/cleanroom-userdev/" + name.substring("cleanroom-userdev-".length()))
                .getAsFile()
                .toPath();
            var marker = mirror.resolve("raw.sha1");
            var sha1 = IO.sha1(raw);
            try {
                var current = Files.isRegularFile(marker) && Files.readString(marker).equals(sha1);
                if (!current) {
                    // A republished artifact must not be shadowed by the copy made from the old one
                    project.delete(mirror);
                }
                if (current || !wantsSources.getOrElse(false)) {
                    return;
                }
                var sources = (ExternalModuleDependency) userdev.getModuleDependency().copy();
                sources.attributes(attributes -> attributes.attribute(UserdevAttributes.ROLE,
                    UserdevAttributes.SOURCES
                ));
                var configuration = project.getConfigurations().create(Repository.USERDEV_SOURCES_CONFIGURATION);
                configuration.setCanBeConsumed(false);
                configuration.getDependencies().add(sources);
                configuration.attributes(attributes -> {
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        project.getObjects().named(Category.class, Category.DOCUMENTATION)
                    );
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                        project.getObjects().named(DocsType.class, DocsType.SOURCES)
                    );
                });
                var metadata = new File(raw.getParentFile(), name + ".module");
                // Gradle's module cache keeps each file of a version under its own hash directory
                var hashes = raw.getParentFile().getParentFile().listFiles(File::isDirectory);
                for (var i = 0; !metadata.isFile() && i < hashes.length; i++) {
                    metadata = new File(hashes[i], name + ".module");
                }
                if (!metadata.isFile()) {
                    project.getLogger().warn("Userdev sources were not attached: {}.module was not found.", name);
                    return;
                }
                // IntelliJ drops sources that come out of a transform, so the sources variant points at the finished jar
                var module = JsonParser.parseString(Files.readString(metadata.toPath())).getAsJsonObject();
                for (var variant : module.getAsJsonArray("variants")) {
                    var attributes = variant.getAsJsonObject().getAsJsonObject("attributes");
                    var role = attributes.get(UserdevAttributes.ROLE.getName());
                    if (role != null && role.getAsString().equals(UserdevAttributes.SOURCES)) {
                        attributes.addProperty(UserdevAttributes.STAGE.getName(), UserdevAttributes.MATERIALIZED);
                        var file = new JsonObject();
                        file.addProperty("name", name + "-sources.jar");
                        file.addProperty("url", name + "-sources.jar");
                        var files = new JsonArray();
                        files.add(file);
                        variant.getAsJsonObject().add("files", files);
                    }
                }
                Files.createDirectories(mirror);
                Files.writeString(mirror.resolve(name + ".module"), module.toString());
                Files.copy(raw.toPath(), mirror.resolve(raw.getName()));
                // Resolved past the mirror, whose metadata no longer leads to the transform. The mirror already
                // has to exist here: this resolves other configurations, and Gradle remembers a miss for the build.
                var materialized = configuration.filter(file -> file.getName().equals(MaterializeUserdevSources.OUTPUT))
                    .getSingleFile();
                Files.copy(materialized.toPath(), mirror.resolve(name + "-sources.jar"));
                Files.writeString(marker, sha1);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to mirror userdev sources into " + mirror, e);
            }
        });

        project.getPluginManager()
            .withPlugin("idea",
                _ -> project.getExtensions().getByType(IdeaModel.class).getModule().setDownloadSources(true)
            );
    }

    private static void configureRun(RunMinecraft task, Side side, UserdevDependency userdev, CachesExtension caches,
        MinecraftExtension minecraft, boolean offline, Directory runDirectory, Provider<File> natives
    ) {
        MinecraftRuns.caches(task, caches, minecraft.getVersionMeta(), offline);
        task.getSide().set(side);
        task.getEnv().set(Environment.CLEANROOM);
        task.getMinecraftVersion().set(userdev.getMinecraftVersion());
        task.setWorkingDir(runDirectory);
        task.getNatives().fileProvider(natives);
    }

    private static NamedDomainObjectProvider<Configuration> sideConfiguration(Project project,
        UserdevDependency userdev, String role
    ) {
        var configuration = Objects.config(project, "_cleanroomUserdev" + capitalized(role),
            "Internal " + role + " userdev resources."
        );
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
    private static NamedDomainObjectProvider<Configuration> nativesConfiguration(Project project,
        UserdevDependency userdev
    ) {
        var platform = Platform.CURRENT.canonicalNativePlatform();
        var objects = project.getObjects();
        var configuration = Objects.config(project, "_cleanroomUserdevNatives",
            "Internal native libraries for " + platform.lwjglNativesClassifier() + "."
        );
        configuration.configure(value -> value.attributes(attributes -> {
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, UserdevAttributes.NATIVES);
            attributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                objects.named(OperatingSystemFamily.class, platform.operatingSystemFamily())
            );
            attributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE,
                objects.named(MachineArchitecture.class, platform.machineArchitecture())
            );
        }));
        project.getDependencies().add(configuration.getName(), rawDependency(userdev, UserdevAttributes.NATIVES));
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
            "Internal libraries the deobf renamer resolves types against."
        );
        configuration.configure(value -> value.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements.class, LibraryElements.JAR)
            );
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES);
        }));
        project.getDependencies().add(configuration.getName(), rawDependency(userdev, UserdevAttributes.CLASSES));
        var declared = userdev.getModuleDependency();
        return objects.fileCollection()
            .from(configuration.map(value -> value.getIncoming()
                .artifactView(view -> view.componentFilter(id -> !(id instanceof ModuleComponentIdentifier module) ||
                    !(declared.getName().equals(module.getModule()) && declared.getGroup().equals(module.getGroup()))))
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
        FileCollection libraries
    ) {
        var dependencies = project.getDependencies();
        dependencies.getArtifactTypes()
            .named(ArtifactTypeDefinition.JAR_TYPE,
                type -> type.getAttributes().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW)
            );
        var deobf = project.getExtensions().getByType(DeobfExtension.class);
        var accessTransformer = ToolConfigs.get(project, "accesstransformer");
        var mergeTool = ToolConfigs.get(project, "mergetool");
        var decompiler = ToolConfigs.get(project, "decompiler");
        // Every role starts from the same prepared directory, so Minecraft is rebuilt once per userdev artifact
        dependencies.registerTransform(PrepareUserdev.class, transform -> {
            transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.PREPARED_TYPE);
            transform.getParameters().getUniversal().from(userdev.getUniversal());
            transform.getParameters().getLibraries().from(libraries);
            transform.getParameters().getRenamerClasspath().from(deobf.getRenamerClasspath());
            transform.getParameters().getAccessTransformerClasspath().from(accessTransformer);
            transform.getParameters().getMergeToolClasspath().from(mergeTool);
            transform.getParameters().getSharedCacheDirectory().set(caches.getDirectory());
            transform.getParameters().getOffline().set(project.getGradle().getStartParameter().isOffline());
        });
        dependencies.registerTransform(MaterializeUserdevClasses.class, transform -> {
            transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW).attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.PREPARED_TYPE);
            transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED).attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getParameters().getAccessTransformers().from(userdev.getAccessTransformers());
            transform.getParameters().getLibraries().from(libraries);
            transform.getParameters().getRenamerClasspath().from(deobf.getRenamerClasspath());
            transform.getParameters().getAccessTransformerClasspath().from(accessTransformer);
        });
        dependencies.registerTransform(MaterializeUserdevSources.class, transform -> {
            transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW).attribute(UserdevAttributes.ROLE, UserdevAttributes.SOURCES).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.PREPARED_TYPE);
            transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED).attribute(UserdevAttributes.ROLE, UserdevAttributes.SOURCES).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            transform.getParameters().getDecompilerClasspath().from(decompiler);
            transform.getParameters().getLibraries().from(libraries);
        });
        for (var side : List.of("client", "server")) {
            var role = side + "-extra";
            dependencies.registerTransform(SelectPrepared.class, transform -> {
                transform.getFrom().attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW).attribute(UserdevAttributes.ROLE, role).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.PREPARED_TYPE);
                transform.getTo().attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED).attribute(UserdevAttributes.ROLE, role).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
                transform.getParameters().getFile().set(role + ".jar");
            });
        }
        dependencies.registerTransform(SelectPrepared.class, transform -> {
            transform.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.PREPARED_TYPE);
            transform.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, UserdevAttributes.SRG_TYPE);
            transform.getParameters().getFile().set(PrepareUserdev.MINECRAFT_SRG);
        });
    }

}
