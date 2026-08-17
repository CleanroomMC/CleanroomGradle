package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.ext.VanillaEnvironment;
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
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
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
        var actualSha1 = IO.sha1(file);
        if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
            throw new IllegalStateException("SHA-1 mismatch for %s: expected %s but got %s. "
                    .formatted(file, expectedSha1, actualSha1) + "Delete the file and rerun its download task.");
        }
    }

    // gradle-download-task overwrite=true makes the task never UP-TO-DATE
    private static void skipWhenSha1Matches(Download task, Provider<String> expectedSha1) {
        task.onlyIf("destination is missing or SHA-1 does not match", t -> {
            if (!expectedSha1.isPresent()) {
                return false;
            }
            var dest = ((Download) t).getDest();
            return dest == null || !IO.sha1Match(dest, expectedSha1.get());
        });
        task.doLast("verifySha1", t -> verifySha1(((Download) t).getDest(), expectedSha1.get()));
    }

    /** {@value #DEFAULT_VERSION}, or the version requested with {@code -Pmc=<version>}. */
    public final Provider<String> minecraftVersion;
    /** Metadata of {@link #minecraftVersion}: the extension's meta by default, launcher-manifest-resolved under {@code -Pmc}. */
    public final Provider<VersionMeta> versionMeta;
    public final Provider<Directory> versionCacheDirectory;
    public final NamedDomainObjectProvider<Configuration> vanillaConfig, vanillaNativesConfig;
    public final TaskProvider<Download> downloadAssetIndex, downloadClientJar, downloadServerJar, downloadClientMappings;
    public final TaskProvider<DownloadAssets> downloadAssets;
    public final TaskProvider<Copy> extractNatives;
    public final TaskProvider<RenameJar> remapClientToOfficial;
    public final TaskProvider<Decompile> decompileVersion;
    public final TaskProvider<RunMinecraft> runVanillaClient, runVanillaServer;

    public VanillaTasks(Project project, CleanroomExtension ext) {
        this(project, ext, primarySpec(project, ext));
    }

    public VanillaTasks(Project project, CleanroomExtension ext, VanillaEnvironment environment) {
        this(project, ext, namedSpec(project, ext, environment));
    }

    private VanillaTasks(Project project, CleanroomExtension ext, Spec spec) {
        this.minecraftVersion = spec.minecraftVersion();
        this.versionMeta = spec.versionMeta();
        this.versionCacheDirectory = spec.versionCacheDirectory();

        var vanillaJavaLauncher = spec.javaMajor().flatMap(major ->
                project.getExtensions().getByType(JavaToolchainService.class).launcherFor(toolchain -> {
                    toolchain.getLanguageVersion().set(JavaLanguageVersion.of(major));
                    toolchain.getVendor().set(JvmVendorSpec.ADOPTIUM);
                    toolchain.getImplementation().set(JvmImplementation.VENDOR_SPECIFIC);
                }));

        var clientMappings = this.versionMeta.map(meta -> meta.download("client_mappings"));
        var serverDownload = this.versionMeta.map(meta -> meta.download("server"));
        var assetIndex = this.versionMeta.map(VersionMeta::assetIndex);

        this.vanillaConfig = Objects.config(project, configurationName(spec, "vanilla", ""));
        this.vanillaNativesConfig = Objects.config(project, configurationName(spec, "vanillaNatives", "Natives"));
        this.vanillaConfig.configure(config -> config.withDependencies(dependencies ->
                addLibraries(project, dependencies, this.versionMeta.get())));
        this.vanillaNativesConfig.configure(config -> config.withDependencies(dependencies ->
                addNatives(project, dependencies, this.versionMeta.get())));

        var decompiler = Objects.toolConfig(project, "decompiler", Meta.DEFAULT_TOOLS.get("decompiler"));

        var downloadAssetIndexName = taskName(spec, "downloadAssetIndex", "download", "AssetIndex");
        var downloadClientJarName = taskName(spec, "downloadClientJar", "download", "ClientJar");
        var downloadServerJarName = taskName(spec, "downloadServerJar", "download", "ServerJar");
        var downloadClientMappingsName = taskName(spec, "downloadClientMappings", "download", "ClientMappings");
        var downloadAssetsName = taskName(spec, "downloadAssets", "download", "Assets");
        var extractNativesName = taskName(spec, "extractNatives", "extract", "Natives");
        var remapClientName = taskName(spec, "remapClientToOfficial", "remap", "ClientToOfficial");
        var decompileName = taskName(spec, "decompileVersion", "decompile", "");
        var runClientName = taskName(spec, "runVanillaClient", "run", "Client");
        var runServerName = taskName(spec, "runVanillaServer", "run", "Server");

        this.downloadAssetIndex = Tasks.register(project, downloadAssetIndexName, Download.class);
        this.downloadClientJar = Tasks.register(project, downloadClientJarName, Download.class);
        this.downloadServerJar = Tasks.register(project, downloadServerJarName, Download.class);
        this.downloadClientMappings = Tasks.register(project, downloadClientMappingsName, Download.class);
        this.downloadAssets = Tasks.register(project, downloadAssetsName, DownloadAssets.class);
        this.extractNatives = Tasks.unzip(project, extractNativesName, this.vanillaNativesConfig,
                this.versionCacheDirectory.map(dir -> dir.dir("natives/" + spec.cacheName())));
        this.remapClientToOfficial = project.getTasks().register(remapClientName, RenameJar.class,
                project.getExtensions().getByType(RenamerExtension.class));
        this.decompileVersion = Tasks.tool(project, ext.getLocalCacheDirectory(), decompileName, Decompile.class, decompiler);
        this.runVanillaClient = Tasks.register(project, runClientName, RunMinecraft.class);
        this.runVanillaServer = Tasks.register(project, runServerName, RunMinecraft.class);
        Tasks.group(GROUP_NAME, this.decompileVersion, this.runVanillaClient, this.runVanillaServer);

        configureTasks(project, ext, spec, vanillaJavaLauncher, clientMappings, serverDownload, assetIndex,
                decompileName);
    }

    private void configureTasks(Project project, CleanroomExtension ext, Spec spec,
                                Provider<JavaLauncher> vanillaJavaLauncher,
                                Provider<VersionMeta.Download> clientMappings,
                                Provider<VersionMeta.Download> serverDownload,
                                Provider<VersionMeta.AssetIndex> assetIndex,
                                String decompileName) {
        this.downloadAssetIndex.configure(task -> {
            task.onlyIf("VersionMeta offers an asset index", t -> assetIndex.isPresent());
            task.src(this.versionMeta.map(VersionMeta::assetIndexUrl));
            task.dest(ext.getCacheDirectory().file(this.versionMeta.map(meta -> "assets/indexes/" + meta.assetIndexId() + ".json")));
            skipWhenSha1Matches(task, this.versionMeta.map(VersionMeta::assetIndexSha1));
        });
        this.downloadClientJar.configure(task -> {
            task.src(this.versionMeta.map(VersionMeta::clientUrl));
            task.dest(this.versionCacheDirectory.map(dir -> dir.file("client.jar")));
            skipWhenSha1Matches(task, this.versionMeta.map(VersionMeta::clientSha1));
        });
        this.downloadServerJar.configure(task -> {
            task.onlyIf("VersionMeta offers a server download", t -> serverDownload.isPresent());
            task.src(serverDownload.map(VersionMeta.Download::url));
            task.dest(this.versionCacheDirectory.map(dir -> dir.file("server.jar")));
            skipWhenSha1Matches(task, serverDownload.map(VersionMeta.Download::sha1));
        });
        this.downloadClientMappings.configure(task -> {
            task.onlyIf("VersionMeta offers client_mappings", t -> clientMappings.isPresent());
            task.src(clientMappings.map(VersionMeta.Download::url));
            task.dest(this.versionCacheDirectory.map(dir -> dir.file("client_mappings.txt")));
            skipWhenSha1Matches(task, clientMappings.map(VersionMeta.Download::sha1));
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
            task.getOutput().set(this.versionCacheDirectory.map(dir -> dir.file("client-official.jar")));
        });
        this.decompileVersion.configure(task -> {
            task.setDescription("Decompiles the selected vanilla client jar for source browsing, under official names when Mojang publishes mappings.");

            task.getJavaLauncher().convention(Providers.javaLauncher(project, 25));
            task.getLogFile().convention(ext.getLocalCacheDirectory().file(decompileName + "/decompile.log"));
            task.getCompiledJar().fileProvider(this.versionMeta.flatMap(meta -> meta.download("client_mappings") != null
                    ? this.remapClientToOfficial.flatMap(RenameJar::getOutput).map(RegularFile::getAsFile)
                    : this.downloadClientJar.map(Download::getDest)));
            task.getLibraries().from(this.vanillaConfig);
            task.getDecompiledJar().fileProvider(this.versionCacheDirectory.zip(this.minecraftVersion, (dir, version) -> dir.file(version + "-sources.jar").getAsFile()));
        });
        this.runVanillaClient.configure(task -> {
            task.dependsOn(this.downloadAssets);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.VANILLA);
            task.getMinecraftVersion().set(this.minecraftVersion);
            task.getVersionMeta().set(this.versionMeta);
            task.getMainClass().set(this.versionMeta.map(VersionMeta::mainClass));
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
        if (!spec.primary()) {
            this.runVanillaClient.configure(task -> task.setWorkingDir(
                    project.getLayout().getProjectDirectory().dir("run/" + spec.cacheName() + "/vanilla/client")));
            this.runVanillaServer.configure(task -> task.setWorkingDir(
                    project.getLayout().getProjectDirectory().dir("run/" + spec.cacheName() + "/vanilla/server")));
        }
    }

    public static String taskSuffix(String environmentName) {
        return StringUtils.capitalize(environmentName);
    }

    private static Spec primarySpec(Project project, CleanroomExtension ext) {
        var providers = project.getProviders();
        var offline = project.getGradle().getStartParameter().isOffline();
        var mcProperty = providers.gradleProperty("mc");
        var version = mcProperty.orElse(DEFAULT_VERSION);
        var meta = launcherMeta(project, ext, mcProperty, offline).orElse(ext.getVersionMeta());
        var cache = mcProperty
                .flatMap(selected -> ext.getCacheDirectory().dir("versions/" + selected))
                .orElse(ext.getVersionCacheDirectory());
        var javaMajor = providers.gradleProperty("cleanroom.vanillaJava")
                .map(Integer::parseInt)
                .orElse(meta.map(VersionMeta::javaMajor));
        return new Spec(true, "", "vanilla", version, meta, cache, javaMajor);
    }

    private static Spec namedSpec(Project project, CleanroomExtension ext, VanillaEnvironment environment) {
        var offline = project.getGradle().getStartParameter().isOffline();
        var version = environment.getVersion();
        var meta = launcherMeta(project, ext, version, offline);
        var cache = version.flatMap(selected -> ext.getCacheDirectory().dir("versions/" + selected));
        var javaMajor = environment.getJavaVersion().orElse(meta.map(VersionMeta::javaMajor));
        return new Spec(false, taskSuffix(environment.getName()), environment.getName(), version, meta, cache, javaMajor);
    }

    private static Provider<VersionMeta> launcherMeta(Project project, CleanroomExtension ext,
                                                       Provider<String> version, boolean offline) {
        return version.flatMap(selected -> project.getProviders().of(LauncherVersionMetaValueSource.class, value -> {
            value.getParameters().getManifestUrl().set(Meta.VERSION_MANIFEST_V2_URL);
            value.getParameters().getMinecraftVersion().set(selected);
            value.getParameters().getCacheDirectory().set(ext.getCacheDirectory());
            value.getParameters().getOffline().set(offline);
        }));
    }

    private static String taskName(Spec spec, String primaryName, String prefix, String ending) {
        return spec.primary() ? primaryName : prefix + spec.taskSuffix() + ending;
    }

    private static String configurationName(Spec spec, String primaryName, String ending) {
        return spec.primary() ? primaryName : "vanilla" + spec.taskSuffix() + ending;
    }

    private record Spec(boolean primary, String taskSuffix, String cacheName,
                        Provider<String> minecraftVersion, Provider<VersionMeta> versionMeta,
                        Provider<Directory> versionCacheDirectory, Provider<Integer> javaMajor) { }

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
