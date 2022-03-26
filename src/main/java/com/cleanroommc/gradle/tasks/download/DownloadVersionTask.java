package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.util.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.IOException;

import static com.cleanroommc.gradle.Constants.*;

public abstract class DownloadVersionTask extends DefaultTask {

    public static TaskProvider<DownloadVersionTask> setupDownloadVersionTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_VERSION, DownloadVersionTask.class);
    }

    public DownloadVersionTask() {
        getVersionDir().convention(MINECRAFT_VERSIONS_DIR);
    }

    @TaskAction
    public void downloadVersion() throws IOException {

    }

    @Internal
    public abstract Property<String> getMinecraftVersion();

    @OutputDirectory
    public abstract DirectoryProperty getVersionDir();

    @OutputFile
    public abstract RegularFileProperty getVersionFile();

}

