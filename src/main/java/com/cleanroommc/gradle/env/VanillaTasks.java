package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.task.mc.DownloadAssets;
import com.cleanroommc.gradle.api.util.Platform;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

public final class VanillaTasks {

    private static final String GROUP_NAME = "Vanilla Tasks";

    public final NamedDomainObjectProvider<Configuration> vanillaConfig;
    public final NamedDomainObjectProvider<Configuration> vanillaNativesConfig;
    public final TaskProvider<Download> downloadAssetIndex;
    public final TaskProvider<Download> downloadClientJar;
    public final TaskProvider<Download> downloadServerJar;
    public final TaskProvider<DownloadAssets> downloadAssets;
    public final TaskProvider<Copy> extractNatives;
    public final TaskProvider<RunMinecraft> runVanillaClient;
    public final TaskProvider<RunMinecraft> runVanillaServer;

    public VanillaTasks(Project project, CleanroomExtension ext) {
        var repos = project.getRepositories();
        repos.maven(mar -> {
            mar.setName("Mojang");
            mar.setUrl(Meta.MOJANG_REPO);
        });
        repos.mavenCentral();

        this.vanillaConfig = Objects.config(project, "vanilla");
        this.vanillaNativesConfig = Objects.config(project, "vanillaNatives");

        this.downloadAssetIndex = Tasks.of(project, GROUP_NAME, "downloadAssetIndex", Download.class);
        this.downloadClientJar = Tasks.of(project, GROUP_NAME, "downloadClientJar", Download.class);
        this.downloadServerJar = Tasks.of(project, GROUP_NAME, "downloadServerJar", Download.class);
        this.downloadAssets = Tasks.of(project, GROUP_NAME, "downloadAssets", DownloadAssets.class);
        this.extractNatives = Tasks.unzip(project, GROUP_NAME, "extractNatives", this.vanillaNativesConfig, ext.getVersionCacheDirectory().dir("natives/vanilla"));
        this.runVanillaClient = Tasks.of(project, GROUP_NAME, "runVanillaClient", RunMinecraft.class);
        this.runVanillaServer = Tasks.of(project, GROUP_NAME, "runVanillaServer", RunMinecraft.class);

        this.downloadAssetIndex.configure(task -> {
            task.src(ext.getVersionMeta().map(VersionMeta::assetIndex).map(VersionMeta.AssetIndex::url));
            task.dest(ext.getCacheDirectory().file("assets/indexes/1.12.json"));
            task.useETag(true);
        });
        this.downloadClientJar.configure(task -> {
            task.src(ext.getVersionMeta().map(VersionMeta::clientUrl));
            task.dest(ext.getVersionCacheDirectory().file("client.jar"));
        });
        this.downloadServerJar.configure(task -> {
            task.src(ext.getVersionMeta().map(VersionMeta::serverUrl));
            task.dest(ext.getVersionCacheDirectory().file("server.jar"));
        });
        this.downloadAssets.configure(task -> {
            task.dependsOn(this.downloadAssetIndex);

            task.getAssetIndexFile().fileProvider(this.downloadAssetIndex.map(Download::getDest));
            task.getObjects().set(ext.getCacheDirectory().dir("assets/objects"));
        });
        this.extractNatives.configure(task -> {
            task.exclude("META-INF/**"); // TODO: Consider exclude block in version meta?
        });
        this.runVanillaClient.configure(task -> {
            task.dependsOn(this.downloadAssets, this.downloadClientJar);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.VANILLA);
            task.getNatives().fileProvider(this.extractNatives.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.classpath(this.downloadClientJar.map(Download::getDest), this.vanillaConfig);
        });
        this.runVanillaServer.configure(task -> {
            task.dependsOn(this.downloadServerJar);

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.VANILLA);
            task.getNatives().fileProvider(this.extractNatives.map(Copy::getDestinationDir));
            task.classpath(this.downloadServerJar.map(Download::getDest), this.vanillaConfig);
        });
    }

    public void afterEvaluate(Project project, CleanroomExtension ext) {
        for (var library : ext.getVersionMeta().get().libraries()) {
            if (library.isValidForOS(Platform.CURRENT)) {
                Objects.dependency(project, this.vanillaConfig, library.name());
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
                        Objects.dependency(project, this.vanillaNativesConfig, dependencyNotation).setTransitive(false);
                    }
                }
            }
        }
    }

}
