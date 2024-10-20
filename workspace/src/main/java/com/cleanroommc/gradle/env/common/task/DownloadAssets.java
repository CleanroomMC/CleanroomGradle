package com.cleanroommc.gradle.env.common.task;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.structure.Digesting;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.api.types.json.schema.AssetIndex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DownloadAssets extends DefaultTask {

    @Internal
    public abstract Property<AssetIndex> getAssetIndex();

    @OutputDirectory
    public abstract DirectoryProperty getObjects();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void downloadAssets() {
        if (!getAssetIndex().isPresent()) {
            throw new RuntimeException("AssetIndex is not present");
        }

        if (this.getProject().getGradle().getStartParameter().isOffline()) {
            this.getState().setDidWork(false);
            return;
        }

        var objectsDirectory = this.getObjects().get().getAsFile();

        for (int i = 0x00; i <= 0xFF; i++) {
            var objectDirectory = Locations.file(objectsDirectory, String.format("%02x", i));
            if (!objectDirectory.exists()) {
                objectDirectory.mkdir();
            }
        }

        var executor = this.getWorkerExecutor();
        var queue = executor.noIsolation();
        var assets = this.getAssetIndex().get().objectCollection();

        var downloads = new AtomicInteger(0);
        var amounts = assets.size();

        boolean ran = false;

        for (var asset : assets) {
            var target = Locations.file(objectsDirectory, asset.path());
            if (!target.exists()) {
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
                        } else if (!Digesting.checkSha1(target, params.getSha1().get())) {
                            FileUtils.deleteQuietly(target);
                            throw new RuntimeException("Asset %s had mismatching checksums. Downloaded %s | Expected %s ".formatted(
                                    target.getAbsolutePath(), Digesting.sha1(target), params.getSha1().get()));
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
