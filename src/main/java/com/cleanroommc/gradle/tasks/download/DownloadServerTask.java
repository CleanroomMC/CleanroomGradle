package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.util.CacheUtils;
import com.cleanroommc.gradle.util.CacheUtils.HashAlgorithm;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.VersionJson;
import com.cleanroommc.gradle.util.json.deserialization.VersionJson.Download;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.DOWNLOAD_SERVER_TASK;
import static com.cleanroommc.gradle.Constants.MINECRAFT_SERVER_FILE;

public abstract class DownloadServerTask extends DefaultTask {

    public static TaskProvider<DownloadServerTask> setupDownloadServerTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_SERVER_TASK, DownloadServerTask.class);
    }

    @TaskAction
    public void task$downloadServer() throws IOException {
        VersionJson json = Utils.loadJson(getMeta().get().getAsFile(), VersionJson.class);
        File target = MINECRAFT_SERVER_FILE.apply(json.id);
        Download server = json.downloads.get("server");
        if (CacheUtils.isFileCorrupt(target, server.sha1, HashAlgorithm.SHA1)) {
            CleanroomLogger.log2("Downloading: {}", server.url);
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            FileUtils.copyURLToFile(server.url, target);
        }
    }

    @InputFile
    public abstract RegularFileProperty getMeta();

}
