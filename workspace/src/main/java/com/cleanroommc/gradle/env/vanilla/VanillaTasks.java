package com.cleanroommc.gradle.env.vanilla;

import com.cleanroommc.gradle.api.Environment;
import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.os.Platform;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.api.types.Types;
import com.cleanroommc.gradle.api.types.json.schema.AssetIndex;
import com.cleanroommc.gradle.api.types.json.schema.VersionManifest;
import com.cleanroommc.gradle.api.types.json.schema.VersionMeta;
import com.cleanroommc.gradle.env.common.task.DownloadAssets;
import com.cleanroommc.gradle.env.common.task.RunMinecraft;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class VanillaTasks {

    public static final String DOWNLOAD_VERSION_MANIFEST_PROPERTY = "com.cleanroommc.download_version_manifest";

    public static final String DOWNLOAD_VERSION_META = "downloadVersionMeta";
    public static final String DOWNLOAD_CLIENT_JAR = "downloadClientJar";
    public static final String DOWNLOAD_SERVER_JAR = "downloadServerJar";
    public static final String DOWNLOAD_ASSET_INDEX = "downloadAssetIndex";
    public static final String DOWNLOAD_ASSETS = "downloadAssets";
    public static final String EXTRACT_NATIVES = "extractNatives";
    public static final String RUN_VANILLA_CLIENT = "runVanillaClient";
    public static final String RUN_VANILLA_SERVER = "runVanillaServer";

    public static void downloadVersionManifest(Project project) {
        var manifestLocation = Locations.build(project, "version_manifest_v2.json");
        if (!IO.exists(manifestLocation)) {
            try {
                var result = IO.download(project, Meta.VERSION_MANIFEST_V2_URL, manifestLocation, dl -> {
                    dl.overwrite(false);
                    dl.onlyIfModified(true);
                    dl.onlyIfNewer(true);
                    dl.useETag(true);
                });
                result.get();
            } catch (IOException | ExecutionException | InterruptedException e) {
                throw new RuntimeException("Unable to download version manifest!", e);
            }
        }
    }

    private final Project project;
    private final String version;
    private final TaskGroup group;
    private final File cache;

    private NamedDomainObjectProvider<Configuration> vanillaConfig, vanillaNativesConfig;

    private TaskProvider<Download> downloadClientJar, downloadServerJar;
    private TaskProvider<DownloadAssets> downloadAssets;
    private TaskProvider<Copy> extractNatives;
    private TaskProvider<RunMinecraft> runVanillaClient, runVanillaServer;

    @Inject
    public VanillaTasks(Project project, String minecraftVersion) {
        this.project = project;
        this.version = minecraftVersion;
        this.group = TaskGroup.of("vanilla " + minecraftVersion);
        this.cache = Locations.build(project, "versions", minecraftVersion, "vanilla");

        this.initRepos();
        this.initConfigs();
        this.initTasks();
    }

    public String minecraftVersion() {
        return version;
    }

    public Provider<String> assetIndexId() {
        return Providers.of(() -> this.versionMeta().get().assetIndexId());
    }

    public Supplier<VersionMeta> versionMeta() {
        return Types.memoizedSupplier(() -> {
            try {
                var file = this.location("version.json");
                if (!file.exists()) {
                    var manifestLocation = Locations.build(project, "version_manifest_v2.json");
                    var result = IO.download(project,
                            Providers.of(() -> Types.readJson(manifestLocation, VersionManifest.class))
                                    .map(manifest -> manifest.version(this.version).url),
                            file,
                            dl -> {
                                dl.overwrite(false);
                                dl.onlyIfModified(true);
                                dl.onlyIfNewer(true);
                            });
                    result.get();
                }
                return Types.readJson(file, VersionMeta.class);
            } catch (IOException | ExecutionException | InterruptedException e) {
                throw new RuntimeException("Unable to download version meta!", e);
            }
        });
    }

    public Supplier<AssetIndex> assetIndex() {
        return Types.memoizedSupplier(() -> {
            try {
                var file = Locations.build(project, "assets", "indexes", assetIndexId().get() + ".json");
                if (!file.exists()) {
                    var result = IO.download(project, Providers.of(() -> versionMeta().get().assetIndexUrl()),
                            Providers.of(() -> Locations.build(project, "assets", "indexes", versionMeta().get().assetIndexId() + ".json")),
                            dl -> {
                                dl.overwrite(false);
                                dl.onlyIfModified(true);
                                dl.onlyIfNewer(true);
                            });
                    result.join();
                }
                return Types.readJson(file, AssetIndex.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Provider<File> clientJar() {
        return downloadClientJar.map(Download::getDest);
    }

    public Provider<File> serverJar() {
        return downloadServerJar.map(Download::getDest);
    }

    public NamedDomainObjectProvider<Configuration> vanillaConfig() {
        return vanillaConfig;
    }

    public NamedDomainObjectProvider<Configuration> vanillaNativesConfig() {
        return vanillaNativesConfig;
    }

    public TaskProvider<Download> downloadClientJar() {
        return downloadClientJar;
    }

    public TaskProvider<Download> downloadServerJar() {
        return downloadServerJar;
    }

    public TaskProvider<DownloadAssets> downloadAssets() {
        return downloadAssets;
    }

    public TaskProvider<Copy> extractNatives() {
        return extractNatives;
    }

    public TaskProvider<RunMinecraft> runVanillaClient() {
        return runVanillaClient;
    }

    public TaskProvider<RunMinecraft> runVanillaServer() {
        return runVanillaServer;
    }

    private void initRepos() {
        var repos = this.project.getRepositories();
        repos.mavenCentral();
        repos.maven(mar -> {
            mar.setName("Mojang");
            mar.setUrl(Meta.MOJANG_REPO);
        });
    }

    private void initConfigs() {
        this.vanillaConfig = Configurations.of(this.project, "vanilla_" + this.version.replace('.', '_'), true);
        this.vanillaNativesConfig = Configurations.of(this.project, "vanillaNatives_" + this.version.replace('.', '_'), false);

        this.project.afterEvaluate(project -> {
            for (var library : versionMeta().get().libraries()) {
                if (library.isValidForOS(Platform.CURRENT)) {
                    Dependencies.add(project, vanillaConfig, library.name());
                    if (library.hasNativesForOS(Platform.CURRENT)) {
                        var osClassifier = library.classifierForOS(Platform.CURRENT);
                        if (osClassifier != null) {
                            var path = osClassifier.path();
                            var matcher = Meta.NATIVES_PATTERN.matcher(path);
                            if (!matcher.find()) {
                                throw new IllegalStateException("Failed to match regex for natives path: " + path);
                            }
                            var group = matcher.group("group").replace('/', '.');
                            var name = matcher.group("name");
                            var version = matcher.group("version");
                            var classifier = matcher.group("classifier");
                            var dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);
                            Dependencies.add(project, vanillaNativesConfig, dependencyNotation);
                        }
                    }
                }
            }

            /*
            if (upgradeLog4j2.get()) {
                Configurations.all(project).forEach(config -> config.resolutionStrategy(rs -> rs.eachDependency(drd -> {
                    var requested = drd.getRequested();
                    if ("org.apache.logging.log4j".equals(requested.getGroup()) && Versioning.lowerThan(requested.getVersion(), "2.17.1")) {
                        drd.because("Upgrade Log4J2 property is enabled, hence all versions of Log4J2 below 2.17.1 will be updated to latest.");
                        drd.useVersion("2.22.1");
                    }
                })));
            }
             */
        });
    }

    private void initTasks() {
        var project = this.project;
        var group = this.group;
        var version = this.version;

        this.downloadClientJar = group.add(Tasks.withDownload(project, this.taskName(DOWNLOAD_CLIENT_JAR), dl -> {
            dl.onlyIf($ -> !location("client.jar").exists());
            dl.overwrite(false);
            dl.onlyIfModified(true);
            dl.src(Providers.of(() -> versionMeta().get().clientUrl()));
            dl.dest(location("client.jar"));
        }));

        this.downloadServerJar = group.add(Tasks.withDownload(project, this.taskName(DOWNLOAD_SERVER_JAR), dl -> {
            dl.onlyIf($ -> !location("server.jar").exists());
            dl.overwrite(false);
            dl.onlyIfModified(true);
            dl.src(Providers.of(() -> versionMeta().get().serverUrl()));
            dl.dest(location("server.jar"));
        }));

        this.downloadAssets = group.add(Tasks.with(project, this.taskName(DOWNLOAD_ASSETS), DownloadAssets.class, dl -> {
            dl.getAssetIndex().set(Providers.of(() -> assetIndex().get()));
            dl.getObjects().set(Locations.build(project, "assets", "objects"));
        }));

        this.extractNatives = group.add(Tasks.unzip(project, this.taskName(EXTRACT_NATIVES), vanillaNativesConfig, location("natives"), t -> t.exclude("META-INF/**")));

        this.runVanillaClient = group.add(Tasks.with(project, this.taskName(RUN_VANILLA_CLIENT), RunMinecraft.class, t -> {
            t.dependsOn(downloadAssets);
            t.getMinecraftVersion().set(version);
            t.getSide().set(Side.CLIENT);
            t.getNatives().fileProvider(extractNatives.map(Copy::getDestinationDir));
            t.getAssetIndexVersion().set(assetIndexId());
            t.getVanillaAssetsLocation().set(Locations.build(project, "assets"));
            t.setWorkingDir(Locations.run(project, version, Environment.VANILLA, Side.CLIENT));
            t.classpath(clientJar());
            t.classpath(vanillaConfig);
            t.getMainClass().set("net.minecraft.client.main.Main");
        }));

        this.runVanillaServer = group.add(Tasks.with(project, taskName(RUN_VANILLA_SERVER), RunMinecraft.class, t -> {
            t.getMinecraftVersion().set(version);
            t.getSide().set(Side.SERVER);
            t.getNatives().fileProvider(extractNatives.map(Copy::getDestinationDir));
            t.setWorkingDir(Locations.run(project, version, Environment.VANILLA, Side.SERVER));
            t.classpath(serverJar());
            t.classpath(vanillaConfig);
            t.getMainClass().set("net.minecraft.server.MinecraftServer");
        }));
    }

    private String taskName(String taskName) {
        return this.version.replace('.', '_') + "_" + taskName;
    }

    private File location(String... paths) {
        return Locations.file(this.cache, paths);
    }

}
