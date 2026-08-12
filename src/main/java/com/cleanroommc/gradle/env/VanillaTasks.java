package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.source.LauncherVersionMetaValueSource;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.DownloadAssets;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.Platform;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class VanillaTasks {

    private static final String GROUP_NAME = "vanilla";
    private static final String DEFAULT_VERSION = "1.12.2";

    private static void verifySha1(File file, String expectedSha1) {
        if (!IO.sha1Match(file, expectedSha1)) {
            throw new IllegalStateException("SHA-1 mismatch for %s: expected %s but got %s.".formatted(file, expectedSha1, IO.sha1(file)));
        }
    }

    /** {@value #DEFAULT_VERSION}, or the version requested with {@code -Pmc=<version>}. */
    public final Provider<String> minecraftVersion;
    /** Metadata of {@link #minecraftVersion}: the extension's meta by default, launcher-manifest-resolved under {@code -Pmc}. */
    public final Provider<VersionMeta> versionMeta;
    public final NamedDomainObjectProvider<Configuration> vanillaConfig, vanillaNativesConfig;
    public final TaskProvider<Download> downloadAssetIndex, downloadClientJar, downloadServerJar, downloadClientMappings;
    public final TaskProvider<DownloadAssets> downloadAssets;
    public final TaskProvider<Copy> extractNatives;
    public final TaskProvider<RenameJar> remapClientToOfficial;
    public final TaskProvider<Decompile> decompileVersion;
    public final TaskProvider<RunMinecraft> runVanillaClient, runVanillaServer;

    public VanillaTasks(Project project, CleanroomExtension ext) {
        var providers = project.getProviders();
        var offline = project.getGradle().getStartParameter().isOffline();
        var mcProperty = providers.gradleProperty("mc");
        this.minecraftVersion = mcProperty.orElse(DEFAULT_VERSION);
        this.versionMeta = mcProperty
                .flatMap(version -> providers.of(LauncherVersionMetaValueSource.class, spec -> {
                    spec.getParameters().getManifestUrl().set(Meta.VERSION_MANIFEST_V2_URL);
                    spec.getParameters().getMinecraftVersion().set(version);
                    spec.getParameters().getCacheDirectory().set(ext.getCacheDirectory());
                    spec.getParameters().getOffline().set(offline);
                }))
                .orElse(ext.getVersionMeta());
        var versionCacheDirectory = mcProperty
                .flatMap(version -> ext.getCacheDirectory().dir("versions/" + version))
                .orElse(ext.getVersionCacheDirectory());


        var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        var vanillaJavaLauncher = providers.gradleProperty("cleanroom.vanillaJava")
                .map(Integer::parseInt)
                .orElse(this.versionMeta.map(VersionMeta::javaMajor))
                .flatMap(major -> toolchains.launcherFor(spec -> {
                    spec.getLanguageVersion().set(JavaLanguageVersion.of(major));
                    spec.getVendor().set(JvmVendorSpec.ADOPTIUM);
                    spec.getImplementation().set(JvmImplementation.VENDOR_SPECIFIC);
                }));

        var clientMappings = this.versionMeta.map(meta -> meta.download("client_mappings"));
        var serverDownload = this.versionMeta.map(meta -> meta.download("server"));

        this.vanillaConfig = Objects.config(project, "vanilla");
        this.vanillaNativesConfig = Objects.config(project, "vanillaNatives");
        this.vanillaConfig.configure(config -> config.withDependencies(dependencies ->
                addLibraries(project, dependencies, this.versionMeta.get())));
        this.vanillaNativesConfig.configure(config -> config.withDependencies(dependencies ->
                addNatives(project, dependencies, this.versionMeta.get())));

        var decompiler = Objects.toolConfig(project, "decompiler", "com.cleanroommc:cleanflower:1.0.0");

        this.downloadAssetIndex = Tasks.register(project, "downloadAssetIndex", Download.class);
        this.downloadClientJar = Tasks.register(project, "downloadClientJar", Download.class);
        this.downloadServerJar = Tasks.register(project, "downloadServerJar", Download.class);
        this.downloadClientMappings = Tasks.register(project, "downloadClientMappings", Download.class);
        this.downloadAssets = Tasks.register(project, "downloadAssets", DownloadAssets.class);
        this.extractNatives = Tasks.unzip(project, "extractNatives", this.vanillaNativesConfig, versionCacheDirectory.map(dir -> dir.dir("natives/vanilla")));
        this.remapClientToOfficial = project.getTasks().register("remapClientToOfficial", RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.decompileVersion = Tasks.tool(project, ext.getLocalCacheDirectory(), "decompileVersion", Decompile.class, decompiler);
        this.runVanillaClient = Tasks.register(project, "runVanillaClient", RunMinecraft.class);
        this.runVanillaServer = Tasks.register(project, "runVanillaServer", RunMinecraft.class);
        Tasks.group(GROUP_NAME, this.decompileVersion, this.runVanillaClient, this.runVanillaServer);

        this.downloadAssetIndex.configure(task -> {
            task.src(this.versionMeta.map(VersionMeta::assetIndexUrl));
            task.dest(ext.getCacheDirectory().file(this.versionMeta.map(meta -> "assets/indexes/" + meta.assetIndexId() + ".json")));
            task.useETag(true);
        });
        this.downloadClientJar.configure(task -> {
            task.src(this.versionMeta.map(VersionMeta::clientUrl));
            task.dest(versionCacheDirectory.map(dir -> dir.file("client.jar")));

            var expectedSha1 = this.versionMeta.map(VersionMeta::clientSha1);
            task.doLast("verifySha1", t -> verifySha1(((Download) t).getDest(), expectedSha1.get()));
        });
        this.downloadServerJar.configure(task -> {
            task.onlyIf("VersionMeta offers a server download", t -> serverDownload.isPresent());
            task.src(serverDownload.map(VersionMeta.Download::url));
            task.dest(versionCacheDirectory.map(dir -> dir.file("server.jar")));

            var expectedSha1 = serverDownload.map(VersionMeta.Download::sha1);
            task.doLast("verifySha1", t -> verifySha1(((Download) t).getDest(), expectedSha1.get()));
        });
        this.downloadClientMappings.configure(task -> {
            task.onlyIf("VersionMeta offers client_mappings", t -> clientMappings.isPresent());
            task.src(clientMappings.map(VersionMeta.Download::url));
            task.dest(versionCacheDirectory.map(dir -> dir.file("client_mappings.txt")));

            var expectedSha1 = clientMappings.map(VersionMeta.Download::sha1);
            task.doLast("verifySha1", t -> verifySha1(((Download) t).getDest(), expectedSha1.get()));
        });
        this.downloadAssets.configure(task -> {
            task.getAssetIndexFile().fileProvider(this.downloadAssetIndex.map(Download::getDest));
            task.getObjects().set(ext.getCacheDirectory().dir("assets/objects"));
        });
        this.extractNatives.configure(task -> {
            task.exclude("META-INF/**"); // TODO: Consider exclude block in version meta?
        });
        this.remapClientToOfficial.configure(task -> {
            task.onlyIf("VersionMeta offers client_mappings", t -> clientMappings.isPresent());
            task.setDescription("Remaps the client jar from obfuscated to Mojang's official names.");

            task.getInput().fileProvider(this.downloadClientJar.map(Download::getDest));
            task.getMap().from(this.downloadClientMappings.map(Download::getDest));
            // The ProGuard log maps official -> obfuscated, the remap goes the other way
            task.getReverse().set(true);
            task.getLibraries().from(this.vanillaConfig);
            task.getOutput().set(versionCacheDirectory.map(dir -> dir.file("client-official.jar")));
        });
        this.decompileVersion.configure(task -> {
            task.setDescription("Decompiles the (-Pmc=<version>) client jar for source browsing, under official names when Mojang publishes mappings.");

            task.getJavaLauncher().convention(Providers.javaLauncher(project, 25));
            task.getLogFile().convention(ext.getLocalCacheDirectory().file("decompileVersion/decompile.log"));
            task.getCompiledJar().fileProvider(this.versionMeta.flatMap(meta -> meta.download("client_mappings") != null
                    ? this.remapClientToOfficial.flatMap(RenameJar::getOutput).map(RegularFile::getAsFile)
                    : this.downloadClientJar.map(Download::getDest)));
            task.getLibraries().from(this.vanillaConfig);
            task.getDecompiledJar().fileProvider(versionCacheDirectory.zip(this.minecraftVersion, (dir, version) -> dir.file(version + "-sources.jar").getAsFile()));
        });
        this.runVanillaClient.configure(task -> {
            task.dependsOn(this.downloadAssets);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.VANILLA);
            task.getMinecraftVersion().set(this.minecraftVersion);
            task.getVersionMeta().set(this.versionMeta);
            task.getJavaLauncher().convention(vanillaJavaLauncher);
            task.getNatives().fileProvider(this.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(this.versionMeta.map(VersionMeta::assetIndexId));
            task.classpath(this.downloadClientJar.map(Download::getDest), this.vanillaConfig);
        });
        this.runVanillaServer.configure(task -> {
            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.VANILLA);
            task.getMinecraftVersion().set(this.minecraftVersion);
            task.getVersionMeta().set(this.versionMeta);
            task.getJavaLauncher().convention(vanillaJavaLauncher);
            task.getNatives().fileProvider(this.extractNatives.map(Copy::getDestinationDir));
            task.classpath(this.downloadServerJar.map(Download::getDest), this.vanillaConfig);
        });
    }

    private static void addLibraries(Project project, DependencySet dependencies, VersionMeta meta) {
        var nativeModules = nativeModules(meta);
        for (var library : meta.libraries()) {
            if (!library.isValidForOS(Platform.CURRENT) || library.artifact() == null) {
                continue;
            }
            var dependency = (ModuleDependency) project.getDependencies().create(library.name());
            for (var module : nativeModules) {
                dependency.exclude(module);
            }
            dependencies.add(dependency);
        }
    }

    private static void addNatives(Project project, DependencySet dependencies, VersionMeta meta) {
        for (var library : meta.libraries()) {
            if (!library.isValidForOS(Platform.CURRENT) || !library.hasNativesForOS(Platform.CURRENT)) {
                continue;
            }
            var classifier = library.classifierForOS(Platform.CURRENT);
            if (classifier == null) {
                continue;
            }
            var matcher = Meta.NATIVES_PATTERN.matcher(classifier.path());
            if (!matcher.find()) {
                throw new IllegalStateException("Failed to match regex for natives path: " + classifier.path());
            }
            var notation = "%s:%s:%s:%s".formatted(matcher.group("group").replace('/', '.'),
                    matcher.group("name"), matcher.group("version"), matcher.group("classifier"));
            var dependency = (ModuleDependency) project.getDependencies().create(notation);
            dependency.setTransitive(false);
            dependencies.add(dependency);
        }
    }

    private static List<Map<String, String>> nativeModules(VersionMeta meta) {
        return meta.libraries().stream()
                .filter(VersionMeta.Library::hasNatives)
                .map(library -> library.name().split(":"))
                .map(coordinates -> Map.of("group", coordinates[0], "module", coordinates[1]))
                .distinct()
                .toList();
    }

}
