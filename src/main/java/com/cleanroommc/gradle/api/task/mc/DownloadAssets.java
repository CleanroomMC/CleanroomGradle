package com.cleanroommc.gradle.api.task.mc;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.AssetIndex;
import com.cleanroommc.gradle.api.util.CleanroomProblems;
import com.cleanroommc.gradle.api.util.IO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@DisableCachingByDefault(because = "Maintains a large shared asset store")
public abstract class DownloadAssets extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetIndexFile();

    @Internal
    public abstract DirectoryProperty getObjects();

    @OutputFiles
    public List<File> getAssetFiles() {
        if (!this.getAssetIndexFile().isPresent()) {
            return List.of();
        }
        var indexFile = this.getAssetIndexFile().get().getAsFile();
        if (!indexFile.isFile()) {
            return List.of();
        }
        var objectsDirectory = this.getObjects().get().getAsFile();
        return IO.readJson(indexFile, AssetIndex.class).objectCollection().stream()
                .map(asset -> new File(objectsDirectory, asset.path()))
                .toList();
    }

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @Inject
    public abstract Problems getProblems();

    private final boolean offline = this.getProject().getGradle().getStartParameter().isOffline();

    @TaskAction
    public void downloadAssets() {
        var assetIndex = IO.readJson(this.getAssetIndexFile().get().getAsFile(), AssetIndex.class);
        var objectsDirectory = this.getObjects().get().getAsFile();
        var assets = assetIndex.objectCollection();
        var problems = new ArrayList<AssetProblem>();
        for (var asset : assets) {
            var target = new File(objectsDirectory, asset.path());
            var problem = problem(target, asset);
            if (problem != null) {
                problems.add(new AssetProblem(asset, target, problem));
            }
        }
        if (problems.isEmpty()) {
            this.setDidWork(false);
            return;
        }
        if (this.offline) {
            var shown = problems.stream().limit(20)
                    .map(problem -> "  - %s: %s (%s)".formatted(problem.asset().realPath(), problem.reason(), problem.target()))
                    .collect(Collectors.joining("\n"));
            var remainder = problems.size() > 20 ? "\n  ... and " + (problems.size() - 20) + " more" : "";
            var details = "Gradle is offline and %d Minecraft asset(s) are missing or invalid:\n%s%s"
                    .formatted(problems.size(), shown, remainder);
            var solution = "Run " + getName() + " once without --offline to repair the shared asset cache.";
            throw CleanroomProblems.throwing(getProblems(), new GradleException(details + "\n" + solution),
                    CleanroomProblems.OFFLINE_ASSETS, spec -> spec.details(details)
                            .solution(solution)
                            .fileLocation(getAssetIndexFile().get().getAsFile().getAbsolutePath()));
        }

        this.getLogger().lifecycle("Downloading {} of {} Minecraft assets", problems.size(), assets.size());
        var queue = this.getWorkerExecutor().noIsolation();
        for (var problem : problems) {
            queue.submit(AssetAction.class, action -> {
                try {
                    action.getSourceUrl().set(new URI(Meta.RESOURCES_BASE_URL + problem.asset().path()).toURL());
                } catch (URISyntaxException | MalformedURLException e) {
                    throw new RuntimeException("Invalid Minecraft asset URL for " + problem.asset().hash(), e);
                }
                action.getSha1().set(problem.asset().hash());
                action.getSize().set(problem.asset().size());
                action.getTargetFile().set(problem.target());
            });
        }
    }

    private static String problem(File target, AssetIndex.AssetEntry asset) {
        if (!target.isFile()) {
            return target.exists() ? "object path is not a regular file" : "object is missing";
        }
        if (target.length() != asset.size()) {
            return "size is %d bytes; expected %d".formatted(target.length(), asset.size());
        }
        return IO.sha1Match(target, asset.hash()) ? null : "SHA-1 does not match " + asset.hash();
    }

    private record AssetProblem(AssetIndex.AssetEntry asset, File target, String reason) { }

    public interface AssetParameters extends WorkParameters {

        Property<URL> getSourceUrl();

        Property<String> getSha1();

        Property<Long> getSize();

        Property<File> getTargetFile();

    }

    public static abstract class AssetAction implements WorkAction<AssetParameters> {

        @Override
        public void execute() {
            var params = this.getParameters();
            for (int retry = 0; retry < 5; retry++) {
                try (var is = params.getSourceUrl().get().openStream()) {
                    var target = params.getTargetFile().get();
                    int size;
                    try (var os = FileUtils.openOutputStream(target)) {
                        size = IOUtils.copy(is, os);
                    }
                    if (size != params.getSize().get()) {
                        FileUtils.deleteQuietly(target);
                        throw new IOException("Asset %s had mismatching sizes. Downloaded %s | Expected %s"
                                .formatted(target.getAbsolutePath(), size, params.getSize().get()));
                    }
                    var actualSha1 = IO.sha1(target);
                    if (!actualSha1.equalsIgnoreCase(params.getSha1().get())) {
                        FileUtils.deleteQuietly(target);
                        throw new IOException("Asset %s had mismatching checksums. Downloaded %s | Expected %s"
                                .formatted(target.getAbsolutePath(), actualSha1, params.getSha1().get()));
                    }
                    return;
                } catch (IOException e) {
                    if (retry == 4) {
                        throw new RuntimeException("Failed to download %s to %s after 5 attempts. Check network access, then rerun downloadAssets."
                                .formatted(params.getSourceUrl().get(), params.getTargetFile().get()), e);
                    }
                }
            }
        }

    }

}
