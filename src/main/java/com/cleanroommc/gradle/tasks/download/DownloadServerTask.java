package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion;
import com.cleanroommc.gradle.json.MinecraftVersion.Download;
import com.cleanroommc.gradle.util.CacheUtils;
import com.cleanroommc.gradle.util.CacheUtils.HashAlgorithm;
import com.cleanroommc.gradle.util.Utils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.*;

public abstract class DownloadServerTask extends DefaultTask {

    public static TaskProvider<DownloadServerTask> setupDownloadServerTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_SERVER_TASK, DownloadServerTask.class);
    }

    @TaskAction
    public void task$downloadServer() throws IOException {
        MinecraftVersion version = MinecraftExtension.get(Constants.PROJECT).getVersionInfo();
        File target = MINECRAFT_SERVER_FILE.apply(version.id);
        Download server = version.downloads.server;
        if (CacheUtils.isFileCorrupt(target, server.sha1, HashAlgorithm.SHA1)) {
            CleanroomLogger.log2("Downloading: {}", server.url);
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            FileUtils.copyURLToFile(server.url, target);
        }
        getJar().set(target);
    }

    @OutputFile
    public abstract RegularFileProperty getJar();

}
