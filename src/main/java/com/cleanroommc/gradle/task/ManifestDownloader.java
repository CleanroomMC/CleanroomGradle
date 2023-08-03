package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.util.DirectoryUtil;
import com.cleanroommc.gradle.util.DownloadUtil;
import com.google.gson.Gson;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class ManifestDownloader extends DefaultTask {
    private final List<File> manifests;

    @Input
    public abstract Property<List<String>> getVersionsToGet();

    @OutputFiles
    public List<File> getManifests() {
        return manifests;
    }

    @Input
    public abstract Property<Gson> getGson();

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @Input
    public abstract Property<Boolean> getAlwaysValidateSha();

    public ManifestDownloader() {
        manifests = new ArrayList<>();
        getInputFile().convention(getProject().getObjects().fileProperty());
        getGson().convention(CleanroomMeta.GSON);
        getAlwaysValidateSha().convention(false);
    }

    @TaskAction
    public void execute() throws IOException {
        ManifestVersion parsedMainManifest;
        try (FileReader reader = new FileReader(getInputFile().get().getAsFile())) {
            parsedMainManifest = getGson().get().fromJson(reader, ManifestVersion.class);
        }

        final var reqVersions = this.getProject().getExtensions().getByType(CleanroomGradle.class).getVersions();
        var reqManifests = parsedMainManifest.versions().stream().
                filter(versions -> reqVersions.contains(versions.id())).toList();

        downloadManifests(reqManifests);
    }

    private void downloadManifests(List<ManifestVersion.Versions> manifestsToDownload) {
        var toDownload = new ArrayList<DownloadUtil.IDownload>();
        for (ManifestVersion.Versions manifest : manifestsToDownload) {
            final var manifestFile = DirectoryUtil.create(getProject(), dirs -> dirs.getVersionManifest(manifest.id()));
            manifests.add(manifestFile);
            toDownload.add(new DownloadUtil.IDownload() {
                @Override
                public String name() {
                    return manifest.id();
                }

                @Override
                public String sha1() {
                    return manifest.sha1();
                }

                @Override
                public String url() {
                    return manifest.url();
                }

                @Override
                public File file() {
                    return manifestFile;
                }
            });
        }

        DownloadUtil.downloadFiles(toDownload, (DownloadUtil.Config config) -> config.alwaysCheckSha(getAlwaysValidateSha().get()));
    }

}
