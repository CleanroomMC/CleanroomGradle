package com.cleanroommc.gradle.api.task.patch;

import com.cleanroommc.gradle.api.util.IO;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.google.gson.JsonObject;
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

    /**
     * The active names identity string (see {@link com.cleanroommc.gradle.api.names.NamesSource}). When
     * present, the patches directory's {@code .mappings.json} stamp is validated against it: a mismatch
     * fails the build, an absent stamp warns once (legacy patch set). Left unset for decompiler-level
     * (srg) patch sets, which skips validation entirely.
     */
    @Optional
    @Input
    public abstract Property<String> getMappingsId();

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

        validateMappingsIdentity();

        var fsOps = this.getFileSystemOperations();
        var originalDir = this.getOriginalDirectory().get().getAsFile();
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

    /**
     * Verifies the patch set was generated in the same names as the active pipeline. Only runs when a
     * {@code mappingsId} is configured (named patch sets); srg-level patch sets leave it unset.
     */
    private void validateMappingsIdentity() {
        if (!this.getMappingsId().isPresent()) {
            return;
        }
        var activeId = this.getMappingsId().get();
        var stamp = new File(this.getPatchesDirectory().get().getAsFile(), ".mappings.json");
        if (!stamp.isFile()) {
            this.getLogger().warn(
                    "Patch set {} has no .mappings.json (legacy patch set); applying against names '{}' without verification.",
                    this.getPatchesDirectory().get().getAsFile(), activeId);
            return;
        }
        var json = IO.readJson(stamp, JsonObject.class);
        var patchId = json.has("names") ? json.get("names").getAsString() : null;
        if (patchId == null || !patchId.equals(activeId)) {
            throw new InvalidUserDataException(
                    ("Mapping identity mismatch for patch set %s:%n"
                            + "  patches were generated in names: %s%n"
                            + "  the active names source is:       %s%n"
                            + "Regenerate the patches (generate*Diffs) against the active names, "
                            + "or switch the names source back (cleanroom.namesDirectory) to match.")
                            .formatted(this.getPatchesDirectory().get().getAsFile(), patchId, activeId));
        }
    }

}
