package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.LoaderExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip;
import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile;
import com.cleanroommc.gradle.api.task.dist.WriteUserdevConfig;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.Property;
import com.cleanroommc.gradle.api.util.dist.Coordinate;
import com.cleanroommc.gradle.api.util.dist.LibraryJson;
import com.cleanroommc.gradle.api.util.dist.ResolvedLibraries;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.MinimalJavadocOptions;

import java.time.Instant;
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
    public final TaskProvider<Jar> minecraftClassesJar, deobfLibraryJar, universalJar, userdevJar, javadocJar;
    public final TaskProvider<StripSideOnlyJar> stripClientMinecraftJar, stripServerMinecraftJar;
    public final TaskProvider<GenerateBinPatches> genClientBinPatches, genServerBinPatches;
    public final TaskProvider<Zip> genRuntimeBinPatches;
    public final TaskProvider<WriteUserdevConfig> writeUserdevConfig;
    public final TaskProvider<PublishMmcPackZip> publishMmcPackZip;
    public final TaskProvider<Zip> installerMmcPackZip;
    public final TaskProvider<WriteInstallProfile> writeInstallProfile;
    public final TaskProvider<Jar> installerJar;

    public DistributionTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, LoaderExtension loader,
                             VanillaTasks vanilla, McpMappings mappings, IntermediateProcessor intermediates) {
        var layout = project.getLayout();
        var providers = project.getProviders();
        var group = String.valueOf(project.getGroup());
        var version = String.valueOf(project.getVersion());
        var minecraftVersion = vanilla.minecraftVersion;
        var universal = new Coordinate(group, ARTIFACT_ID, version, "universal", "jar");
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
        var resolvedRepositoryUrls = repositoryUrls(project);

        var installerBase = Objects.config(project, "installerBase");
        installerBase.configure(config -> {
            config.setDescription("The Cleanroom Installer runtime that installerJar re-packages.");
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
            config.setTransitive(false);
        });
        project.getDependencies().addProvider(installerBase.getName(),
                loader.getInstallerVersion().map(installerVersion -> "com.cleanroommc:installer:" + installerVersion));

        var runtimeClasspath = project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        var distributionLibraries = Objects.config(project, "distributionLibraries");
        distributionLibraries.configure(config -> {
            config.setDescription("Resolved classpath and LWJGL native libraries shared by all Cleanroom distributions.");
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
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
        var distributionNatives = Objects.config(project, "distributionNatives");
        distributionNatives.configure(config -> {
            config.setDescription("Vanilla libraries extracted as natives by Cleanroom distributions, for every platform.");
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
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
                .map(url -> LibraryJson.trailingSlash(url) + universal.mavenPath());

        this.writeMcp2Srg = mappings.write(project, caches, "writeMcp2Srg", WriteMappings.Direction.MCP_TO_SRG, UserdevConfig.MCP2SRG);
        this.writeObf2SrgTsrg = mappings.write(project, caches, "writeObf2SrgTsrg", WriteMappings.Direction.OBF_TO_SRG, "obf2srg.tsrg");
        this.writeMcp2Notch = mappings.write(project, caches, "writeMcp2Notch", WriteMappings.Direction.MCP_TO_NOTCH, "mcp2notch.tsrg");
        this.reobfJar = Tasks.register(project, "reobfJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.minecraftClassesJar = Tasks.register(project, "minecraftClassesJar", Jar.class);
        this.deobfLibraryJar = Tasks.register(project, "deobfLibraryJar", Jar.class);
        this.reobfMinecraftJar = Tasks.register(project, "reobfMinecraftJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.stripClientMinecraftJar = Tasks.register(project, "stripClientMinecraftJar", StripSideOnlyJar.class);
        this.stripServerMinecraftJar = Tasks.register(project, "stripServerMinecraftJar", StripSideOnlyJar.class);
        this.genClientBinPatches = Tasks.register(project, "genClientBinPatches", GenerateBinPatches.class);
        this.genServerBinPatches = Tasks.register(project, "genServerBinPatches", GenerateBinPatches.class);
        this.genRuntimeBinPatches = Tasks.register(project, "genRuntimeBinPatches", Zip.class);
        this.universalJar = Tasks.register(project, "universalJar", Jar.class);
        this.userdevJar = Tasks.register(project, "userdevJar", Jar.class);
        this.javadocJar = Tasks.register(project, "javadocJar", Jar.class);
        this.writeUserdevConfig = Tasks.register(project, "writeUserdevConfig", WriteUserdevConfig.class);
        this.publishMmcPackZip = Tasks.register(project, "publishMmcPackZip", PublishMmcPackZip.class);
        this.installerMmcPackZip = Tasks.register(project, "packageInstallerMmcPackZip", Zip.class);
        this.writeInstallProfile = Tasks.register(project, "writeInstallProfile", WriteInstallProfile.class);
        this.installerJar = Tasks.register(project, "installerJar", Jar.class);
        var clientTweakClass = loader.getClientTweakClass();
        var serverTweakClass = loader.getServerTweakClass();
        var launchClass = loader.getLaunchClass();
        Tasks.group("build", this.reobfJar);
        Tasks.group(GROUP_NAME, this.universalJar, this.userdevJar, this.javadocJar, this.publishMmcPackZip, this.installerJar);
        project.getTasks().named("assemble").configure(task ->
                task.dependsOn(this.universalJar, this.userdevJar, this.javadocJar, this.publishMmcPackZip));

        this.reobfJar.configure(task -> {
            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Srg.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(caches.getLocalDirectory().file("dist/reobf/" + ARTIFACT_ID + "-srg.jar"));
        });
        this.minecraftClassesJar.configure(task -> {
            task.setDescription("Repackages only the patched-Minecraft portion of the main jar for binpatch generation.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(archives.zipTree(jarTask.flatMap(Jar::getArchiveFile)), spec -> spec.include(Meta.MINECRAFT_PACKAGE_PATH + "**"));
            task.getDestinationDirectory().set(caches.getLocalDirectory().dir("dist/reobf"));
            task.getArchiveFileName().set("minecraft-mcp.jar");
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
            task.getInput().set(this.minecraftClassesJar.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Notch.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath), jarTask.flatMap(Jar::getArchiveFile));
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
        this.genClientBinPatches.configure(task -> {
            task.getOriginalJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getModifiedJar().set(this.stripClientMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getIncludedPrefixes().add(Meta.MINECRAFT_PACKAGE_PATH);
            task.getObfuscationMappings().set(this.writeObf2SrgTsrg.flatMap(WriteMappings::getOutput));
            task.getBinpatches().set(caches.getLocalDirectory().file("binpatches/client.zip"));
        });
        this.genServerBinPatches.configure(task -> {
            task.getOriginalJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getModifiedJar().set(this.stripServerMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getIncludedPrefixes().add(Meta.MINECRAFT_PACKAGE_PATH);
            task.getObfuscationMappings().set(this.writeObf2SrgTsrg.flatMap(WriteMappings::getOutput));
            task.getBinpatches().set(caches.getLocalDirectory().file("binpatches/server.zip"));
        });
        this.genRuntimeBinPatches.configure(task -> {
            task.setDescription("Merges client and server binpatches into a single archive for runtime.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(archives.zipTree(this.genClientBinPatches.flatMap(GenerateBinPatches::getBinpatches)),
                    spec -> spec.into("binpatch/client"));
            task.from(archives.zipTree(this.genServerBinPatches.flatMap(GenerateBinPatches::getBinpatches)),
                    spec -> spec.into("binpatch/server"));
            task.getDestinationDirectory().set(caches.getLocalDirectory().dir("binpatches"));
            task.getArchiveFileName().set("runtime.zip");
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
            task.from(this.genRuntimeBinPatches.flatMap(Zip::getArchiveFile),
                    spec -> spec.rename(name -> UserdevConfig.BINPATCHES));

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
                forgeSection.put("Specification-Version", specVersion(version));
                forgeSection.put("Implementation-Title", group);
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
            task.getBinpatches().set(UserdevConfig.meta(UserdevConfig.BINPATCHES));
            task.getClientBinpatches().set(UserdevConfig.CLIENT_BINPATCHES);
            task.getServerBinpatches().set(UserdevConfig.SERVER_BINPATCHES);
            task.getSrg2Mcp().set(UserdevConfig.meta(UserdevConfig.SRG2MCP));
            task.getMcp2Srg().set(UserdevConfig.meta(UserdevConfig.MCP2SRG));
            task.getAccessTransformers().set(loader.getAccessTransformers().getElements().map(files ->
                    files.stream().map(file -> UserdevConfig.meta(UserdevConfig.ATS) + "/" + file.getAsFile().getName()).toList()));
            task.getLibraries().set(ResolvedLibraries.modules(project.getConfigurations()
                    .named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                    .flatMap(config -> config.getIncoming().getResolutionResult().getRootComponent()))
                    .zip(LwjglNatives.publishedCoordinates(project, loader.getLwjglNativesClassifiers()), ResolvedLibraries::mergeNatives));
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

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("userdev");

            // SRG-named
            task.from(archives.zipTree(this.reobfJar.flatMap(RenameJar::getOutput)), spec -> spec.exclude(Meta.MINECRAFT_PACKAGE_PATH + "**"));
            task.from(this.genRuntimeBinPatches.flatMap(Zip::getArchiveFile),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.BINPATCHES));
            task.from(loader.getAccessTransformers(),
                    spec -> spec.into(UserdevConfig.meta(UserdevConfig.ATS)));
            task.from(mappings.writeSrg2Mcp.flatMap(WriteMappings::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.SRG2MCP));
            task.from(this.deobfLibraryJar.flatMap(Jar::getArchiveFile),
                    spec -> spec.into(UserdevConfig.META));
            task.from(this.writeMcp2Srg.flatMap(WriteMappings::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.MCP2SRG));
            task.from(this.writeUserdevConfig.flatMap(WriteUserdevConfig::getOutput),
                    spec -> spec.into(UserdevConfig.META).rename(name -> UserdevConfig.FILE_NAME));
        });
        this.publishMmcPackZip.configure(task -> {
            task.setDescription("Publishes a minimal MultiMC/PrismLauncher import ZIP.");

            task.getInstanceName().set(titleProperty);
            task.getCleanroomVersion().set(version);
            task.getMainClass().set(launchClass);
            task.getTweakers().set(clientTweakClass.map(List::of));
            task.getCompatibleJavaMajors().set(targetJavaMajor.map(List::of));
            task.getUniversalCoordinate().set(universal.serialized());
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
            // Every published pack so far is the classifier-less zip beside the jars; keep that name.
            task.getArchiveFile().set(layout.getBuildDirectory().file("libs/" + ARTIFACT_ID + "-" + version + ".zip"));
        });
        this.installerMmcPackZip.configure(task -> {
            task.setDescription("Packages the MMC instance metadata embedded in installerJar without duplicating its universal jar.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
            task.from(archives.zipTree(this.publishMmcPackZip.flatMap(PublishMmcPackZip::getArchiveFile)),
                    spec -> spec.exclude("libraries/" + universal.fileName()));
            task.getDestinationDirectory().set(caches.getLocalDirectory().dir("dist"));
            task.getArchiveFileName().set("mmc-installer.zip");
        });
        this.writeInstallProfile.configure(task -> {
            task.setDescription("Writes install_profile.json and version.json for the Cleanroom installer.");

            task.getProfileName().set(titleProperty);
            task.getCleanroomVersion().set(version);
            task.getVersionId().set(titleProperty + "-" + version);
            task.getMainClass().set(launchClass);
            task.getServerMainClass().set(launchClass);
            task.getTweakers().set(clientTweakClass.map(List::of));
            task.getServerTweakers().set(serverTweakClass.map(List::of));
            task.getJvmArgs().set(loader.getInstallerJvmArgs());
            task.getMinimumJava().set(targetJavaMajor);
            task.getRecommendedJava().set(targetJavaMajor);
            task.getUniversalCoordinate().set(universal.serialized());
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
                spec.into("maven/" + group.replace('.', '/') + "/" + ARTIFACT_ID + "/" + version);
                spec.rename(_ -> universal.fileName());
            });
            task.from(this.installerMmcPackZip.flatMap(Zip::getArchiveFile), spec -> {
                spec.into("mmc");
                spec.rename(_ -> "pack.zip");
            });
            task.getManifest().attributes(Map.of(
                    "Main-Class", "com.cleanroommc.installer.Main",
                    "Implementation-Version", version,
                    "Cleanroom-Version", version)
            );
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

        intermediates.discardAfterAll("discardReobfLoaderJar",
                List.of(this.universalJar, this.userdevJar),
                this.reobfJar.flatMap(RenameJar::getOutput)
        );
        intermediates.discardAfter(this.reobfMinecraftJar, this.minecraftClassesJar.flatMap(Jar::getArchiveFile));
        intermediates.discardAfterAll("discardNotchMinecraftJar",
                List.of(this.stripClientMinecraftJar, this.stripServerMinecraftJar),
                this.reobfMinecraftJar.flatMap(RenameJar::getOutput)
        );
        intermediates.discardAfter(this.genClientBinPatches, this.stripClientMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
        intermediates.discardAfter(this.genServerBinPatches, this.stripServerMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
        intermediates.discardAfterAll("discardSideBinPatches",
                List.of(this.genRuntimeBinPatches),
                this.genClientBinPatches.flatMap(GenerateBinPatches::getBinpatches),
                this.genServerBinPatches.flatMap(GenerateBinPatches::getBinpatches)
        );
    }

    /**
     * The specification version: the last release tag, i.e. the project version with any {@code +build...} suffix removed.
     */
    private static String specVersion(String version) {
        var idx = version.indexOf('+');
        return idx == -1 ? version : version.substring(0, idx);
    }

    private static Map<String, String> repositoryUrls(Project project) {
        var repositories = new HashMap<String, String>();
        project.getRepositories().withType(MavenArtifactRepository.class).forEach(repository ->
                repositories.put(repository.getName(), LibraryJson.trailingSlash(repository.getUrl().toString())));
        return Map.copyOf(repositories);
    }

    private static Provider<String> universalRepositoryUrl(Project project) {
        return project.getProviders().provider(() -> {
            var publishing = project.getExtensions().findByType(PublishingExtension.class);
            if (publishing == null) {
                throw new GradleException("Cleanroom distribution requires the maven-publish plugin");
            }
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
