package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.MinecraftExtension;
import com.cleanroommc.gradle.api.ext.VanillaEnvironment;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.source.LauncherVersionMetaValueSource;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.common.Decompile;
import com.cleanroommc.gradle.api.task.mc.DownloadAssets;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.LwjglNatives;
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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class VanillaTasks {

    public static final String LWJGL2_GROUP = "org.lwjgl.lwjgl";

    private static final String GROUP_NAME = "vanilla";

    public static void addDistributionLibraries(DependencyFactory factory, Collection<Dependency> dependencies, VersionMeta meta) {
        addLibraries(factory, dependencies, meta, library -> !isLwjgl2(library.name()));
    }

    static void addDistributionNatives(DependencyFactory factory, DependencySet dependencies, VersionMeta meta,
                                       Map<String, String> selectedVersions) {
        for (var library : meta.libraries()) {
            if (!library.hasNatives() || library.downloads() == null
                    || library.downloads().classifiers() == null || isLwjgl2(library.name())) {
                continue;
            }
            var coordinates = library.name().split(":");
            if (ordinaryArtifactWasReplaced(library, coordinates, selectedVersions)) {
                continue;
            }
            for (var classifier : library.downloads().classifiers().keySet()) {
                if (!classifier.startsWith(LwjglNatives.CLASSIFIER_PREFIX)) {
                    continue;
                }
                var dependency = factory.create("%s:%s:%s:%s".formatted(coordinates[0], coordinates[1], coordinates[2], classifier));
                dependency.setTransitive(false);
                dependencies.add(dependency);
            }
        }
    }

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

    private static boolean isLwjgl2(String name) {
        return name.startsWith(LWJGL2_GROUP + ":");
    }

    static Map<String, String> selectedVersions(Configuration configuration) {
        var versions = new LinkedHashMap<String, String>();
        for (var component : configuration.getIncoming().getResolutionResult().getAllComponents()) {
            if (component.getId() instanceof ModuleComponentIdentifier module) {
                versions.put(module.getGroup() + ":" + module.getModule(), module.getVersion());
            }
        }
        return versions;
    }

    private static boolean ordinaryArtifactWasReplaced(VersionMeta.Library library, String[] coordinates,
                                                        Map<String, String> selectedVersions) {
        if (library.artifact() == null) {
            return false;
        }
        var selectedVersion = selectedVersions.get(coordinates[0] + ":" + coordinates[1]);
        return selectedVersion != null && !selectedVersion.equals(coordinates[2]);
    }

    private static void addLibraries(DependencyFactory factory, Collection<Dependency> dependencies, VersionMeta meta,
                                     Predicate<VersionMeta.Library> accept) {
        var nativeModules = nativeModules(meta);
        for (var library : meta.libraries()) {
            if (library.artifact() == null || !accept.test(library)) {
                continue;
            }
            var dependency = factory.create(library.name());
            for (var module : nativeModules) {
                dependency.exclude(module);
            }
            dependencies.add(dependency);
        }
    }

    static void addNatives(DependencyFactory factory, DependencySet dependencies, VersionMeta meta,
                           Map<String, String> selectedVersions) {
        addNativesFor(factory, dependencies, meta, selectedVersions, Platform.CURRENT);
    }

    /**
     * The vanilla libraries extracted as natives on one platform, for a published per-platform variant.
     */
    public static void addNativesFor(DependencyFactory factory, Collection<Dependency> dependencies, VersionMeta meta,
                                     Map<String, String> selectedVersions, Platform platform) {
        for (var library : meta.libraries()) {
            if (!library.isValidForOS(platform) || !library.hasNativesForOS(platform)) {
                continue;
            }
            var coordinates = library.name().split(":");
            if (ordinaryArtifactWasReplaced(library, coordinates, selectedVersions)) {
                continue;
            }
            var classifier = library.classifierForOS(platform);
            if (classifier == null) {
                continue;
            }
            var matcher = Meta.NATIVES_PATTERN.matcher(classifier.path());
            if (!matcher.find()) {
                throw new IllegalStateException("Failed to match regex for natives path: " + classifier.path());
            }
            var notation = "%s:%s:%s:%s".formatted(matcher.group("group").replace('/', '.'),
                    matcher.group("name"), matcher.group("version"), matcher.group("classifier"));
            var dependency = factory.create(notation);
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

    /**
     * Always {@link Meta#ONE_TRUE_MINECRAFT_VERSION} for the unsuffixed vanilla tasks.
     */
    public final Provider<String> minecraftVersion;
    /**
     * Launcher-manifest metadata for {@link #minecraftVersion}.
     */
    public final Provider<VersionMeta> versionMeta;
    public final Provider<Directory> versionCacheDirectory;
    public final Provider<File> assetIndexFile, clientJar, serverJar;
    public final NamedDomainObjectProvider<Configuration> vanillaConfig, vanillaNativesConfig;
    public final TaskProvider<Download> downloadAssetIndex, downloadClientJar, downloadServerJar, downloadClientMappings;
    public final TaskProvider<DownloadAssets> downloadAssets;
    public final TaskProvider<Copy> extractNatives;
    public final TaskProvider<RenameJar> remapClientToOfficial;
    public final TaskProvider<Decompile> decompileVersion;
    public final TaskProvider<RunMinecraft> runVanillaClient, runVanillaServer;

    public VanillaTasks(Project project, CachesExtension caches, MinecraftExtension minecraft) {
        this(project, caches, minecraft, primarySpec(project, caches, minecraft));
    }

    public VanillaTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, VanillaEnvironment environment) {
        this(project, caches, minecraft, namedSpec(project, caches, environment));
    }

    private VanillaTasks(Project project, CachesExtension caches, MinecraftExtension minecraft, Spec spec) {
        this.minecraftVersion = spec.minecraftVersion();
        this.versionMeta = spec.versionMeta();
        this.versionCacheDirectory = spec.versionCacheDirectory();
        this.assetIndexFile = caches.getDirectory()
                .file(this.versionMeta.map(meta -> "assets/indexes/" + meta.assetIndexId() + ".json"))
                .map(RegularFile::getAsFile);
        this.clientJar = this.versionCacheDirectory.map(dir -> dir.file("client.jar").getAsFile());
        this.serverJar = this.versionCacheDirectory.map(dir -> dir.file("server.jar").getAsFile());

        var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        var vanillaJavaLauncher = spec.javaMajor().flatMap(major -> Providers.javaLauncher(toolchains, major));

        var clientMappings = this.versionMeta.map(meta -> meta.download("client_mappings"));
        var serverDownload = this.versionMeta.map(meta -> meta.download("server"));
        var assetIndex = this.versionMeta.map(VersionMeta::assetIndex);

        var factory = project.getDependencyFactory();
        this.vanillaConfig = Objects.config(project, configurationName(spec, "vanilla", ""),
                "Minecraft libraries for the " + spec.cacheName() + " environment, from its launcher metadata.");
        this.vanillaNativesConfig = Objects.config(project, configurationName(spec, "vanillaNatives", "Natives"),
                "Minecraft platform natives for the " + spec.cacheName() + " environment.");
        this.vanillaConfig.configure(config -> config.withDependencies(dependencies ->
                addLibraries(factory, dependencies, this.versionMeta.get(),
                        library -> library.isValidForOS(Platform.CURRENT))));
        var selectedVanillaVersions = project.provider(() -> selectedVersions(this.vanillaConfig.get()));
        this.vanillaNativesConfig.configure(config -> config.withDependencies(dependencies ->
                addNatives(factory, dependencies, this.versionMeta.get(), selectedVanillaVersions.get())));

        var decompiler = ToolConfigs.get(project, "decompiler");
        var offline = project.getGradle().getStartParameter().isOffline();

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
        this.remapClientToOfficial = Tasks.register(project, remapClientName, RenameJar.class, project.getExtensions().getByType(RenamerExtension.class));
        this.decompileVersion = Tasks.tool(project, caches.getLocalDirectory(), decompileName, Decompile.class, decompiler);
        this.runVanillaClient = Tasks.register(project, runClientName, RunMinecraft.class);
        this.runVanillaServer = Tasks.register(project, runServerName, RunMinecraft.class);
        Tasks.group(GROUP_NAME, this.decompileVersion, this.runVanillaClient, this.runVanillaServer);

        configureTasks(project, caches, spec, vanillaJavaLauncher, clientMappings, serverDownload, assetIndex,
                decompileName, offline);
    }

    private void configureTasks(Project project, CachesExtension caches, Spec spec,
                                Provider<JavaLauncher> vanillaJavaLauncher,
                                Provider<VersionMeta.Download> clientMappings,
                                Provider<VersionMeta.Download> serverDownload,
                                Provider<VersionMeta.AssetIndex> assetIndex,
                                String decompileName, boolean offline) {
        this.downloadAssetIndex.configure(task -> {
            task.onlyIf("VersionMeta offers an asset index", t -> assetIndex.isPresent());
            task.src(this.versionMeta.map(VersionMeta::assetIndexUrl));
            task.dest(this.assetIndexFile);
            skipWhenSha1Matches(task, this.versionMeta.map(VersionMeta::assetIndexSha1));
        });
        this.downloadClientJar.configure(task -> {
            task.src(this.versionMeta.map(VersionMeta::clientUrl));
            task.dest(this.clientJar);
            skipWhenSha1Matches(task, this.versionMeta.map(VersionMeta::clientSha1));
        });
        this.downloadServerJar.configure(task -> {
            task.onlyIf("VersionMeta offers a server download", t -> serverDownload.isPresent());
            task.src(serverDownload.map(VersionMeta.Download::url));
            task.dest(this.serverJar);
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
            task.getObjects().set(caches.getDirectory().dir("assets/objects"));
            task.getOffline().set(offline);
        });
        this.extractNatives.configure(task -> {
            task.exclude("META-INF/**"); // TODO: Consider exclude block in version meta?
        });
        this.remapClientToOfficial.configure(task -> {
            task.onlyIf("VersionMeta offers client_mappings", _ -> clientMappings.isPresent());
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

            task.getJavaLauncher().convention(Providers.javaLauncher(project));
            task.getLogFile().convention(caches.getLocalDirectory().file(decompileName + "/decompile.log"));
            task.getCompiledJar().fileProvider(this.versionMeta.flatMap(meta -> meta.download("client_mappings") != null
                    ? this.remapClientToOfficial.flatMap(RenameJar::getOutput).map(RegularFile::getAsFile)
                    : this.downloadClientJar.map(Download::getDest)));
            task.getLibraries().from(this.vanillaConfig);
            task.getDecompiledJar().fileProvider(this.versionCacheDirectory.zip(this.minecraftVersion, (dir, version) -> dir.file(version + "-sources.jar").getAsFile()));
        });
        this.runVanillaClient.configure(task -> {
            task.dependsOn(this.downloadAssets);
            MinecraftRuns.caches(task, caches, this.versionMeta, offline);

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
            MinecraftRuns.caches(task, caches, this.versionMeta, offline);

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

    private static Spec primarySpec(Project project, CachesExtension caches, MinecraftExtension minecraft) {
        var offline = project.getGradle().getStartParameter().isOffline();
        var version = project.provider(() -> Meta.ONE_TRUE_MINECRAFT_VERSION);
        var meta = launcherMeta(project, caches, version, offline).orElse(minecraft.getVersionMeta());
        return new Spec(true, "", "vanilla", version, meta, caches.getVersionDirectory(), meta.map(VersionMeta::javaMajor));
    }

    private static Spec namedSpec(Project project, CachesExtension caches, VanillaEnvironment environment) {
        var offline = project.getGradle().getStartParameter().isOffline();
        var version = environment.getVersion();
        var meta = launcherMeta(project, caches, version, offline);
        var cache = version.flatMap(selected -> caches.getDirectory().dir("versions/" + selected));
        var javaMajor = environment.getJavaVersion().orElse(meta.map(VersionMeta::javaMajor));
        return new Spec(false, taskSuffix(environment.getName()), environment.getName(), version, meta, cache, javaMajor);
    }

    private static Provider<VersionMeta> launcherMeta(Project project, CachesExtension caches, Provider<String> version, boolean offline) {
        var providers = project.getProviders();
        var cacheDirectory = caches.getDirectory();
        return version.flatMap(selected -> providers.of(LauncherVersionMetaValueSource.class, value -> {
            value.getParameters().getManifestUrl().set(Meta.VERSION_MANIFEST_V2_URL);
            value.getParameters().getMinecraftVersion().set(selected);
            value.getParameters().getCacheDirectory().set(cacheDirectory);
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

}
