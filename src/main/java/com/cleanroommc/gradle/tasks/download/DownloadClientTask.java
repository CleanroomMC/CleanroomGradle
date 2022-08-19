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

import static com.cleanroommc.gradle.Constants.DOWNLOAD_CLIENT_TASK;
import static com.cleanroommc.gradle.Constants.MINECRAFT_CLIENT_FILE;

public abstract class DownloadClientTask extends DefaultTask {

    public static TaskProvider<DownloadClientTask> setupDownloadClientTask(Project project) {
        return Utils.prepareTask(project, DOWNLOAD_CLIENT_TASK, DownloadClientTask.class);
    }

    @TaskAction
    public void task$downloadClient() throws IOException {
        VersionJson json = Utils.loadJson(getMeta().get().getAsFile(), VersionJson.class);
        File target = MINECRAFT_CLIENT_FILE.apply(json.id);
        Download client = json.downloads.get("client");
        if (CacheUtils.isFileCorrupt(target, client.sha1, HashAlgorithm.SHA1)) {
            CleanroomLogger.log2("Downloading: {}", client.url);
            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            FileUtils.copyURLToFile(client.url, target);
        }
    }

    @InputFile
    public abstract RegularFileProperty getMeta();

}
