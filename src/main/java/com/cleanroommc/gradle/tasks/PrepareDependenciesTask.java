package com.cleanroommc.gradle.tasks;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.json.MinecraftVersion;
import com.cleanroommc.gradle.json.MinecraftVersion.Artifact;
import com.cleanroommc.gradle.json.MinecraftVersion.Library;
import com.cleanroommc.gradle.util.CacheUtils;
import com.cleanroommc.gradle.util.CacheUtils.HashAlgorithm;
import com.cleanroommc.gradle.util.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.cleanroommc.gradle.Constants.*;

// TODO: figure out OS rules
public abstract class PrepareDependenciesTask extends DefaultTask {

    public static TaskProvider<PrepareDependenciesTask> setupPrepareDependenciesTask(Project project) {
        return Utils.prepareTask(project, PREPARE_DEPENDENCIES_TASK, PrepareDependenciesTask.class);
    }

    @TaskAction
    public void task$downloadDependencies() throws IOException {
        MinecraftVersion version = MinecraftExtension.get(Constants.PROJECT).getVersionInfo();
        File librariesFolder = LIBRARIES_FOLDER.apply(version.id);
        File nativesFolder = NATIVES_FOLDER.apply(version.id);
        librariesFolder.mkdirs();
        boolean refreshNatives = false;
        for (Library library : version.libraries()) {
            Artifact artifact = library.downloads.artifact;
            if (artifact != null) {
                File target = new File(librariesFolder, artifact.path);
                if (library.extract != null) {
                    String hash = null;
                    File parentFolder = target.getParentFile();
                    if (parentFolder == null || parentFolder.listFiles() == null) {
                        CleanroomLogger.log2("Downloading Library: {}", artifact.url);
                        File originalTarget = new File(target + ".original");
                        FileUtils.copyURLToFile(artifact.url, originalTarget);
                        FileUtils.copyFile(originalTarget, target);
                        try (FileSystem fs = FileSystems.newFileSystem(target.toPath())) {
                            for (String exclude : library.extract.exclude) {
                                Path path = fs.getPath(exclude);
                                if (Files.exists(path)) {
                                    Files.walkFileTree(path, new SimpleFileVisitor<>() {
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
                            }
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
                            if (CacheUtils.isFileCorrupt(originalTarget, artifact.sha1, HashAlgorithm.SHA1)) {
                                CleanroomLogger.log2("Downloading Library: {}", artifact.url);
                                FileUtils.copyURLToFile(artifact.url, originalTarget);
                            }
                            FileUtils.copyFile(originalTarget, target);
                            try (FileSystem fs = FileSystems.newFileSystem(target.toPath())) {
                                for (String exclude : library.extract.exclude) {
                                    Path path = fs.getPath(exclude);
                                    if (Files.exists(path)) {
                                        Files.walkFileTree(path, new SimpleFileVisitor<>() {
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
                                }
                            }
                            hash = CacheUtils.hash(target, HashAlgorithm.SHA1);
                            new File(parentFolder, hash + ".sha1").createNewFile();
                        }
                    }
                } else if (CacheUtils.isFileCorrupt(target, artifact.sha1, HashAlgorithm.SHA1)) {
                    CleanroomLogger.log2("Downloading Library: {}", artifact.url);
                    FileUtils.copyURLToFile(artifact.url, target);
                }
            }
            if (library.downloads.classifiers != null) {
                Artifact classifier = library.downloads.classifiers.get(library.natives.get(Constants.OPERATING_SYSTEM.toString()));
                if (classifier != null) {
                    File target = new File(nativesFolder, classifier.path);
                    if (CacheUtils.isFileCorrupt(target, classifier.sha1, HashAlgorithm.SHA1)) {
                        refreshNatives = true;
                        CleanroomLogger.log2("Downloading Native Library: {}", classifier.url);
                        FileUtils.copyURLToFile(classifier.url, target);
                    }
                }
            }
        }
        if (refreshNatives) {
            extractNatives(version);
        }
    }

    private void extractNatives(MinecraftVersion version) throws IOException {
        File nativesFolder = NATIVES_FOLDER.apply(version.id);
        File extractedNativesFolder = EXTRACTED_NATIVES_FOLDER.apply(version.id);
        extractedNativesFolder.mkdirs();
        Files.walkFileTree(nativesFolder.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Objects.requireNonNull(file);
                Objects.requireNonNull(attrs);
                if (file.toString().endsWith(".jar")) { // Process this native jar
                    CleanroomLogger.log2("Extracting files from: {}", file.toString());
                    try (InputStream inputStream = Files.newInputStream(Paths.get(file.toUri())); ZipInputStream zis = new ZipInputStream(inputStream)) {
                        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                            if (!entry.isDirectory() && !entry.getName().startsWith("META-INF/")) {
                                File output;
                                if (entry.getName().endsWith(".class")) {
                                    output = new File(extractedNativesFolder, entry.getName());
                                } else {
                                    String[] splits = entry.getName().split("/");
                                    output = new File(extractedNativesFolder, splits[splits.length - 1]);
                                }
                                try (FileOutputStream fos = new FileOutputStream(output)) {
                                    IOUtils.copy(zis, fos);
                                }
                            }
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
