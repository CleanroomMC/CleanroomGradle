package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Downloader;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.Manifest;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static com.cleanroommc.gradle.Constants.*;

public abstract class DownloadVersionTask extends DefaultTask {

    public static TaskProvider<DownloadVersionTask> setupDownloadVersionTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_VERSION, DownloadVersionTask.class);
    }

    public DownloadVersionTask() {
        getMinecraftVersion().convention(getProject().provider(() -> MinecraftExtension.get(getProject()).getVersion()));
        getVersionDir().convention(MINECRAFT_VERSIONS_DIR);
    }

    @TaskAction
    public void task$downloadVersion() throws IOException {
        downloadVersion();
    }

    public void downloadVersion() throws IOException {
        String mcVersion = getMinecraftVersion().get();
        URL url = Utils.loadJson(getManifestFile().getAsFile().get(), Manifest.class).getUrl(mcVersion);
        getVersionFile().set(getVersionDir().file(mcVersion + ".json").get().getAsFile());
        if (!Downloader.downloadEtaggedFile(url, getVersionFile().get().getAsFile(), false)) {
            throw new RuntimeException("Unable to download + " + mcVersion + " version json.");
        }
    }

    @Internal
    public abstract RegularFileProperty getManifestFile();

    @Internal
    public abstract Property<String> getMinecraftVersion();

    @OutputDirectory
    public abstract DirectoryProperty getVersionDir();

    @OutputFile
    public abstract RegularFileProperty getVersionFile();

}
