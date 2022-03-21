package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.AssetIndex;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.AssetIndex.AssetEntry;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.cleanroommc.gradle.Constants.*;

public class GrabAssetsTask extends DefaultTask {

    public static void setupDownloadAssetsTask(Project project) {
        GrabAssetsTask grabAssetsTask = project.getTasks().create(DL_MINECRAFT_ASSETS_TASK, GrabAssetsTask.class);
        grabAssetsTask.dependsOn(project.getTasks().getByPath(DL_MINECRAFT_ASSET_INDEX_TASK));
    }

    private File virtualRoot = null;

    @TaskAction
    public void downloadAndGet() throws IOException {
        if (!MINECRAFT_ASSET_OBJECTS_DIR.exists() || !MINECRAFT_ASSET_OBJECTS_DIR.isDirectory()) {
            MINECRAFT_ASSET_OBJECTS_DIR.mkdirs();
        }
        File indexFile = ASSET_INDEX_FILE.apply(MinecraftExtension.get(getProject()).getVersion());
        AssetIndex index = AssetIndex.load(indexFile);
        if (index.virtual) {
            virtualRoot = new File(ASSETS_CACHE_FOLDER, "virtual/" + Files.getNameWithoutExtension(indexFile.getName()));
            virtualRoot.mkdirs();
        }
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        for (Entry<String, AssetEntry> e : index.objects.entrySet()) {
            executor.submit(new GetAssetTask(new Asset(e.getKey(), e.getValue().hash, e.getValue().size), MINECRAFT_ASSET_OBJECTS_DIR, virtualRoot));
        }
        executor.shutdown(); // Complete & Shutdown
        int max = (int) executor.getTaskCount();
        // Keep it running
        try {
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                int done = (int) executor.getCompletedTaskCount();
                CleanroomLogger.log2("Current status: {}/{} ({}%)", done, max, (int) ((double) done / max * 100));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Asset {

        private final String name;
        private final String hash;
        private final String path;
        private final long size;

        private Asset(String name, String hash, long size) {
            this.name = name;
            this.hash = hash.toLowerCase();
            this.path = hash.substring(0, 2) + "/" + hash;
            this.size = size;
        }

    }

    private static class GetAssetTask implements Callable<Boolean> {

        private static final int MAX_TRIES = 5;

        private final Asset asset;
        private final File assetDir, virtualRoot;

        private GetAssetTask(Asset asset, File assetDir, File virtualRoot) {
            this.asset = asset;
            this.assetDir = assetDir;
            this.virtualRoot = virtualRoot;
        }

        @Override
        public Boolean call() {
            boolean worked = true;
            File file = new File(assetDir, asset.path);
            for (int tryNum = 1; tryNum < MAX_TRIES + 1; tryNum++) {
                try {
                    if (Utils.isFileCorruptSHA1(file, asset.size, asset.hash)) {
                        file.delete();
                    }
                    if (!file.exists()) {
                        file.getParentFile().mkdirs();
                        File localMc = new File(MINECRAFT_ASSET_OBJECTS_DIR, asset.path);
                        if (Utils.isFileCorruptSHA1(localMc, asset.size, asset.hash)) {
                            ReadableByteChannel channel = Channels.newChannel(new URL(MINECRAFT_ASSETS_LINK + "/" + asset.path).openStream());
                            try (FileOutputStream fout = new FileOutputStream(file);
                                 FileChannel fileChannel = fout.getChannel()) {
                                fileChannel.transferFrom(channel, 0, asset.size);
                            }
                        } else {
                            Utils.copyFile(localMc, file, asset.size);
                        }
                    }
                    if (virtualRoot != null) {
                        File virtual = new File(virtualRoot, asset.name);
                        if (Utils.isFileCorruptSHA1(virtual, asset.size, asset.hash)) {
                            virtual.delete();
                            Utils.copyFile(file, virtual);
                        }
                    }
                } catch (Exception e) {
                    CleanroomLogger.error("Error downloading asset (try no. {}) : {}", tryNum, asset.name);
                    e.printStackTrace();
                    worked = false;
                }
            }
            return worked;
        }
    }

}
