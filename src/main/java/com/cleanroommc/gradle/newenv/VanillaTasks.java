package com.cleanroommc.gradle.newenv;

import com.cleanroommc.gradle.newapi.util.Environment;
import com.cleanroommc.gradle.newapi.Meta;
import com.cleanroommc.gradle.newapi.ext.CleanroomExtension;
import com.cleanroommc.gradle.newapi.schema.AssetIndex;
import com.cleanroommc.gradle.newapi.schema.VersionMeta;
import com.cleanroommc.gradle.newapi.task.Tasks;
import com.cleanroommc.gradle.newapi.task.mc.RunMinecraft;
import com.cleanroommc.gradle.newapi.util.IO;
import com.cleanroommc.gradle.newapi.util.Objects;
import com.cleanroommc.gradle.newapi.task.mc.DownloadAssets;
import com.cleanroommc.gradle.newapi.util.Platform;
import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

public final class VanillaTasks {

    private static final String GROUP_NAME = "Vanilla Tasks";

    public static NamedDomainObjectProvider<Configuration> VANILLA_CONFIG, VANILLA_NATIVES_CONFIG;

    public static TaskProvider<Download> DOWNLOAD_ASSET_INDEX, DOWNLOAD_CLIENT_JAR, DOWNLOAD_SERVER_JAR;
    public static TaskProvider<DownloadAssets> DOWNLOAD_ASSETS;
    public static TaskProvider<Copy> EXTRACT_NATIVES;
    public static TaskProvider<RunMinecraft> RUN_VANILLA_CLIENT, RUN_VANILLA_SERVER;

    public static void init(Project project, CleanroomExtension ext) {
        var repos = project.getRepositories();
//        repos.maven(mar -> {
//            mar.setName("Cleanroom");
//            mar.setUrl(Meta.CLEANROOM_REPO);
//        });
        repos.maven(mar -> {
            mar.setName("Mojang");
            mar.setUrl(Meta.MOJANG_REPO);
        });
        repos.mavenCentral();

        VANILLA_CONFIG = Objects.config(project, "vanilla");
        VANILLA_NATIVES_CONFIG = Objects.config(project, "vanillaNatives");

        DOWNLOAD_ASSET_INDEX = Tasks.of(project, GROUP_NAME, "downloadAssetIndex", Download.class);
        DOWNLOAD_CLIENT_JAR = Tasks.of(project, GROUP_NAME, "downloadClientJar", Download.class);
        DOWNLOAD_SERVER_JAR = Tasks.of(project, GROUP_NAME, "downloadServerJar", Download.class);
        DOWNLOAD_ASSETS = Tasks.of(project, GROUP_NAME, "downloadAssets", DownloadAssets.class);
        EXTRACT_NATIVES = Tasks.unzip(project, GROUP_NAME, "extractNatives", VANILLA_NATIVES_CONFIG, ext.getVersionCacheDirectory().dir("natives/vanilla"));
        RUN_VANILLA_CLIENT = Tasks.of(project, GROUP_NAME, "runVanillaClient", RunMinecraft.class);
        RUN_VANILLA_SERVER = Tasks.of(project, GROUP_NAME, "runVanillaServer", RunMinecraft.class);

        DOWNLOAD_ASSET_INDEX.configure(task -> {
            task.src(ext.getVersionMeta().map(VersionMeta::assetIndex).map(VersionMeta.AssetIndex::url));
            task.dest(ext.getCacheDirectory().file("assets/indexes/1.12.json"));
            task.useETag(true);
        });
        DOWNLOAD_CLIENT_JAR.configure(task -> {
            task.src(ext.getVersionMeta().map(VersionMeta::clientUrl));
            task.dest(ext.getVersionCacheDirectory().file("client.jar"));
        });
        DOWNLOAD_SERVER_JAR.configure(task -> {
            task.src(ext.getVersionMeta().map(VersionMeta::serverUrl));
            task.dest(ext.getVersionCacheDirectory().file("server.jar"));
        });
        DOWNLOAD_ASSETS.configure(task -> {
            task.dependsOn(DOWNLOAD_ASSET_INDEX);

            task.getAssetIndex().set(DOWNLOAD_ASSET_INDEX.map(Download::getDest).map(file -> IO.readJson(file, AssetIndex.class)));
            task.getObjects().set(ext.getCacheDirectory().dir("assets/objects"));
        });
        EXTRACT_NATIVES.configure(task -> {
            task.exclude("META-INF/**"); // TODO: Consider exclude block in version meta?
        });
        RUN_VANILLA_CLIENT.configure(task -> {
            task.dependsOn(DOWNLOAD_ASSETS, DOWNLOAD_CLIENT_JAR);

            task.getSide().set(Side.CLIENT);
            task.getEnv().set(Environment.VANILLA);
            task.getNatives().fileProvider(EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.getAssetIndexVersion().set(ext.getVersionMeta().map(VersionMeta::assetIndexId));
            task.getVanillaAssetsLocation().set(ext.getCacheDirectory().file("assets"));
            task.classpath(DOWNLOAD_CLIENT_JAR.map(Download::getDest), VANILLA_CONFIG);
        });
        RUN_VANILLA_SERVER.configure(task -> {
            task.dependsOn(DOWNLOAD_SERVER_JAR);

            task.getSide().set(Side.SERVER);
            task.getEnv().set(Environment.VANILLA);
            task.getNatives().fileProvider(EXTRACT_NATIVES.map(Copy::getDestinationDir));
            task.classpath(DOWNLOAD_SERVER_JAR.map(Download::getDest), VANILLA_CONFIG);
        });
    }

    public static void afterEvaluate(Project project, CleanroomExtension ext) {
        for (var library : ext.getVersionMeta().get().libraries()) {
            if (library.isValidForOS(Platform.CURRENT)) {
                Objects.dependency(project, VANILLA_CONFIG, library.name());
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
                        Objects.dependency(project, VANILLA_NATIVES_CONFIG, dependencyNotation).setTransitive(false);
                    }
                }
            }
        }
    }

    private VanillaTasks() { }

}
