package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.dist.LzmaCompress;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the Cleanroom release/distribution pipeline.
 * Only instantiated when {@code cleanroom { loaderProject = true }}.
 */
public final class DistributionTasks {

    private static final String GROUP_NAME = "Distribution Tasks";
    private static final String MINECRAFT_VERSION = "1.12.2";
    private static final String ARTIFACT_ID = "cleanroom";

    public final TaskProvider<WriteMappings> writeMcp2SrgDist, writeObf2SrgTsrg;
    public final TaskProvider<RenameJar> reobfJar, reobfMinecraftJar;
    public final TaskProvider<Jar> minecraftClassesJar, universalJar, userdevJar, javadocJar;
    public final TaskProvider<StripSideOnlyJar> stripClientMinecraftJar, stripServerMinecraftJar;
    public final TaskProvider<GenerateBinPatches> genClientBinPatches, genServerBinPatches;
    public final TaskProvider<Zip> genRuntimeBinPatches;
    public final TaskProvider<LzmaCompress> deobfDataLzma;

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

        // TODO: Evaluate
        // Old Forge files
        var extraFiles = project.files(
                "CREDITS.txt",
                "LICENSE.txt",
                "LICENSE-Paulscode IBXM Library.txt",
                "LICENSE-Paulscode SoundSystem CodecIBXM.txt",
                layout.getBuildDirectory().file("changelog.txt"));

