package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.CleanroomMeta;
import com.cleanroommc.gradle.dependency.Side;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.util.DirectoryUtil;
import com.cleanroommc.gradle.util.DownloadUtil;
import com.google.gson.Gson;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class MinecraftDownloader extends DefaultTask {
    private final List<File> files;

    @Input
    public abstract ListProperty<File> getManifests();

    @OutputFiles
    public List<File> getFiles() {
        return files;
    }

    @Input
    public abstract Property<Gson> getGson();

    public MinecraftDownloader() {
        files = new ArrayList<>();
        getGson().convention(CleanroomMeta.GSON);
    }

    @TaskAction
    public void execute() throws IOException {
        for (var path : getManifests().get()) {
            VersionMetadata parsedMetadata;
            try (FileReader reader = new FileReader(path)) {
                parsedMetadata = getGson().get().fromJson(reader, VersionMetadata.class);
            }

            downloadedAssetManifests(parsedMetadata.id(), parsedMetadata);
            downloadDependencies(parsedMetadata.id(), parsedMetadata);
            downloadNatives(parsedMetadata.id(), parsedMetadata);
            downloadSide(parsedMetadata.id(), parsedMetadata);
        }
    }

    private void downloadedAssetManifests(final String version, final VersionMetadata toDownload) {
        final var file = DirectoryUtil.create(getProject(), dirs -> dirs.getAssetManifestForVersion(version));
        files.add(file);
        DownloadUtil.downloadFile(new DownloadUtil.IDownload() {
            @Override
            public String name() {
                return version + " AssetManifest";
            }

            @Override
            public String sha1() {
                return toDownload.assetIndex().sha1();
            }

            @Override
            public String url() {
                return toDownload.assetIndex().url();
            }

            @Override
            public File file() {
                return file;
            }
        });
    }

    private void downloadDependencies(final String version, final VersionMetadata toDownload) {
        List<DownloadUtil.IDownload> libs = new ArrayList<>();
        for (VersionMetadata.Library library : toDownload.libraries()) {
            if (library.isValidForOS()) {
                final var lib = library.artifact();
                if (lib != null) {
                    final var libFile = lib.relativeFile(DirectoryUtil.create(getProject(), dir -> dir.getLibs(version)));
                    files.add(libFile);
                    libs.add(DownloadUtil.toIDownload(lib, libFile));
                }
            }
        }
        DownloadUtil.downloadFiles(libs);
    }

    private void downloadNatives(final String version, final VersionMetadata toDownload) {
        List<DownloadUtil.IDownload> natives = new ArrayList<>();
        for (VersionMetadata.Library library : toDownload.libraries()) {
            if (library.hasNativesForOS()) {
                final var lib = library.classifierForOS();
                if (lib != null) {
                    final var nativeFile = lib.relativeFile(DirectoryUtil.create(getProject(), dir -> dir.getNatives(version)));
                    files.add(nativeFile);
                    natives.add(DownloadUtil.toIDownload(lib, nativeFile));
                }
            }
        }
        DownloadUtil.downloadFiles(natives);
    }

    private void downloadSide(final String version, final VersionMetadata toDownload) {
        List<DownloadUtil.IDownload> jars = new ArrayList<>(2);
        for (VersionMetadata.Download jar : List.of(toDownload.downloads().get(Side.CLIENT_ONLY.getValue()),
                toDownload.downloads().get(Side.SERVER_ONLY.getValue()))) {
            final var jarFile = jar.relativeFile(DirectoryUtil.create(getProject(), dir -> dir.getSide(version)));
            files.add(jarFile);
            jars.add(DownloadUtil.toIDownload(jar, jarFile));
        }
        DownloadUtil.downloadFiles(jars);
    }
}
