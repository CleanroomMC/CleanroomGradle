package com.cleanroommc.gradle.api.task.mc;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.AssetIndex;
import com.cleanroommc.gradle.api.util.IO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

@DisableCachingByDefault(because = "Maintains a large shared asset store")
public abstract class DownloadAssets extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetIndexFile();

    @OutputDirectory
    public abstract DirectoryProperty getObjects();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    private final boolean offline = this.getProject().getGradle().getStartParameter().isOffline();

    @TaskAction
    public void downloadAssets() {
        if (this.offline) {
            this.getState().setDidWork(false);
            return;
        }

        var assetIndex = IO.readJson(this.getAssetIndexFile().get().getAsFile(), AssetIndex.class);
        var objectsDirectory = this.getObjects().get().getAsFile();

        for (int i = 0x00; i <= 0xFF; i++) {
            var objectDirectory = new File(objectsDirectory, String.format("%02x", i));
            if (!objectDirectory.exists()) {
                objectDirectory.mkdir();
            }
        }

        var executor = this.getWorkerExecutor();
        var queue = executor.noIsolation();
        var assets = assetIndex.objectCollection();

        var downloads = new AtomicInteger(0);
        var amounts = assets.size();

        boolean ran = false;

        for (var asset : assets) {
            var target = new File(objectsDirectory, asset.path());
            if (!target.exists() || !IO.sha1Match(target, asset.hash())) {
                ran = true;
                queue.submit(AssetAction.class, action -> {
                    try {
                        this.getLogger().lifecycle("Downloading {}", asset.hash());
                        action.getSourceUrl().set(new URI(Meta.RESOURCES_BASE_URL + asset.path()).toURL());
                    } catch (URISyntaxException | MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                    action.getSha1().set(asset.hash());
                    action.getSize().set(asset.size());
                    action.getTargetFile().set(target);
                    action.getDownloads().set(downloads);
                });
            }
        }

        if (!ran || downloads.get() == amounts) {
            this.setDidWork(false);
        }

    }

    public interface AssetParameters extends WorkParameters {

        Property<URL> getSourceUrl();

        Property<String> getSha1();

        Property<Long> getSize();

        Property<File> getTargetFile();

        Property<AtomicInteger> getDownloads();

    }

    public static abstract class AssetAction implements WorkAction<AssetParameters> {

        @Override
        public void execute() {
            var params = this.getParameters();
            for (int retry = 0; retry < 5; retry++) {
                try (var is = params.getSourceUrl().get().openStream()) {
                    var target = params.getTargetFile().get();
                    try (var os = FileUtils.openOutputStream(target)) {
                        int size = IOUtils.copy(is, os);
                        if (size != params.getSize().get()) {
                            FileUtils.deleteQuietly(target);
                            throw new RuntimeException("Asset %s had mismatching sizes. Downloaded %s | Expected %s ".formatted(
                                    target.getAbsolutePath(), size, params.getSize().get()));
                        } else if (!IO.sha1Match(target, params.getSha1().get())) {
                            FileUtils.deleteQuietly(target);
                            throw new RuntimeException("Asset %s had mismatching checksums. Downloaded %s | Expected %s ".formatted(
                                    target.getAbsolutePath(), IO.sha1(target), params.getSha1().get()));
                        }
                        params.getDownloads().get().incrementAndGet();
                        return;
                    }
                } catch (IOException e) {
                    if (retry == 4) {
                        throw new RuntimeException("5 retries failed, unable to download.", e);
                    }
                }
            }
        }

    }

}
