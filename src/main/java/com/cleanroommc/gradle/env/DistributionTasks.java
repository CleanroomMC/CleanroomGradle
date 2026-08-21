package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip;
import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile;
import com.cleanroommc.gradle.api.task.dist.WriteUserdevConfig;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.dist.LibraryArtifact;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.MinimalJavadocOptions;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers the Cleanroom release/distribution pipeline.
 * Only instantiated for {@code cleanroom { mode = ProjectMode.LOADER }}.
 */
public final class DistributionTasks {

    private static final String GROUP_NAME = "cleanroom distribution";
    private static final String MINECRAFT_VERSION = "1.12.2";
    private static final int MINIMUM_JAVA = 25;
    private static final String ARTIFACT_ID = "cleanroom";
    private static final String MINECRAFT_PACKAGE = "net/minecraft/";

    public static final String USERDEV_BINPATCHES = "binpatches.zip";
    public static final String USERDEV_CLIENT_BINPATCHES = "binpatch/client/";
    public static final String USERDEV_SERVER_BINPATCHES = "binpatch/server/";
    public static final String USERDEV_SRG2MCP = "srg2mcp.tsrg";
    public static final String USERDEV_MCP2SRG = "mcp2srg.tsrg";
    public static final String USERDEV_ATS = "ats";

    public final TaskProvider<WriteMappings> writeMcp2SrgDist, writeObf2SrgTsrg, writeMcp2Notch;
    public final TaskProvider<RenameJar> reobfJar, reobfMinecraftJar;
    public final TaskProvider<Jar> minecraftClassesJar, universalJar, userdevJar, javadocJar;
    public final TaskProvider<StripSideOnlyJar> stripClientMinecraftJar, stripServerMinecraftJar;
    public final TaskProvider<GenerateBinPatches> genClientBinPatches, genServerBinPatches;
    public final TaskProvider<Zip> genRuntimeBinPatches;
    public final TaskProvider<WriteUserdevConfig> writeUserdevConfig;
    public final TaskProvider<PublishMmcPackZip> publishMmcPackZip;
    public final TaskProvider<WriteInstallProfile> writeInstallProfile;
    public final TaskProvider<Jar> installerJar;

