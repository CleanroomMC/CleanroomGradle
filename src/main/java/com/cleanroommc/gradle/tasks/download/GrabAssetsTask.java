package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.util.CacheUtils;
import com.cleanroommc.gradle.util.CacheUtils.HashAlgorithm;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.VersionJson;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.Version;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import static com.cleanroommc.gradle.Constants.*;

public abstract class GrabAssetsTask extends DefaultTask {

    public static void setupDownloadAssetsTask(Project project) {
        GrabAssetsTask grabAssetsTask = Utils.createTask(project, DL_MINECRAFT_ASSETS_TASK, GrabAssetsTask.class);
        grabAssetsTask.dependsOn(Utils.getTask(project, DL_MINECRAFT_ASSET_INDEX_TASK));
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
    public void getOrDownload() throws IOException, InterruptedException {
        AssetIndex index = Utils.loadJson(getIndex(), AssetIndex.class);
        List<String> keys = new ArrayList<>(index.objects.keySet());
        Collections.sort(keys);
        removeDuplicateRemotePaths(keys, index);
        ExecutorService executorService = Executors.newFixedThreadPool(getWorkerThreadCount().get());
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
        executorService.awaitTermination(8, TimeUnit.HOURS);
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
        // VersionJson json = Utils.loadJson(getMeta().get().getAsFile(), VersionJson.class);
        Version json = Version.getCurrentVersion();
        File target = ASSET_INDEX_FILE.apply(json.assetIndex.id);
        if (CacheUtils.isFileCorrupt(target, json.assetIndex.sha1, HashAlgorithm.SHA1)) {
            CleanroomLogger.log2("Downloading: {}", json.assetIndex.url);
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            // FileUtils.copyURLToFile(json.assetIndex.url, target);
            FileUtils.copyURLToFile(new URL(json.assetIndex.url), target);
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
