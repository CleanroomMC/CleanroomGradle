package com.cleanroommc.gradle.api.task.patch;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@DisableCachingByDefault(because = "Can modify the original source tree in place")
public abstract class ApplyDiffs extends DefaultTask {

    @Inject
    public abstract FileSystemOperations getFileSystemOperations();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getOriginalDirectory();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getPatchesDirectory();

    @Input
    public abstract Property<Boolean> getInPlace();

    @Optional
    @OutputDirectory
    public abstract DirectoryProperty getModifiedDirectory();

    public ApplyDiffs() {
        this.getInPlace().convention(false);
    }

    @TaskAction
    public void applyDiffs() {
        var inPlace = this.getInPlace().get();
        if (!inPlace && !this.getModifiedDirectory().isPresent()) {
            throw new InvalidUserDataException("When inPlace is false, modifiedDirectory must be specified.");
        }

        var fsOps = this.getFileSystemOperations();
        var originalDir = this.getOriginalDirectory().get().getAsFile();
        var patchesDir = this.getPatchesDirectory().get().getAsFile();
        var modifiedDir = inPlace ? null : this.getModifiedDirectory().get().getAsFile();
        var patchesTree = this.getPatchesDirectory().getAsFileTree().matching(pf -> pf.include("**/*.patch"));
        int totalPatches = patchesTree.getFiles().size();
        var counter = new AtomicInteger();

        if (!inPlace) {
            fsOps.copy(spec -> {
                spec.from(originalDir);
                spec.into(modifiedDir);
            });
        }

        patchesTree.visit(fvd -> {
            if (!fvd.isDirectory()) {
                var patchFile = fvd.getFile();
                var relativePath = fvd.getRelativePath().getPathString();
                relativePath = relativePath.substring(0, relativePath.lastIndexOf('.')); // Remove .patch
                var originalFile = new File(originalDir, relativePath);
                File targetFile = inPlace ? originalFile : new File(modifiedDir, relativePath);

                if (!targetFile.exists()) {
                    this.getLogger().lifecycle("Skipping {} as original file is not found. {}/{} patches applied.", relativePath, counter.incrementAndGet(), totalPatches);
                } else {
                    try {
                        var patch = UnifiedDiffUtils.parseUnifiedDiff(FileUtils.readLines(patchFile, StandardCharsets.UTF_8));
                        var originalLines = FileUtils.readLines(originalFile, StandardCharsets.UTF_8);
                        var patchedLines = DiffUtils.patch(originalLines, patch);
                        this.getLogger().lifecycle("Patching {}. {}/{} patches applied.", relativePath, counter.incrementAndGet(), totalPatches);
                        FileUtils.writeLines(targetFile, StandardCharsets.UTF_8.name(), patchedLines, false);
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error when patching %s".formatted(relativePath), t); // TODO: skip and log error?
                    }
                }
            }
        });

        this.getLogger().lifecycle("{}/{} patches were applied.", counter.get(), totalPatches);
    }

}
