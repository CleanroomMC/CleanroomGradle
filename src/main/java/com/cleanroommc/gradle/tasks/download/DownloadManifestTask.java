package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.util.Downloader;
import com.cleanroommc.gradle.util.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.net.URL;

import static com.cleanroommc.gradle.Constants.*;

public abstract class DownloadManifestTask extends DefaultTask {

    public static TaskProvider<DownloadManifestTask> setupDownloadManifestTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_MANIFEST, DownloadManifestTask.class);
    }

    public DownloadManifestTask() {
        getManifestSource().convention(MINECRAFT_MANIFEST_LINK);
        getManifest().convention(MINECRAFT_MANIFEST_FILE);
    }

    @TaskAction
    public void task$downloadManifest() throws IOException {
        if (!Downloader.downloadEtaggedFile(new URL(getManifestSource().get()), getManifest().get().getAsFile(), false)) {
            throw new RuntimeException("Unable to download manifest.");
        }
    }

    @Input
    public abstract Property<String> getManifestSource();

    @OutputFile
    public abstract RegularFileProperty getManifest();

}
