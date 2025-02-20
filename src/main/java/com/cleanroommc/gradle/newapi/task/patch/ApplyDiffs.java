package com.cleanroommc.gradle.newapi.task.patch;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ApplyDiffs extends DefaultTask {

    @InputDirectory
    public abstract DirectoryProperty getOriginalDirectory();

    @InputDirectory
    public abstract DirectoryProperty getPatchesDirectory();

    @Optional
    @OutputDirectory
    public abstract DirectoryProperty getModifiedDirectory();

    @Input
    public abstract Property<Boolean> getInPlace();

    public ApplyDiffs() {
        this.getInPlace().convention(false);
    }

    @TaskAction
    public void applyDiffs() {
        var inPlace = this.getInPlace().get();
        if (!inPlace && !this.getModifiedDirectory().isPresent()) {
            throw new InvalidUserDataException("When inplace is false, modifiedDirectory must be specified.");
        }

        var project = this.getProject();
        var logger = project.getLogger();
        var originalDir = this.getOriginalDirectory().get().getAsFile();
        var patchesDir = this.getPatchesDirectory().get().getAsFile();
        var modifiedDir = inPlace ? null : this.getModifiedDirectory().get().getAsFile();
        var patchesTree = project.fileTree(patchesDir).matching(pf -> pf.include("**/*.patch"));
        int totalPatches = patchesTree.getFiles().size();
        var counter = new AtomicInteger();

        patchesTree.visit(fvd -> {
            if (!fvd.isDirectory()) {
                var patchFile = fvd.getFile();
                var relativePath = fvd.getRelativePath().getPathString();
                relativePath = relativePath.substring(0, relativePath.lastIndexOf('.')); // Remove .patch
                var originalFile = new File(originalDir, relativePath);
                File targetFile;
                if (inPlace) {
                    targetFile = new File(originalDir, relativePath);
                } else {
                    targetFile = new File(modifiedDir, relativePath);
                    project.copy(spec -> {
                        spec.from(originalFile);
                        spec.into(targetFile);
                    });
                }

                if (!targetFile.exists()) {
                    logger.lifecycle("Skipping {} as original file is not found. {}/{} patches applied.", relativePath, counter.incrementAndGet(), totalPatches);
                } else {
                    try {
                        var patch = UnifiedDiffUtils.parseUnifiedDiff(FileUtils.readLines(patchFile, StandardCharsets.UTF_8));
                        var originalLines = FileUtils.readLines(originalFile, StandardCharsets.UTF_8);
                        var patchedLines = DiffUtils.patch(originalLines, patch);
                        logger.lifecycle("Patching {}. {}/{} patches applied.", relativePath, counter.incrementAndGet(), totalPatches);
                        FileUtils.writeLines(targetFile, StandardCharsets.UTF_8.name(), patchedLines, false);
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error when patching %s".formatted(relativePath), t); // TODO: skip and log error?
                    }
                }
            }
        });

        logger.lifecycle("{}/{} patches were applied.", counter.get(), totalPatches);
    }

}
