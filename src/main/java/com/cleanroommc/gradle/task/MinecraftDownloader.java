package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.dependency.Side;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.util.DirectoryUtil;
import com.cleanroommc.gradle.util.DownloadUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class MinecraftDownloader extends DefaultTask {
    @Input
    public abstract MapProperty<String, VersionMetadata> getManifests();

    public List<File> dirs;
    @OutputFiles
    public List<File> getDirs() {
        return dirs;
    }

    public MinecraftDownloader() {
        dirs = new ArrayList<>();
    }

    @TaskAction
    public void execute() {
        getManifests().get().forEach((versionString, versionMetadata) -> {
            downloadedAssetManifests(versionString, versionMetadata);
            downloadDependencies(versionString, versionMetadata);
            downloadNatives(versionString, versionMetadata);
            downloadSide(versionString, versionMetadata);
        });
    }

    private void downloadedAssetManifests(final String version, final VersionMetadata toDownload) {
        final var dir = DirectoryUtil.create(getProject(), dirs -> dirs.getAssetManifestForVersion(version));
        dirs.add(dir);
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
                return dir;
            }
        });
    }

    private void downloadDependencies(final String version, final VersionMetadata toDownload) {
        List<DownloadUtil.IDownload> libs = new ArrayList<>();
        for (VersionMetadata.Library library : toDownload.libraries()) {
            if (library.isValidForOS()) {
                final var lib = library.artifact();
                if (lib != null) {
                    final var libDir = DirectoryUtil.create(getProject(), dir -> dir.getLibs(version));
                    dirs.add(libDir);
                    libs.add(DownloadUtil.toIDownload(lib, libDir));
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
                    final var nativeDir = DirectoryUtil.create(getProject(), dir -> dir.getNatives(version));
                    dirs.add(nativeDir);
                    natives.add(DownloadUtil.toIDownload(lib, nativeDir));
                }
            }
        }
        DownloadUtil.downloadFiles(natives);
    }

    private void downloadSide(final String version, final VersionMetadata toDownload) {
        final var runtimeDir = DirectoryUtil.create(getProject(), dir -> dir.getSide(version));
        dirs.add(runtimeDir);
        DownloadUtil.downloadFiles(
                List.of(toDownload.downloads().get(Side.CLIENT_ONLY.getValue()),
                        toDownload.downloads().get(Side.SERVER_ONLY.getValue())),
                download -> DownloadUtil.toIDownload(download, runtimeDir));
    }
}
