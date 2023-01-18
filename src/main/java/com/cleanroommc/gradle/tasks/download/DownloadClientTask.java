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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

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
        String hash = null;
        File parentFolder = target.getParentFile();
        if (parentFolder == null || parentFolder.listFiles() == null) {
            CleanroomLogger.log2("Downloading: {}", client.url);
            File originalTarget = new File(target + ".original");
            FileUtils.copyURLToFile(client.url, originalTarget);
            FileUtils.copyFile(originalTarget, target);
            try (FileSystem fs = FileSystems.newFileSystem(target.toPath())) {
                Files.walkFileTree(fs.getPath("META-INF"), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return super.visitFile(file, attrs);
                    }
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return super.postVisitDirectory(dir, exc);
                    }
                });
            }
            hash = CacheUtils.hash(target, HashAlgorithm.SHA1);
            new File(parentFolder, hash + ".sha1").createNewFile();
        } else {
            for (File check : parentFolder.listFiles()) {
                if (check.getName().endsWith("sha1")) {
                    hash = check.getName().split("\\.")[0];
                }
            }
            if (hash == null || CacheUtils.isFileCorrupt(target, hash, HashAlgorithm.SHA1)) {
                File originalTarget = new File(target + ".original");
                if (CacheUtils.isFileCorrupt(originalTarget, client.sha1, HashAlgorithm.SHA1)) {
                    CleanroomLogger.log2("Downloading Library: {}", client.url);
                    FileUtils.copyURLToFile(client.url, originalTarget);
                }
                FileUtils.copyFile(originalTarget, target);
                try (FileSystem fs = FileSystems.newFileSystem(target.toPath())) {
                    Files.walkFileTree(fs.getPath("META-INF"), new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.deleteIfExists(file);
                            return super.visitFile(file, attrs);
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.deleteIfExists(dir);
                            return super.postVisitDirectory(dir, exc);
                        }
                    });
                }
                hash = CacheUtils.hash(target, HashAlgorithm.SHA1);
                new File(parentFolder, hash + ".sha1").createNewFile();
            }
        }
        getJar().set(target);
    }

    @OutputFile
    public abstract RegularFileProperty getJar();

}
