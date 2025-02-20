package com.cleanroommc.gradle.newapi.ext;

import com.cleanroommc.gradle.newapi.Meta;
import com.cleanroommc.gradle.newapi.schema.Manifest;
import com.cleanroommc.gradle.newapi.schema.VersionMeta;
import com.cleanroommc.gradle.newapi.util.IO;
import de.undercouch.gradle.tasks.download.DownloadAction;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

public abstract class CleanroomExtension {

    public static CleanroomExtension get(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class);
    }

    @Inject
    public abstract Project getProject();

    public abstract DirectoryProperty getCacheDirectory();

    public abstract DirectoryProperty getVersionCacheDirectory();

    public abstract DirectoryProperty getLocalCacheDirectory();

    public abstract Property<Boolean> getDebug();

    public abstract Property<Manifest> getManifest();

    public abstract Property<VersionMeta> getVersionMeta();

    public abstract Property<Boolean> getDevelopInitialPatches();

    public CleanroomExtension() {
        final var project = this.getProject();

        this.getCacheDirectory().fileValue(new File(project.getGradle().getGradleUserHomeDir(), "caches/" + Meta.CG_FOLDER));
        this.getVersionCacheDirectory().convention(this.getCacheDirectory().dir("versions/1.12.2"));
        this.getLocalCacheDirectory().convention(this.getProject().getLayout().getBuildDirectory().dir(Meta.CG_FOLDER));
        this.getDebug().convention(false);
        this.getManifest().convention(this.getCacheDirectory().map(dir -> {
            var file = dir.file("version_manifest.json").getAsFile();
            if (!file.exists()) {
                var downloadAction = new DownloadAction(project);
                downloadAction.src(Meta.VERSION_MANIFEST_V2_URL);
                downloadAction.dest(file);
                downloadAction.useETag(true);
                try {
                    downloadAction.execute().join();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return IO.readJson(file, Manifest.class);
        }));
        this.getVersionMeta().convention(this.getVersionCacheDirectory().map(dir -> {
            var file = dir.file("meta.json").getAsFile();
            if (!file.exists()) {
                var downloadAction = new DownloadAction(project);
                downloadAction.src(this.getManifest().map(manifest -> manifest.version("1.12.2").url));
                downloadAction.dest(file);
                downloadAction.useETag(true);
                try {
                    downloadAction.execute().join();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return IO.readJson(file, VersionMeta.class);
        }));
        this.getDevelopInitialPatches().convention(false);
    }

}
