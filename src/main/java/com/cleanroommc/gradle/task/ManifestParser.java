package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.extension.CleanroomGradle;
import com.cleanroommc.gradle.json.schema.ManifestVersion;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.util.DirectoryUtil;
import com.cleanroommc.gradle.util.DownloadUtil;
import com.google.gson.Gson;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public abstract class ManifestParser extends DefaultTask {

    @Input
    public abstract Property<List<String>> getVersionsToGet();

    public abstract MapProperty<String, VersionMetadata> getVersionMetadata();

    @Input
    public abstract Property<Gson> getGson();

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @Input
    public abstract Property<Boolean> getAlwaysValidateSha();

    public ManifestParser() {
        getInputFile().convention(getProject().getObjects().fileProperty());
        getGson().convention(CleanroomMeta.GSON);
        getAlwaysValidateSha().convention(false);
        getVersionMetadata().set(new HashMap<>());
        getVersionMetadata().finalizeValue();
    }

    @TaskAction
    public void execute() throws IOException {
        ManifestVersion parsedManifest;
        try (FileReader reader = new FileReader(getInputFile().get().getAsFile())) {
            parsedManifest = getGson().get().fromJson(reader, ManifestVersion.class);
        }

        final var reqVersions = this.getProject().getExtensions().getByType(CleanroomGradle.class).getVersions();
        var reqManifests = parsedManifest.versions().stream().
                filter(versions -> reqVersions.contains(versions.id())).toList();

        downloadManifests(reqManifests);
        parseDownloadedManifests(reqManifests);
    }


    private void downloadManifests(List<ManifestVersion.Versions> manifestsToDownload) {
        DownloadUtil.downloadFiles(manifestsToDownload, config -> config.alwaysCheckSha(getAlwaysValidateSha().get()),
                manifest -> new DownloadUtil.IDownload() {
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
                        return DirectoryUtil.create(getProject(), dirs -> dirs.getVersionManifest(manifest.id()));
                    }
                });
    }

    private void parseDownloadedManifests(List<ManifestVersion.Versions> manifestsToParse) throws IOException {
        for (ManifestVersion.Versions manifest : manifestsToParse) {
            var file = DirectoryUtil.create(getProject(), dirs -> dirs.getVersionManifest(manifest.id()));
            try (FileReader reader = new FileReader(file)) {
                getVersionMetadata().get().put(manifest.id(), getGson().get().fromJson(reader, VersionMetadata.class));
            }
        }
    }
}
