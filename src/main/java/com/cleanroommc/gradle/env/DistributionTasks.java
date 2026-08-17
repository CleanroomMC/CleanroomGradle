package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.dist.WriteUserdevConfig;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Category;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers the Cleanroom release/distribution pipeline.
 * Only instantiated when {@code cleanroom { loaderProject = true }}.
 */
public final class DistributionTasks {

    private static final String GROUP_NAME = "distribution";
    private static final String MINECRAFT_VERSION = "1.12.2";
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

        var mainSourceSet = project.getExtensions().getByType(JavaPluginExtension.class)
                .getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);
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
        Tasks.group("build", this.reobfJar);
        Tasks.group(GROUP_NAME, this.universalJar, this.userdevJar, this.javadocJar);
        project.getTasks().named("assemble").configure(task -> task.dependsOn(this.universalJar, this.userdevJar, this.javadocJar));

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

            task.getMinecraftVersion().set(MINECRAFT_VERSION);
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

}
