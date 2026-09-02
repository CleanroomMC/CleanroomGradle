package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.LoaderExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.ext.PatchDevEnvironment;
import com.cleanroommc.gradle.api.ext.PatchesExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip;
import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile;
import com.cleanroommc.gradle.api.task.dist.WriteUserdevConfig;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.Platform;
import com.cleanroommc.gradle.api.util.dist.Coordinate;
import com.cleanroommc.gradle.api.util.dist.LibraryJson;
import com.cleanroommc.gradle.api.util.dist.ResolvedLibraries;
import com.cleanroommc.gradle.api.util.lazy.ProjectCoordinates;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches;
import com.cleanroommc.gradle.api.task.sas.CheckSAS;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import com.cleanroommc.gradle.api.task.userdev.MaterializeUserdevSourcesJar;
import de.undercouch.gradle.tasks.download.Download;
import org.apache.commons.lang3.StringUtils;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentFactory;
import com.cleanroommc.gradle.api.userdev.UserdevAttributes;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registers the Cleanroom release/distribution pipeline.
 * Only instantiated for {@code cleanroom { mode = 'loader' }}.
 */
public final class DistributionTasks {

    private static final String GROUP_NAME = "cleanroom distribution";
    private static final String ARTIFACT_ID = "cleanroom";

    public final TaskProvider<WriteMappings> writeMcp2Srg, writeObf2SrgTsrg, writeMcp2Notch;
    public final TaskProvider<RenameJar> reobfJar, reobfMinecraftJar;
    public final TaskProvider<Jar> deobfLibraryJar, universalJar, userdevJar, sourcesJar, javadocJar;
    public final TaskProvider<StripSideOnlyJar> stripClientMinecraftJar, stripServerMinecraftJar;
    public final TaskProvider<GenerateBinPatches> genBinPatches;
    public final TaskProvider<WriteUserdevConfig> writeUserdevConfig;
    public final TaskProvider<MaterializeUserdevSourcesJar> userdevSourcesJar;
    public final TaskProvider<PublishMmcPackZip> publishMmcPackZip;
    public final TaskProvider<WriteInstallProfile> writeInstallProfile;
    public final TaskProvider<Jar> installerJar;

    private final Provider<List<String>> runtimeModules;
    private final SoftwareComponent userdevComponent;