    public DistributionTasks(Project project, CleanroomExtension ext, VanillaTasks vanilla, MCPTasks mcp) {
        var layout = project.getLayout();
        var providers = project.getProviders();
        var group = String.valueOf(project.getGroup());
        var version = String.valueOf(project.getVersion());
        var titleProperty = providers.gradleProperty("title").orElse("Cleanroom");
        var vendorProperty = providers.gradleProperty("vendor").orElse("CleanroomMC");
        var timestampProperty = providers.environmentVariable("SOURCE_DATE_EPOCH")
                .map(Long::parseLong)
                .map(epoch ->
                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")))
                .orElse("1970-01-01T00:00:00+0000");

        var javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        var mainSourceSet = javaExtension.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);
        var jarTask = project.getTasks().named("jar", Jar.class);

        var srgMapping = ext.getVersionCacheDirectory().file("mcp_config/config/joined.tsrg");
        var mcpMappingsDir = mcp.extractMcpMappings.map(Copy::getDestinationDir);

        var extraFiles = project.fileTree(layout.getProjectDirectory(), spec -> spec.include(
                "CREDITS.txt",
                "LICENSE.txt",
                "LICENSE",
                "CHANGELOG.md",
                "LICENSE-Paulscode IBXM Library.txt",
                "LICENSE-Paulscode SoundSystem CodecIBXM.txt"
        ));

        // TODO: make this less hardcoded
        var distributionRepositories = new LinkedHashMap<String, String>();
        distributionRepositories.put("*", "https://repo.maven.apache.org/maven2/");
        distributionRepositories.put("com.cleanroommc", Meta.CLEANROOM_REPO);
        distributionRepositories.put("top.outlands", Meta.CLEANROOM_REPO);
        distributionRepositories.put("net.minecraftforge", Meta.FORGE_REPO);
        distributionRepositories.put("de.oceanlabs.mcp", Meta.FORGE_REPO);
        distributionRepositories.put("com.mojang", Meta.MOJANG_REPO);

        var installerBase = Objects.config(project, "installerBase");
        installerBase.configure(config -> {
            config.setDescription("The Cleanroom Installer runtime that installerJar re-packages.");
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
            config.setTransitive(false);
        });
        project.getDependencies().addProvider(installerBase.getName(),
                ext.getInstallerVersion().map(installerVersion -> "com.cleanroommc:installer:" + installerVersion));

        var runtimeClasspath = project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        var mmcPackLibraries = Objects.config(project, "mmcPackLibraries");
        mmcPackLibraries.configure(config -> {
            config.setDescription("Runtime libraries embedded as hashed download metadata in the MMC/Prism pack.");
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
            config.extendsFrom(runtimeClasspath.get(), project.getConfigurations().getByName(LwjglNatives.ALL_CONFIGURATION_NAME));
            config.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            });
        });
        var mmcLibraryArtifacts = mmcPackLibraries.flatMap(config -> config.getIncoming().getArtifacts().getResolvedArtifacts())
                .map(artifacts -> artifacts.stream()
                        .filter(artifact -> artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)
                        .sorted(Comparator.comparing(DistributionTasks::resolvedCoordinate))
                        .map(artifact -> {
                            var input = project.getObjects().newInstance(LibraryArtifact.class);
                            input.getCoordinate().set(resolvedCoordinate(artifact));
                            input.getFile().fileValue(artifact.getFile());
                            return input;
                        })
                        .toList()
                );

        this.writeMcp2SrgDist = Tasks.register(project, "writeMcp2SrgDist", WriteMappings.class);
        this.writeObf2SrgTsrg = Tasks.register(project, "writeObf2SrgTsrg", WriteMappings.class);
        this.writeMcp2Notch = mcp.registerMcp2Notch(project, ext);
        this.reobfJar = project.getTasks().register("reobfJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.minecraftClassesJar = Tasks.register(project, "minecraftClassesJar", Jar.class);
        this.reobfMinecraftJar = project.getTasks().register("reobfMinecraftJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
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
        this.writeInstallProfile = Tasks.register(project, "writeInstallProfile", WriteInstallProfile.class);
        this.installerJar = Tasks.register(project, "installerJar", Jar.class);
        Tasks.group("build", this.reobfJar);
        Tasks.group(GROUP_NAME, this.universalJar, this.userdevJar, this.javadocJar, this.publishMmcPackZip, this.installerJar);
        project.getTasks().named("assemble").configure(task ->
                task.dependsOn(this.universalJar, this.userdevJar, this.javadocJar, this.publishMmcPackZip));

        this.writeMcp2SrgDist.configure(task -> {
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
        this.writeObf2SrgTsrg.configure(task -> {
            task.dependsOn(mcp.extractMcpConfig);

            task.getJoinedSrgFile().set(srgMapping);
            task.getDirection().set(WriteMappings.Direction.OBF_TO_SRG);
            task.getFormat().set(IMappingFile.Format.TSRG);
            task.getOutput().set(ext.getLocalCacheDirectory().file("mappings/obf2srg.tsrg"));
        });
        this.reobfJar.configure(task -> {
            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2SrgDist.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(ext.getLocalCacheDirectory().file("dist/reobf/" + ARTIFACT_ID + "-srg.jar"));
        });
        this.minecraftClassesJar.configure(task -> {
            task.setDescription("Repackages only the patched-Minecraft portion of the main jar for binpatch generation.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(project.zipTree(jarTask.flatMap(Jar::getArchiveFile)), spec -> spec.include("net/minecraft/**"));
            task.getDestinationDirectory().set(ext.getLocalCacheDirectory().dir("dist/reobf"));
            task.getArchiveFileName().set("minecraft-mcp.jar");
        });
        this.reobfMinecraftJar.configure(task -> {
            task.getInput().set(this.minecraftClassesJar.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2Notch.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(ext.getLocalCacheDirectory().file("dist/reobf/minecraft-notch.jar"));
        });
        this.stripClientMinecraftJar.configure(task -> {
            task.getInputJar().set(this.reobfMinecraftJar.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.CLIENT);
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("dist/reobf/minecraft-client.jar"));
        });
        this.stripServerMinecraftJar.configure(task -> {
            task.getInputJar().set(this.reobfMinecraftJar.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.SERVER);
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("dist/reobf/minecraft-server.jar"));
        });
        this.genClientBinPatches.configure(task -> {
            task.getOriginalJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getModifiedJar().set(this.stripClientMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getIncludedPrefixes().add(MINECRAFT_PACKAGE);
            task.getBinpatches().set(ext.getLocalCacheDirectory().file("binpatches/client.zip"));
        });
        this.genServerBinPatches.configure(task -> {
            task.getOriginalJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getModifiedJar().set(this.stripServerMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getIncludedPrefixes().add(MINECRAFT_PACKAGE);
            task.getBinpatches().set(ext.getLocalCacheDirectory().file("binpatches/server.zip"));
        });
        this.genRuntimeBinPatches.configure(task -> {
            task.setDescription("Merges client and server binpatches into a single archive for runtime.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(project.zipTree(this.genClientBinPatches.flatMap(GenerateBinPatches::getBinpatches)),
                    spec -> spec.into("binpatch/client"));
            task.from(project.zipTree(this.genServerBinPatches.flatMap(GenerateBinPatches::getBinpatches)),
                    spec -> spec.into("binpatch/server"));
            task.getDestinationDirectory().set(ext.getLocalCacheDirectory().dir("binpatches"));
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

            task.from(project.zipTree(this.reobfJar.flatMap(RenameJar::getOutput)), spec -> spec.exclude("net/minecraft/**"));
            task.from(extraFiles);
            task.from(this.writeObf2SrgTsrg.flatMap(WriteMappings::getOutput),
                    spec -> spec.rename(name -> "deobf_data-" + MINECRAFT_VERSION + ".tsrg"));
            task.from(this.genRuntimeBinPatches.flatMap(Zip::getArchiveFile),
                    spec -> spec.rename(name -> "binpatches.zip"));

            var forgeVersion = ext.getForgeVersion();
            task.doFirst("configureManifest", t -> {
                var jar = (Jar) t;

                // TODO needed? or leave to buildscript
                Map<String, Object> main = new LinkedHashMap<>();
                main.put("Timestamp", timestampProperty.get());
                // TODO
                main.put("Main-Class", "net.minecraftforge.fml.relauncher.ServerLaunchWrapper");
                main.put("Tweak-Class", "net.minecraftforge.fml.common.launcher.FMLTweaker");
                jar.getManifest().attributes(main);

                Map<String, Object> forgeSection = new LinkedHashMap<>();
                forgeSection.put("Specification-Title", titleProperty.get());
                forgeSection.put("Specification-Vendor", vendorProperty.get());
                forgeSection.put("Specification-Version", specVersion(version));
                forgeSection.put("Implementation-Title", group);
                forgeSection.put("Implementation-Version", forgeVersion.get());
                forgeSection.put("Implementation-Vendor", vendorProperty.get());
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
            task.getForgeVersion().set(ext.getForgeVersion());
            task.getMcpConfig().set(mcp.mcpConfig.map(Objects::notation));
            task.getBinpatches().set(USERDEV_BINPATCHES);
            task.getClientBinpatches().set(USERDEV_CLIENT_BINPATCHES);
            task.getServerBinpatches().set(USERDEV_SERVER_BINPATCHES);
            task.getSrg2Mcp().set(USERDEV_SRG2MCP);
            task.getMcp2Srg().set(USERDEV_MCP2SRG);
            task.getAccessTransformers().set(ext.getAccessTransformers().getElements().map(files ->
                    files.stream().map(file -> USERDEV_ATS + "/" + file.getAsFile().getName()).toList()));
            task.getLibraries().set(resolvedLibraries(project).zip(LwjglNatives.publishedCoordinates(project, ext), DistributionTasks::mergeLibraries));
            task.getLoaderGroup().set(group);
            task.getOutput().set(ext.getLocalCacheDirectory().file("dist/" + UserdevConfig.FILE_NAME));
        });
        this.userdevJar.configure(task -> {
            task.setDescription("Assembles the userdev jar for mod developers.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("userdev");

            // SRG-named
            task.from(project.zipTree(this.reobfJar.flatMap(RenameJar::getOutput)), spec -> spec.exclude(MINECRAFT_PACKAGE + "**"));
            task.from(this.genRuntimeBinPatches.flatMap(Zip::getArchiveFile),
                    spec -> spec.rename(name -> USERDEV_BINPATCHES));
            task.from(ext.getAccessTransformers(), spec -> spec.into(USERDEV_ATS));
            task.from(mcp.writeSrg2Mcp.flatMap(WriteMappings::getOutput),
                    spec -> spec.rename(name -> USERDEV_SRG2MCP));
            task.from(this.writeMcp2SrgDist.flatMap(WriteMappings::getOutput),
                    spec -> spec.rename(name -> USERDEV_MCP2SRG));
            task.from(this.writeUserdevConfig.flatMap(WriteUserdevConfig::getOutput));
        });
        this.publishMmcPackZip.configure(task -> {
            task.setDescription("Publishes a minimal MultiMC/PrismLauncher import ZIP.");

            task.getInstanceName().set(titleProperty);
            task.getCleanroomVersion().set(version);
            task.getMainClass().set("top.outlands.foundation.boot.Foundation");
            task.getTweakers().add("net.minecraftforge.fml.common.launcher.FMLTweaker");
            task.getCompatibleJavaMajors().set(javaExtension.getToolchain().getLanguageVersion()
                    .map(languageVersion -> List.of(languageVersion.asInt()))
                    .orElse(List.of()));
            task.getUniversalCoordinate().set(group + ":" + ARTIFACT_ID + ":" + version + ":universal");
            task.getUniversalUrl().set(Meta.CLEANROOM_REPO
                    + group.replace('.', '/') + "/" + ARTIFACT_ID + "/" + version + "/"
                    + ARTIFACT_ID + "-" + version + "-universal.jar");
            task.getUniversalJar().set(this.universalJar.flatMap(Jar::getArchiveFile));
            task.getLibraries().set(mmcLibraryArtifacts);
            task.getInheritedLibraries().set(ext.getVersionMeta().map(meta -> meta.libraries().stream()
                    .map(library -> library.name())
                    .collect(Collectors.toCollection(LinkedHashSet::new))));
            task.getRepositoryUrls().set(distributionRepositories);
            // Every published pack so far is the classifier-less zip beside the jars; keep that name.
            task.getArchiveFile().set(layout.getBuildDirectory().file("libs/" + ARTIFACT_ID + "-" + version + ".zip"));
        });
        this.writeInstallProfile.configure(task -> {
            task.setDescription("Writes install_profile.json and version.json for the Cleanroom installer.");

            task.getProfileName().set(titleProperty);
            task.getCleanroomVersion().set(version);
            task.getVersionId().set(MINECRAFT_VERSION + "-" + titleProperty.get() + "-" + version);
            task.getMainClass().set("top.outlands.foundation.boot.Foundation");
            task.getServerMainClass().set("top.outlands.foundation.boot.Foundation");
            task.getTweakers().add("net.minecraftforge.fml.common.launcher.FMLTweaker");
            task.getServerTweakers().add("net.minecraftforge.fml.common.launcher.FMLServerTweaker");
            task.getJvmArgs().set(ext.getInstallerJvmArgs());
            task.getMinimumJava().set(MINIMUM_JAVA);
            task.getRecommendedJava().set(javaExtension.getToolchain().getLanguageVersion()
                    .map(JavaLanguageVersion::asInt)
                    .orElse(MINIMUM_JAVA)
            );
            task.getUniversalCoordinate().set(group + ":" + ARTIFACT_ID + ":" + version + ":universal");
            task.getUniversalJar().set(this.universalJar.flatMap(Jar::getArchiveFile));
            task.getLibraries().set(mmcLibraryArtifacts);
            task.getInheritedLibraries().set(ext.getVersionMeta().map(meta -> meta.libraries().stream()
                    .map(library -> library.name())
                    .collect(Collectors.toCollection(LinkedHashSet::new))));
            task.getRepositoryUrls().set(distributionRepositories);
            task.getVersionMeta().set(ext.getVersionMeta());
            task.getReleaseTime().set(timestampProperty);
            task.getInstallProfile().set(ext.getLocalCacheDirectory().map(dir -> dir.file("dist/install_profile.json")));
            task.getVersionJson().set(ext.getLocalCacheDirectory().map(dir -> dir.file("dist/version.json")));
        });

        this.installerJar.configure(task -> {
            task.setDescription("Assembles the version-pinned Cleanroom installer jar.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("installer");

            task.from(project.zipTree(installerBase.map(config -> {
                var files = config.getFiles();
                if (files.size() != 1) {
                    throw new IllegalStateException("Expected exactly one installer runtime jar on the "
                            + installerBase.getName() + " configuration, resolved " + files
                            + ". Set cleanroom.installerVersion to a published com.cleanroommc:installer release.");
                }
                return files.iterator().next();
            })), spec -> spec.exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"));
            task.from(this.writeInstallProfile.flatMap(WriteInstallProfile::getInstallProfile));
            task.from(this.writeInstallProfile.flatMap(WriteInstallProfile::getVersionJson));
            task.from(this.universalJar.flatMap(Jar::getArchiveFile), spec -> {
                spec.into("maven/" + group.replace('.', '/') + "/" + ARTIFACT_ID + "/" + version);
                spec.rename(_ -> ARTIFACT_ID + "-" + version + ".jar");
            });
            task.from(this.publishMmcPackZip.flatMap(PublishMmcPackZip::getArchiveFile), spec -> {
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

        var intermediates = IntermediateProcessor.of(project);
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

    /** The specification version: the last release tag, i.e. the project version with any {@code +build...} suffix removed. */
    private static String specVersion(String version) {
        var idx = version.indexOf('+');
        return idx == -1 ? version : version.substring(0, idx);
    }

    private static Provider<List<String>> resolvedLibraries(Project project) {
        return project.getConfigurations().named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .flatMap(config -> config.getIncoming().getResolutionResult().getRootComponent())
                .map(root -> {
                    var coordinates = new LinkedHashSet<String>();
                    collectModules(root, coordinates, new HashSet<>());
                    return List.copyOf(coordinates);
                });
    }

    private static void collectModules(ResolvedComponentResult component, Set<String> coordinates, Set<ComponentIdentifier> seen) {
        if (!seen.add(component.getId())) {
            return;
        }
        // A platform contributes versions rather than a jar, so it is not a library to put on a classpath
        if (component.getId() instanceof ModuleComponentIdentifier module && !isPlatform(component)) {
            coordinates.add(module.getGroup() + ":" + module.getModule() + ":" + module.getVersion());
        }
        for (var dependency : component.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                collectModules(resolved.getSelected(), coordinates, seen);
            }
        }
    }

    private static boolean isPlatform(ResolvedComponentResult component) {
        for (var variant : component.getVariants()) {
            var attributes = variant.getAttributes();
            for (var attribute : attributes.keySet()) {
                if (!Category.CATEGORY_ATTRIBUTE.getName().equals(attribute.getName())) {
                    continue;
                }
                var category = String.valueOf(attributes.getAttribute(attribute));
                if (Category.REGULAR_PLATFORM.equals(category) || Category.ENFORCED_PLATFORM.equals(category)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> mergeLibraries(List<String> modules, List<String> natives) {
        var versions = new LinkedHashMap<String, String>();
        for (var module : modules) {
            var parts = module.split(":");
            versions.put(parts[0] + ":" + parts[1], parts[2]);
        }
        var libraries = new LinkedHashSet<>(modules);
        for (var nativeLibrary : natives) {
            var parts = nativeLibrary.split(":");
            var module = parts[0] + ":" + parts[1];
            var version = versions.get(module);
            if (version == null) {
                continue;
            }
            libraries.add(module + ":" + version + ":" + parts[2]);
        }
        return List.copyOf(libraries);
    }

    private static String resolvedCoordinate(ResolvedArtifactResult artifact) {
        if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier module)) {
            throw new IllegalArgumentException("MMC packs can only publish module artifacts: " + artifact.getId());
        }
        var fileName = artifact.getFile().getName();
        var dot = fileName.lastIndexOf('.');
        var extension = dot == -1 ? "jar" : fileName.substring(dot + 1);
        var stem = dot == -1 ? fileName : fileName.substring(0, dot);
        var prefix = module.getModule() + "-" + module.getVersion();
        if (!stem.equals(prefix) && !stem.startsWith(prefix + "-")) {
            throw new IllegalArgumentException("Cannot derive the classifier for " + artifact.getId()
                    + " from file " + fileName);
        }
        var classifier = stem.length() == prefix.length() ? "" : stem.substring(prefix.length() + 1);
        var coordinate = module.getGroup() + ":" + module.getModule() + ":" + module.getVersion();
        if (!classifier.isEmpty()) {
            coordinate += ":" + classifier;
        }
        if (!extension.equals("jar")) {
            coordinate += "@" + extension;
        }
        return coordinate;
    }

}
