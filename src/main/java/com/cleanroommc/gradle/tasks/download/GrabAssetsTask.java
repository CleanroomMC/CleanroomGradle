package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion;
import com.cleanroommc.gradle.json.MinecraftVersion.AssetIndex;
import com.cleanroommc.gradle.util.CacheUtils;
import com.cleanroommc.gradle.util.CacheUtils.HashAlgorithm;
import com.cleanroommc.gradle.util.Utils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import static com.cleanroommc.gradle.Constants.*;

public abstract class GrabAssetsTask extends DefaultTask {

    public static TaskProvider<GrabAssetsTask> setupDownloadAssetsTask(Project project) {
        return Utils.prepareTask(project, GRAB_ASSETS, GrabAssetsTask.class);
    }

    private static void removeDuplicateRemotePaths(List<String> keys, AssetIndex index) {
        Set<String> seen = new HashSet<>(keys.size());
        keys.removeIf(key -> !seen.add(index.objects.get(key).getPath()));
    }

    public GrabAssetsTask() {
        getAssetRepository().convention(MINECRAFT_ASSETS_LINK);
        getWorkerThreadCount().convention(8);
    }

    @TaskAction
    public void task$getOrDownload() throws IOException, InterruptedException {
        AssetIndex index = Utils.loadJson(getIndex(), AssetIndex.class);
        List<String> keys = new ArrayList<>(index.objects.keySet());
        Collections.sort(keys);
        removeDuplicateRemotePaths(keys, index);
        ExecutorService executorService = Executors.newFixedThreadPool(getWorkerThreadCount().get());
        CleanroomLogger.log("Using {} worker threads to grab and download assets.", getWorkerThreadCount().get());
        CopyOnWriteArrayList<String> failedDownloads = new CopyOnWriteArrayList<>();
        String assetRepo = getAssetRepository().get();
        for (String key : keys) {
            Asset asset = index.objects.get(key);
            File target = new File(ASSET_OBJECTS_FOLDER, asset.getPath());
            if (CacheUtils.isFileCorrupt(target, asset.hash, HashAlgorithm.SHA1)) {
                URL url = new URL(assetRepo + asset.getPath());
                Runnable copyURLtoFile = () -> {
                    try {
                        File localFile = FileUtils.getFile(MINECRAFT_ASSET_OBJECTS_FOLDER + File.separator + asset.getPath());
                        if (localFile.exists()) {
                            CleanroomLogger.log2("Copying local object: {} | Asset: {}", asset.getPath(), key);
                            FileUtils.copyFile(localFile, target);
                        } else {
                            CleanroomLogger.log2("Downloading: {} | Asset: {}", asset.getPath(), key);
                            FileUtils.copyURLToFile(url, target, 10000, 5000);
                        }
                        if (CacheUtils.isFileCorrupt(target, asset.hash, HashAlgorithm.SHA1)) {
                            failedDownloads.add(key);
                            CacheUtils.deleteFile(target);
                            CleanroomLogger.error("{} hash failed.", key);
                        }
                    } catch (IOException e) {
                        failedDownloads.add(key);
                        CleanroomLogger.error("{} failed.", key);
                        e.printStackTrace();
                    }
                };
                executorService.execute(copyURLtoFile);
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.HOURS);
        if (!failedDownloads.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            for (String key : failedDownloads) {
                errorMessage.append("Failed to get asset: ").append(key).append("\n");
            }
            errorMessage.append("Some assets failed to download or validate, try running the task again.");
            throw new RuntimeException(errorMessage.toString());
        }
    }

    @InputFile
    public abstract RegularFileProperty getMeta();

    @Internal
    public abstract Property<String> getAssetRepository();

    @Internal
    public abstract Property<Integer> getWorkerThreadCount();

    private File getIndex() throws IOException {
        MinecraftVersion.AssetIndex assetIndex = MinecraftExtension.get(PROJECT).getVersionInfo().assetIndex;
        File target = ASSET_INDEX_FILE.apply(assetIndex.id);
        if (CacheUtils.isFileCorrupt(target, assetIndex.sha1, HashAlgorithm.SHA1)) {
            CleanroomLogger.log2("Downloading: {}", assetIndex.url);
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            FileUtils.copyURLToFile(assetIndex.url, target);
        }
        return target;
    }

    private static class AssetIndex {

        Map<String, Asset> objects;

    }

    private static class Asset {

        String hash;

        public String getPath() {
            return hash.substring(0, 2) + '/' + hash;
        }

    }

}
