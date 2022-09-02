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
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.DOWNLOAD_CLIENT_TASK;
import static com.cleanroommc.gradle.Constants.MINECRAFT_CLIENT_FILE;

public abstract class DownloadClientTask extends DefaultTask {

    public static TaskProvider<DownloadClientTask> setupDownloadClientTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_CLIENT_TASK, DownloadClientTask.class);
    }

    @TaskAction
    public void task$downloadClient() throws IOException {
        MinecraftVersion version = MinecraftExtension.get(Constants.PROJECT).getVersionInfo();
        File target = MINECRAFT_CLIENT_FILE.apply(version.id);
        Download client = version.downloads.client;
        if (CacheUtils.isFileCorrupt(target, client.sha1, HashAlgorithm.SHA1)) {
            CleanroomLogger.log2("Downloading: {}", client.url);
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            FileUtils.copyURLToFile(client.url, target);
        }
        getJar().set(target);
    }

    @OutputFile
    public abstract RegularFileProperty getJar();

}