    public DistributionTasks(Project project, ProjectCoordinates coordinates, CachesExtension caches,
                             MinecraftExtension minecraft, LoaderExtension loader, PatchesExtension patches,
                             VanillaTasks vanilla, McpMappings mappings, MCPTasks mcp,
                             IntermediateProcessor intermediates, SoftwareComponentFactory components) {
        var layout = project.getLayout();
        var providers = project.getProviders();
        var group = coordinates.getGroup();
        var version = coordinates.getVersion();
        var minecraftVersion = vanilla.minecraftVersion;
        var universal = group.zip(version, (id, number) -> new Coordinate(id, ARTIFACT_ID, number, "universal", "jar"));
        var titleProperty = "Cleanroom";
        var vendorProperty = "CleanroomMC";
        var timestampProperty = providers.environmentVariable("SOURCE_DATE_EPOCH")
                .map(Long::parseLong)
                .map(epoch ->
                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")))
                .orElse("1970-01-01T00:00:00+0000");

        var javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        var targetJavaMajor = Providers.targetJavaMajor(project);
        var mainSourceSet = javaExtension.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);
        var jarTask = project.getTasks().named("jar", Jar.class);

        var archives = Tasks.archives(project);
        var resolvedRepositoryUrls = coordinates.getRepositoryUrls();
        // Registered by CleanroomTasks: the source patch set a mod workspace rebuilds Minecraft's sources from
        var minecraftPatches = patches.getPatchDev().named("minecraft").flatMap(PatchDevEnvironment::getPatches);

        var installerBase = Objects.config(project, "installerBase",
                "The Cleanroom Installer runtime that installerJar re-packages.");
        installerBase.configure(config -> config.setTransitive(false));
        project.getDependencies().addProvider(installerBase.getName(),
                loader.getInstallerVersion().map(installerVersion -> "com.cleanroommc:installer:" + installerVersion));

        var runtimeClasspath = project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        var distributionLibraries = Objects.config(project, "distributionLibraries",
                "Resolved classpath and LWJGL native libraries shared by all Cleanroom distributions.");
        distributionLibraries.configure(config -> {
            config.extendsFrom(runtimeClasspath.get(), project.getConfigurations().getByName(LwjglNatives.ALL_CONFIGURATION_NAME));
            config.exclude(Map.of("group", VanillaTasks.LWJGL2_GROUP));
            config.withDependencies(dependencies -> VanillaTasks.addDistributionLibraries(
                    project.getDependencyFactory(), dependencies, minecraft.getVersionMeta().get()));
            config.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            });
        });
        var distributionLibraryArtifacts = ResolvedLibraries.artifacts(project.getObjects(),
                distributionLibraries.flatMap(config -> config.getIncoming().getArtifacts().getResolvedArtifacts()),
                distributionLibraries.flatMap(config -> config.getIncoming().getResolutionResult().getRootComponent()),
                resolvedRepositoryUrls);

        var selectedDistributionVersions = providers.provider(() -> VanillaTasks.selectedVersions(distributionLibraries.get()));
        var distributionNatives = Objects.config(project, "distributionNatives",
                "Vanilla libraries extracted as natives by Cleanroom distributions, for every platform.");
        distributionNatives.configure(config -> {
            config.setTransitive(false);
            config.withDependencies(dependencies -> VanillaTasks.addDistributionNatives(
                    project.getDependencyFactory(), dependencies, minecraft.getVersionMeta().get(),
                    selectedDistributionVersions.get()));
        });
        var manifestUrls = minecraft.getVersionMeta().map(meta -> {
            var urls = new HashMap<String, String>();
            for (var library : meta.libraries()) {
                var downloads = library.downloads();
                if (downloads == null) {
                    continue;
                }
                if (downloads.artifact() != null && downloads.artifact().url() != null) {
                    urls.put(library.name(), downloads.artifact().url());
                }
                if (downloads.classifiers() != null) {
                    downloads.classifiers().forEach((classifier, download) -> {
                        if (download.url() != null) {
                            urls.put(library.name() + ":" + classifier, download.url());
                        }
                    });
                }
            }
            return urls;
        });
        var distributionNativeArtifacts = ResolvedLibraries.artifacts(project.getObjects(),
                distributionNatives.flatMap(config -> config.getIncoming().getArtifacts().getResolvedArtifacts()),
                distributionNatives.flatMap(config -> config.getIncoming().getResolutionResult().getRootComponent()),
                resolvedRepositoryUrls);
        var universalUrl = universalRepositoryUrl(project)
                .zip(universal, (url, coordinate) -> LibraryJson.trailingSlash(url) + coordinate.mavenPath());

        this.writeMcp2Srg = mappings.write(project, caches, "writeMcp2Srg", WriteMappings.Direction.MCP_TO_SRG, UserdevConfig.MCP2SRG);
        this.writeObf2SrgTsrg = mappings.write(project, caches, "writeObf2SrgTsrg", WriteMappings.Direction.OBF_TO_SRG, "obf2srg.tsrg");
        this.writeMcp2Notch = mappings.write(project, caches, "writeMcp2Notch", WriteMappings.Direction.MCP_TO_NOTCH, "mcp2notch.tsrg");
        this.reobfJar = Tasks.register(project, "reobfJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.deobfLibraryJar = Tasks.register(project, "deobfLibraryJar", Jar.class);
        this.reobfMinecraftJar = Tasks.register(project, "reobfMinecraftJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.stripClientMinecraftJar = Tasks.register(project, "stripClientMinecraftJar", StripSideOnlyJar.class);
        this.stripServerMinecraftJar = Tasks.register(project, "stripServerMinecraftJar", StripSideOnlyJar.class);
        this.genBinPatches = Tasks.register(project, "genBinPatches", GenerateBinPatches.class);
        this.universalJar = Tasks.register(project, "universalJar", Jar.class);
        this.userdevJar = Tasks.register(project, "userdevJar", Jar.class);
        this.userdevSourcesJar = Tasks.register(project, "userdevSourcesJar", MaterializeUserdevSourcesJar.class);
        this.sourcesJar = Tasks.register(project, "sourcesJar", Jar.class);
        this.javadocJar = Tasks.register(project, "javadocJar", Jar.class);
        this.writeUserdevConfig = Tasks.register(project, "writeUserdevConfig", WriteUserdevConfig.class);
        this.publishMmcPackZip = Tasks.register(project, "publishMmcPackZip", PublishMmcPackZip.class);
        this.writeInstallProfile = Tasks.register(project, "writeInstallProfile", WriteInstallProfile.class);
        this.installerJar = Tasks.register(project, "installerJar", Jar.class);
        var clientTweakClass = loader.getClientTweakClass();
        var serverTweakClass = loader.getServerTweakClass();
        var launchClass = loader.getLaunchClass();
        Tasks.group("build", this.reobfJar);
        Tasks.group(GROUP_NAME, this.universalJar, this.userdevJar, this.userdevSourcesJar, this.sourcesJar, this.javadocJar,
                this.publishMmcPackZip, this.installerJar);
        project.getTasks().named("assemble").configure(task -> task.dependsOn(this.universalJar, this.userdevJar,
                this.userdevSourcesJar,
                this.sourcesJar, this.javadocJar, this.publishMmcPackZip, this.installerJar));

        this.reobfJar.configure(task -> {
            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Srg.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(caches.getLocalDirectory().file("dist/reobf/" + ARTIFACT_ID + "-srg.jar"));
        });
        this.deobfLibraryJar.configure(task -> {
            task.setDescription("Packages the SRG Minecraft hierarchy used to deobfuscate mod dependencies.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(archives.zipTree(this.reobfJar.flatMap(RenameJar::getOutput)),
                    spec -> spec.include(Meta.MINECRAFT_PACKAGE_PATH + "**"));
            task.getDestinationDirectory().set(caches.getLocalDirectory().dir("dist/reobf"));
            task.getArchiveFileName().set(UserdevConfig.DEOBF_LIBRARY);
        });
        this.reobfMinecraftJar.configure(task -> {
            // genBinPatches only looks at net/minecraft, so the loader classes riding along are harmless
            task.setDescription("Renames the main jar back to obfuscated names, for binpatch generation.");
            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Notch.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(caches.getLocalDirectory().file("dist/reobf/minecraft-notch.jar"));
        });
        this.stripClientMinecraftJar.configure(task -> {
            task.getInputJar().set(this.reobfMinecraftJar.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.CLIENT);
            task.getOutputJar().set(caches.getLocalDirectory().file("dist/reobf/minecraft-client.jar"));
        });
        this.stripServerMinecraftJar.configure(task -> {
            task.getInputJar().set(this.reobfMinecraftJar.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.SERVER);
            task.getOutputJar().set(caches.getLocalDirectory().file("dist/reobf/minecraft-server.jar"));
        });
        this.genBinPatches.configure(task -> {
            task.setDescription("Generates the client and server binpatches Cleanroom ships to rebuild Minecraft.");

            task.getClientOriginalJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getClientModifiedJar().set(this.stripClientMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getClientPrefix().set(UserdevConfig.CLIENT_BINPATCHES);
            task.getServerOriginalJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getServerModifiedJar().set(this.stripServerMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getServerPrefix().set(UserdevConfig.SERVER_BINPATCHES);
            task.getIncludedPrefixes().add(Meta.MINECRAFT_PACKAGE_PATH);
            task.getObfuscationMappings().set(this.writeObf2SrgTsrg.flatMap(WriteMappings::getOutput));
            task.getBinpatches().set(caches.getLocalDirectory().file("binpatches/" + UserdevConfig.BINPATCHES));
        });
        this.universalJar.configure(task -> {
            task.setDescription("Assembles the Cleanroom universal jar (reobfuscated classes, binpatches and deobf data).");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("universal");
            task.getInputs().property("manifestTimestamp", timestampProperty);

            task.from(archives.zipTree(this.reobfJar.flatMap(RenameJar::getOutput)), spec -> spec.exclude(Meta.MINECRAFT_PACKAGE_PATH + "**"));
            task.from(layout.getProjectDirectory(), spec -> spec.include(
                    "CREDITS.txt",
                    "LICENSE.txt",
                    "LICENSE",
                    "CHANGELOG.md",
                    "LICENSE-Paulscode IBXM Library.txt",
                    "LICENSE-Paulscode SoundSystem CodecIBXM.txt"
            ));
            task.from(this.writeObf2SrgTsrg.flatMap(WriteMappings::getOutput),
                    spec -> spec.rename(name -> "deobf_data-" + minecraftVersion.get() + ".tsrg"));
            task.from(this.genBinPatches.flatMap(GenerateBinPatches::getBinpatches));

            var forgeVersion = loader.getForgeVersion();
            task.doFirst("configureManifest", t -> {
                var jar = (Jar) t;

                // TODO needed? or leave to buildscript
                Map<String, Object> main = new LinkedHashMap<>();
                main.put("Timestamp", timestampProperty.get());
                // TODO
                main.put("Main-Class", "net.minecraftforge.fml.relauncher.ServerLaunchWrapper");
                main.put("Tweak-Class", clientTweakClass.get());
                jar.getManifest().attributes(main);

                Map<String, Object> forgeSection = new LinkedHashMap<>();
                forgeSection.put("Specification-Title", titleProperty);
                forgeSection.put("Specification-Vendor", vendorProperty);
                forgeSection.put("Specification-Version", specVersion(version.get()));
                forgeSection.put("Implementation-Title", group.get());
                forgeSection.put("Implementation-Version", forgeVersion.get());
                forgeSection.put("Implementation-Vendor", vendorProperty);
                jar.getManifest().attributes(forgeSection, "net/minecraftforge/common/"); // Don't ask... FIXME
            });
        });

        var javadocTask = project.getTasks().named("javadoc", Javadoc.class);
        javadocTask.configure(task -> {
            task.setFailOnError(false);
            task.options(MinimalJavadocOptions::quiet);
        });

        this.writeUserdevConfig.configure(task -> {
            task.setDescription("Writes the metadata a mod developer's environment rebuilds itself from.");

            task.getCleanroomVersion().set(version);
            task.getForgeVersion().set(loader.getForgeVersion());
            task.getMcpConfig().set(mappings.mcpConfig.map(Objects::notation));
            task.getMcpMappings().set(mappings.mcpMappings.map(Objects::fullNotation));
            task.getInitialPatches().set(mcp.initialPatches.map(Objects::fullNotation));
            task.getTools().set(ToolConfigs.sourceToolNotations(project));
            task.getBinpatches().set(UserdevConfig.meta(UserdevConfig.BINPATCHES));
            task.getClientBinpatches().set(UserdevConfig.CLIENT_BINPATCHES);
            task.getServerBinpatches().set(UserdevConfig.SERVER_BINPATCHES);
            task.getSrg2Mcp().set(UserdevConfig.meta(UserdevConfig.SRG2MCP));
            task.getMcp2Srg().set(UserdevConfig.meta(UserdevConfig.MCP2SRG));
            task.getAccessTransformers().set(loader.getAccessTransformers().getElements().map(files ->
                    files.stream().map(file -> UserdevConfig.meta(UserdevConfig.ATS) + "/" + file.getAsFile().getName()).toList()));
            task.getSideAnnotationStrippers().set(UserdevConfig.meta(UserdevConfig.SAS));
            task.getPatches().set(UserdevConfig.meta(UserdevConfig.PATCHES));
            task.getClientUrl().set(minecraft.getVersionMeta().map(VersionMeta::clientUrl));
            task.getClientSha1().set(minecraft.getVersionMeta().map(VersionMeta::clientSha1));
            task.getServerUrl().set(minecraft.getVersionMeta().map(VersionMeta::serverUrl));
            task.getServerSha1().set(minecraft.getVersionMeta().map(VersionMeta::serverSha1));
            task.getLoaderGroup().set(group);
            task.getClientMainClass().set(loader.getClientMainClass());
            task.getServerMainClass().set(loader.getServerMainClass());
            task.getLaunchClass().set(launchClass);
            task.getClientTweakClass().set(clientTweakClass);
            task.getServerTweakClass().set(serverTweakClass);
            task.getClientTarget().set(loader.getClientTarget());
            task.getServerTarget().set(loader.getServerTarget());
            task.getOutput().set(caches.getLocalDirectory().file("dist/" + UserdevConfig.FILE_NAME));
        });
        this.userdevJar.configure(task -> {
            task.setDescription("Assembles the userdev jar for mod developers.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set("cleanroom-userdev");
            task.getArchiveVersion().set(version);

            // SRG-named
            task.from(archives.zipTree(this.reobfJar.flatMap(RenameJar::getOutput)), spec -> spec.exclude(Meta.MINECRAFT_PACKAGE_PATH + "**"));
            task.from(this.genBinPatches.flatMap(GenerateBinPatches::getBinpatches),
                    spec -> spec.into(UserdevConfig.META));
            task.from(loader.getAccessTransformers(),
                    spec -> spec.into(UserdevConfig.meta(UserdevConfig.ATS)));
            task.from(mcp.checkSAS.flatMap(CheckSAS::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.SAS));
            task.from(minecraftPatches, spec -> spec.into(UserdevConfig.meta(UserdevConfig.PATCHES)));
            task.from(mappings.writeSrg2Mcp.flatMap(WriteMappings::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.SRG2MCP));
            task.from(this.writeObf2SrgTsrg.flatMap(WriteMappings::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.OBF2SRG));
            task.from(mappings.access,
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.ACCESS));
            task.from(mappings.constructors,
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.CONSTRUCTORS));
            task.from(mappings.exceptions,
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.EXCEPTIONS));
            task.from(mappings.methodMappings,
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.METHODS));
            task.from(mappings.fieldMappings,
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.FIELDS));
            task.from(mappings.parameterMappings,
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.PARAMS));
            task.from(mcp.extractInitialPatches.map(Copy::getDestinationDir),
                    spec -> spec.into(UserdevConfig.meta(UserdevConfig.INITIAL_PATCHES)));
            task.from(mainSourceSet.map(SourceSet::getAllJava), spec -> {
                spec.exclude(Meta.MINECRAFT_PACKAGE_PATH + "**");
                spec.into(UserdevConfig.meta(UserdevConfig.LOADER_SOURCES));
            });
            task.from(archives.zipTree(mcp.splitClientJar.flatMap(com.cleanroommc.gradle.api.task.mcp.SplitJar::getExtraJar)),
                    spec -> spec.into(UserdevConfig.meta("client-extra")));
            task.from(archives.zipTree(mcp.splitServerJar.flatMap(com.cleanroommc.gradle.api.task.mcp.SplitJar::getExtraJar)),
                    spec -> spec.into(UserdevConfig.meta("server-extra")));
            task.from(this.deobfLibraryJar.flatMap(Jar::getArchiveFile),
                    spec -> spec.into(UserdevConfig.META));
            task.from(mcp.decompileSrg.flatMap(com.cleanroommc.gradle.api.task.common.Decompile::getCompiledJar),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.SOURCE_INPUT));
            task.from(this.writeMcp2Srg.flatMap(WriteMappings::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.MCP2SRG));
            task.from(this.writeUserdevConfig.flatMap(WriteUserdevConfig::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.FILE_NAME));
        });
        this.userdevSourcesJar.configure(task -> {
            task.setDescription("Builds the conventional sources artifact IDEs request for cleanroom-userdev.");
            task.getUserdevArtifact().set(this.userdevJar.flatMap(Jar::getArchiveFile));
            task.getLibraries().from(vanilla.vanillaConfig);
            task.getDecompilerClasspath().from(ToolConfigs.get(project, "decompiler"));
            task.getOutput().set(layout.getBuildDirectory().file(version.map(number ->
                    "libs/cleanroom-userdev-" + number + "-sources.jar")));
        });
        this.publishMmcPackZip.configure(task -> {
            task.setDescription("Publishes a minimal MultiMC/PrismLauncher import ZIP.");

            task.getInstanceName().set(titleProperty);
            task.getCleanroomVersion().set(version);
            task.getMainClass().set(launchClass);
            task.getTweakers().set(clientTweakClass.map(List::of));
            task.getCompatibleJavaMajors().set(targetJavaMajor.map(List::of));
            task.getUniversalCoordinate().set(universal.map(Coordinate::serialized));
            task.getUniversalUrl().set(universalUrl);
            task.getUniversalJar().set(this.universalJar.flatMap(Jar::getArchiveFile));
            task.getLibraries().set(distributionLibraryArtifacts);
            task.getInheritedLibraries().set(minecraft.getVersionMeta()
                    .map(meta -> meta.libraries().stream()
                            .map(VersionMeta.Library::name)
                            .collect(Collectors.toSet())
                    )
            );
            task.getMinecraftExcludeRules().set(distributionLibraries.map(ResolvedLibraries::excludeRules));
            task.getArchiveFile().set(layout.getBuildDirectory().file(
                    version.map(number -> "libs/" + ARTIFACT_ID + "-" + number + ".zip")));
            task.getInstallerArchiveFile().set(caches.getLocalDirectory().file("dist/mmc-installer.zip"));
        });
        this.writeInstallProfile.configure(task -> {
            task.setDescription("Writes install_profile.json and version.json for the Cleanroom installer.");

            task.getProfileName().set(titleProperty);
            task.getCleanroomVersion().set(version);
            task.getVersionId().set(version.map(number -> titleProperty + "-" + number));
            task.getMainClass().set(launchClass);
            task.getServerMainClass().set(launchClass);
            task.getTweakers().set(clientTweakClass.map(List::of));
            task.getServerTweakers().set(serverTweakClass.map(List::of));
            task.getJvmArgs().set(loader.getInstallerJvmArgs());
            task.getMinimumJava().set(targetJavaMajor);
            task.getRecommendedJava().set(targetJavaMajor);
            task.getUniversalCoordinate().set(universal.map(Coordinate::serialized));
            task.getUniversalUrl().set(universalUrl);
            task.getUniversalJar().set(this.universalJar.flatMap(Jar::getArchiveFile));
            task.getLibraries().set(distributionLibraryArtifacts);
            task.getNativeLibraries().set(distributionNativeArtifacts);
            task.getLibraryExcludeRules().set(distributionLibraries.map(ResolvedLibraries::excludeRules));
            task.getManifestUrls().set(manifestUrls);
            task.getVersionMeta().set(minecraft.getVersionMeta());
            task.getReleaseTime().set(timestampProperty);
            task.getInstallProfile().set(caches.getLocalDirectory().map(dir -> dir.file("dist/install_profile.json")));
            task.getVersionJson().set(caches.getLocalDirectory().map(dir -> dir.file("dist/version.json")));
            task.getEmbeddedLibraries().set(caches.getLocalDirectory().dir("dist/maven-local"));
        });

        this.installerJar.configure(task -> {
            task.setDescription("Assembles the version-pinned Cleanroom installer jar.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("installer");

            var installerRuntime = installerBase.flatMap(config ->
                    config.getIncoming().getArtifacts().getResolvedArtifacts().map(artifacts -> {
                        if (artifacts.size() != 1) {
                            throw new IllegalStateException("Expected exactly one installer runtime jar on the "
                                    + "installerBase configuration, resolved " + artifacts.size()
                                    + ". Set cleanroom.loader.installerVersion to a published com.cleanroommc:installer release.");
                        }
                        return artifacts.iterator().next().getFile();
                    }));
            task.from(archives.zipTree(installerRuntime), spec -> spec.exclude(
                    "META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"));
            task.from(this.writeInstallProfile.flatMap(WriteInstallProfile::getInstallProfile));
            task.from(this.writeInstallProfile.flatMap(WriteInstallProfile::getVersionJson));
            task.from(this.writeInstallProfile.flatMap(WriteInstallProfile::getEmbeddedLibraries),
                    spec -> spec.into("maven"));
            task.from(this.universalJar.flatMap(Jar::getArchiveFile), spec -> {
                spec.into(group.zip(version, (groupId, number) ->
                        "maven/" + groupId.replace('.', '/') + "/" + ARTIFACT_ID + "/" + number));
                spec.rename(_ -> universal.get().fileName());
            });
            task.from(this.publishMmcPackZip.flatMap(PublishMmcPackZip::getInstallerArchiveFile), spec -> {
                spec.into("mmc");
                spec.rename(_ -> "pack.zip");
            });
            task.doFirst("configureManifest", _ -> task.getManifest().attributes(Map.of(
                    "Main-Class", "com.cleanroommc.installer.Main",
                    "Implementation-Version", version.get(),
                    "Cleanroom-Version", version.get())
            ));
        });

        this.sourcesJar.configure(task -> {
            task.setDescription("Packages the loader's own sources. Minecraft is left out, since a userdev "
                    + "workspace decompiles it under its own MCP names.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("sources");

            task.from(mainSourceSet.map(SourceSet::getAllJava), spec -> spec.exclude(Meta.MINECRAFT_PACKAGE_PATH + "**"));
        });

        this.javadocJar.configure(task -> {
            task.setDescription("Packages Javadoc into a jar.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("javadoc");

            task.from(javadocTask.map(Javadoc::getDestinationDir));
        });

        this.runtimeModules = ResolvedLibraries.modules(runtimeClasspath
                .flatMap(config -> config.getIncoming().getResolutionResult().getRootComponent()));
        this.userdevComponent = registerVariants(project, minecraft, components, targetJavaMajor);

        intermediates.discardAfterAll(
                List.of(this.universalJar, this.userdevJar),
                this.reobfJar.flatMap(RenameJar::getOutput)
        );
        intermediates.after(mcp.discardCheckSAS, this.userdevJar);
        intermediates.discardAfterAll(
                List.of(this.stripClientMinecraftJar, this.stripServerMinecraftJar),
                this.reobfMinecraftJar.flatMap(RenameJar::getOutput)
        );
        intermediates.discardAfter(this.genBinPatches,
                this.stripClientMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar),
                this.stripServerMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
    }

    public void registerPublications(Project project, ProjectCoordinates coordinates) {
        var version = coordinates.getVersion();
        var runtimeModules = this.runtimeModules;
        var publishing = project.getExtensions().getByType(PublishingExtension.class);
        publishing.getPublications().register("cleanroom", MavenPublication.class, publication -> {
            publication.setArtifactId(ARTIFACT_ID);
            publication.setVersion(version.get());
            publication.artifact(this.publishMmcPackZip.flatMap(PublishMmcPackZip::getArchiveFile),
                    artifact -> artifact.setExtension("zip"));
            publication.artifact(this.universalJar);
            publication.artifact(this.sourcesJar);
            publication.artifact(this.javadocJar);
            publication.artifact(this.installerJar);
            // The published jars are reobfuscated, so there is no java component whose dependencies match them
            publication.getPom().withXml(xml -> {
                var dependencies = xml.asNode().appendNode("dependencies");
                for (var module : runtimeModules.get()) {
                    var parts = module.split(":");
                    var dependency = dependencies.appendNode("dependency");
                    dependency.appendNode("groupId", parts[0]);
                    dependency.appendNode("artifactId", parts[1]);
                    dependency.appendNode("version", parts[2]);
                    dependency.appendNode("scope", "runtime");
                }
            });
        });

        publishing.getPublications().register("cleanroomUserdev", MavenPublication.class, publication -> {
            publication.setArtifactId("cleanroom-userdev");
            publication.setVersion(version.get());
            publication.from(this.userdevComponent);
            publication.artifact(this.userdevSourcesJar.flatMap(MaterializeUserdevSourcesJar::getOutput), artifact -> {
                artifact.setClassifier("sources");
                artifact.setExtension("jar");
            });
        });
    }

    private SoftwareComponent registerVariants(Project project, MinecraftExtension minecraft,
                                               SoftwareComponentFactory components,
                                               Provider<Integer> targetJavaMajor) {
        var configurations = project.getConfigurations();
        var javaApiElements = configurations.named(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME);
        var javaRuntimeElements = configurations.named(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME);

        var minecraftLibraries = configurations.register("cleanroomUserdevMinecraftLibraries", configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
            configuration.setDescription("Vanilla libraries the published userdev module depends on.");
            configuration.getDependencies().addAllLater(minecraft.getVersionMeta().map(meta -> {
                var dependencies = new ArrayList<Dependency>();
                VanillaTasks.addDistributionLibraries(project.getDependencyFactory(), dependencies, meta);
                return dependencies;
            }));
        });

        var apiElements = configurations.register("cleanroomUserdevApiElements", configuration -> {
            userdevVariant(project, configuration, Usage.JAVA_API, Category.LIBRARY, UserdevAttributes.CLASSES,
                    targetJavaMajor);
            configuration.extendsFrom(javaApiElements.get(), minecraftLibraries.get());
        });
        var runtimeElements = configurations.register("cleanroomUserdevRuntimeElements", configuration -> {
            userdevVariant(project, configuration, Usage.JAVA_RUNTIME, Category.LIBRARY, UserdevAttributes.CLASSES,
                    targetJavaMajor);
            configuration.extendsFrom(javaRuntimeElements.get(), minecraftLibraries.get());
        });
        var sourcesElements = configurations.register("cleanroomUserdevSourcesElements", configuration -> {
            userdevVariant(project, configuration, Usage.JAVA_RUNTIME, Category.DOCUMENTATION,
                    UserdevAttributes.SOURCES, targetJavaMajor);
            configuration.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                    project.getObjects().named(DocsType.class, DocsType.SOURCES));
            configuration.extendsFrom(minecraftLibraries.get());
        });
        var clientExtraElements = configurations.register("cleanroomUserdevClientExtraElements", configuration ->
                userdevVariant(project, configuration, Usage.JAVA_RUNTIME, Category.LIBRARY,
                        UserdevAttributes.CLIENT_EXTRA, targetJavaMajor));
        var serverExtraElements = configurations.register("cleanroomUserdevServerExtraElements", configuration ->
                userdevVariant(project, configuration, Usage.JAVA_RUNTIME, Category.LIBRARY,
                        UserdevAttributes.SERVER_EXTRA, targetJavaMajor));

        var rawArtifact = project.getArtifacts().add(apiElements.getName(), this.userdevJar);
        for (var elements : List.of(runtimeElements, sourcesElements, clientExtraElements, serverExtraElements)) {
            elements.configure(configuration -> configuration.getOutgoing().getArtifacts().add(rawArtifact));
        }

        var userdev = components.adhoc("cleanroomUserdev");
        project.getComponents().add(userdev);
        userdev.addVariantsFromConfiguration(apiElements.get(), details -> details.mapToMavenScope("compile"));
        userdev.addVariantsFromConfiguration(runtimeElements.get(), details -> details.mapToMavenScope("runtime"));
        userdev.addVariantsFromConfiguration(sourcesElements.get(), details -> details.mapToOptional());
        userdev.addVariantsFromConfiguration(clientExtraElements.get(), details -> details.mapToOptional());
        userdev.addVariantsFromConfiguration(serverExtraElements.get(), details -> details.mapToOptional());
        for (var platform : Platform.nativePlatforms()) {
            userdev.addVariantsFromConfiguration(nativesVariant(project, minecraft, targetJavaMajor, platform).get(),
                    details -> details.mapToOptional());
        }
        return userdev;
    }

    private static NamedDomainObjectProvider<Configuration> nativesVariant(
            Project project, MinecraftExtension minecraft, Provider<Integer> targetJavaMajor, Platform platform) {
        var classifier = platform.lwjglNativesClassifier();
        var suffix = Arrays.stream(classifier.split("-"))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining());
        var natives = project.getConfigurations().register("cleanroomUserdev" + suffix, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
            configuration.setDescription("Native libraries a userdev workspace on " + classifier + " resolves.");
            configuration.getDependencies().addAllLater(minecraft.getVersionMeta().map(meta -> {
                var dependencies = new ArrayList<Dependency>();
                LwjglNatives.addFor(project, dependencies, classifier);
                VanillaTasks.addNativesFor(project.getDependencyFactory(), dependencies, meta, Map.of(), platform);
                return dependencies;
            }));
        });
        return project.getConfigurations().register("cleanroomUserdev" + suffix + "Elements", configuration -> {
            userdevVariant(project, configuration, Usage.JAVA_RUNTIME, Category.LIBRARY,
                    UserdevAttributes.NATIVES, targetJavaMajor);
            configuration.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE,
                    project.getObjects().named(OperatingSystemFamily.class, platform.operatingSystemFamily()));
            configuration.getAttributes().attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE,
                    project.getObjects().named(MachineArchitecture.class, platform.machineArchitecture()));
            configuration.extendsFrom(natives.get());
        });
    }

    private static void userdevVariant(Project project, org.gradle.api.artifacts.Configuration configuration,
                                       String usage, String category, String role,
                                       Provider<Integer> targetJavaMajor) {
        configuration.setCanBeConsumed(true);
        configuration.setCanBeResolved(false);
        configuration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE,
                project.getObjects().named(Usage.class, usage));
        configuration.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE,
                project.getObjects().named(Category.class, category));
        configuration.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        configuration.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE,
                project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
        configuration.getAttributes().attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJavaMajor);
        configuration.getAttributes().attribute(UserdevAttributes.ROLE, role);
    }

    /**
     * The specification version: the last release tag, i.e. the project version with any {@code +build...} suffix removed.
     */
    private static String specVersion(String version) {
        var idx = version.indexOf('+');
        return idx == -1 ? version : version.substring(0, idx);
    }

    private static Provider<String> universalRepositoryUrl(Project project) {
        return project.getProviders().provider(() -> {
            var publishing = project.getExtensions().getByType(PublishingExtension.class);
            var urls = publishing.getRepositories().withType(MavenArtifactRepository.class).stream()
                    .map(repository -> LibraryJson.trailingSlash(repository.getUrl().toString()))
                    .collect(Collectors.toSet());
            if (urls.size() != 1) {
                throw new GradleException("Cleanroom distribution requires exactly one Maven publication repository; found "
                        + urls);
            }
            return urls.iterator().next();
        });
    }

}