        this.writeMcp2SrgDist = Tasks.of(project, GROUP_NAME, "writeMcp2SrgDist", WriteMappings.class);
        this.writeObf2SrgTsrg = Tasks.of(project, GROUP_NAME, "writeObf2SrgTsrg", WriteMappings.class);
        this.reobfJar = project.getTasks().register("reobfJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.minecraftClassesJar = Tasks.of(project, GROUP_NAME, "minecraftClassesJar", Jar.class);
        this.reobfMinecraftJar = project.getTasks().register("reobfMinecraftJar", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.stripClientMinecraftJar = Tasks.of(project, GROUP_NAME, "stripClientMinecraftJar", StripSideOnlyJar.class);
        this.stripServerMinecraftJar = Tasks.of(project, GROUP_NAME, "stripServerMinecraftJar", StripSideOnlyJar.class);
        this.genClientBinPatches = Tasks.of(project, GROUP_NAME, "genClientBinPatches", GenerateBinPatches.class);
        this.genServerBinPatches = Tasks.of(project, GROUP_NAME, "genServerBinPatches", GenerateBinPatches.class);
        this.genRuntimeBinPatches = Tasks.of(project, GROUP_NAME, "genRuntimeBinPatches", Zip.class);
        this.deobfDataLzma = Tasks.of(project, GROUP_NAME, "deobfDataLzma", LzmaCompress.class);
        this.universalJar = Tasks.of(project, GROUP_NAME, "universalJar", Jar.class);

        this.writeMcp2SrgDist.configure(task -> {
            task.dependsOn(mcp.extractMcpConfig, mcp.extractMcpMappings);

            task.getJoinedSrgFile().set(srgMapping);
            task.getMethodMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "methods.csv")));
            task.getFieldMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "fields.csv")));
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
            task.setGroup(GROUP_NAME);
            task.dependsOn(jarTask, this.writeMcp2SrgDist);

            task.getInput().set(jarTask.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(this.writeMcp2SrgDist.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(ext.getLocalCacheDirectory().file("dist/reobf/" + ARTIFACT_ID + "-srg.jar"));
        });
        this.minecraftClassesJar.configure(task -> {
            task.dependsOn(jarTask);
            task.setDescription("Repackages only the patched-Minecraft portion of the main jar for binpatch generation.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(project.zipTree(jarTask.flatMap(Jar::getArchiveFile)), spec -> spec.include("net/minecraft/**"));
            task.getDestinationDirectory().set(ext.getLocalCacheDirectory().dir("dist/reobf"));
            task.getArchiveFileName().set("minecraft-mcp.jar");
        });
        this.reobfMinecraftJar.configure(task -> {
            task.setGroup(GROUP_NAME);
            task.dependsOn(this.minecraftClassesJar, mcp.writeMcp2Notch);

            task.getInput().set(this.minecraftClassesJar.flatMap(Jar::getArchiveFile));
            task.getMap().setFrom(mcp.writeMcp2Notch.flatMap(WriteMappings::getOutput));
            task.getLibraries().setFrom(vanilla.vanillaConfig, mainSourceSet.map(SourceSet::getCompileClasspath));
            task.getOutput().set(ext.getLocalCacheDirectory().file("dist/reobf/minecraft-notch.jar"));
        });
        this.stripClientMinecraftJar.configure(task -> {
            task.dependsOn(this.reobfMinecraftJar);
            task.getInputJar().set(this.reobfMinecraftJar.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.CLIENT);
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("dist/reobf/minecraft-client.jar"));
        });
        this.stripServerMinecraftJar.configure(task -> {
            task.dependsOn(this.reobfMinecraftJar);
            task.getInputJar().set(this.reobfMinecraftJar.flatMap(RenameJar::getOutput));
            task.getTargetSide().set(Side.SERVER);
            task.getOutputJar().set(ext.getLocalCacheDirectory().file("dist/reobf/minecraft-server.jar"));
        });
        this.genClientBinPatches.configure(task -> {
            task.dependsOn(vanilla.downloadClientJar, this.stripClientMinecraftJar);

            task.getOriginalJar().fileProvider(vanilla.downloadClientJar.map(Download::getDest));
            task.getModifiedJar().set(this.stripClientMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getBinpatches().set(ext.getLocalCacheDirectory().file("binpatches/client.zip"));
        });
        this.genServerBinPatches.configure(task -> {
            task.dependsOn(vanilla.downloadServerJar, this.stripServerMinecraftJar);

            task.getOriginalJar().fileProvider(vanilla.downloadServerJar.map(Download::getDest));
            task.getModifiedJar().set(this.stripServerMinecraftJar.flatMap(StripSideOnlyJar::getOutputJar));
            task.getBinpatches().set(ext.getLocalCacheDirectory().file("binpatches/server.zip"));
        });
        this.genRuntimeBinPatches.configure(task -> {
            task.dependsOn(this.genClientBinPatches, this.genServerBinPatches);
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
        this.deobfDataLzma.configure(task -> {
            task.dependsOn(mcp.writeObf2Srg);

            task.getInput().set(mcp.writeObf2Srg.flatMap(WriteMappings::getOutput));
            task.getOutput().set(ext.getLocalCacheDirectory().file("dist/deobf_data-" + MINECRAFT_VERSION + ".lzma"));
        });
        this.universalJar.configure(task -> {
            task.dependsOn(this.reobfJar, this.writeObf2SrgTsrg, this.genRuntimeBinPatches);
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

        this.userdevJar = Tasks.of(project, GROUP_NAME, "userdevJar", Jar.class);
        this.javadocJar = Tasks.of(project, GROUP_NAME, "javadocJar", Jar.class);

        var javadocTask = project.getTasks().named("javadoc", Javadoc.class);
        javadocTask.configure(task -> {
            task.setFailOnError(false);
            task.options(MinimalJavadocOptions::quiet);
        });

        this.userdevJar.configure(task -> {
            task.dependsOn(jarTask, this.genRuntimeBinPatches, mcp.writeSrg2Mcp, this.writeMcp2SrgDist);
            task.setDescription("Assembles the userdev jar for mod developers.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("userdev");

            task.from(project.zipTree(jarTask.flatMap(Jar::getArchiveFile)));
            task.from(this.genRuntimeBinPatches.flatMap(Zip::getArchiveFile),
                    spec -> spec.rename(name -> "binpatches.zip"));
            task.from(ext.getAccessTransformers(), spec -> spec.into("ats"));
            task.from(mcp.writeSrg2Mcp.flatMap(WriteMappings::getOutput),
                    spec -> spec.rename(name -> "srg2mcp.srg"));
            task.from(this.writeMcp2SrgDist.flatMap(WriteMappings::getOutput),
                    spec -> spec.rename(name -> "mcp2srg.tsrg"));
        });
        this.javadocJar.configure(task -> {
            task.dependsOn(javadocTask);
            task.setDescription("Packages Javadoc into a jar.");
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.getArchiveBaseName().set(ARTIFACT_ID);
            task.getArchiveVersion().set(version);
            task.getArchiveClassifier().set("javadoc");

            task.from(javadocTask.map(Javadoc::getDestinationDir));
        });
    }

    /** The specification version: the last release tag, i.e. the project version with any {@code +build...} suffix removed. */
    private static String specVersion(String version) {
        var idx = version.indexOf('+');
        return idx == -1 ? version : version.substring(0, idx);
    }

}
