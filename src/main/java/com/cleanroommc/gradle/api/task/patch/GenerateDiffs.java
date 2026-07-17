package com.cleanroommc.gradle.api.task.patch;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

@DisableCachingByDefault(because = "Generates committed patches from a mutable development tree")
public abstract class GenerateDiffs extends DefaultTask {

    @Inject
    public abstract FileSystemOperations getFileSystemOperations();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getOriginalDirectory();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getModifiedDirectory();

    @Input
    public abstract Property<Integer> getContextLines();

    @Input
    public abstract Property<Boolean> getCleanOutput();

    /**
     * The names identity these patches are generated in (see {@link com.cleanroommc.gradle.api.names.NamesSource}).
     * When present, a {@code .mappings.json} stamp is written into the patches directory
     * so {@link ApplyDiffs} can refuse to apply them against a differently-named base.
     * Absent for decompiler-level (srg) patch sets.
     */
    @Optional
    @Input
    public abstract Property<String> getMappingsId();

    /** The mcp_config version recorded alongside the names id in {@code .mappings.json}. */
    @Optional
    @Input
    public abstract Property<String> getMcpConfigVersion();

    @OutputDirectory
    public abstract DirectoryProperty getPatchesDirectory();

    public GenerateDiffs() {
        this.getContextLines().convention(3);
        this.getCleanOutput().convention(true);
    }

    @TaskAction
    public void generateDiffs() {
        var counter = new AtomicInteger();
        var originalDir = this.getOriginalDirectory().get().getAsFile();
        var patchesDir = this.getPatchesDirectory().get().getAsFile();
        var contextLines = this.getContextLines().get();

        if (this.getCleanOutput().get()) {
            this.getFileSystemOperations().delete(spec -> spec.delete(patchesDir));
        }

        this.getModifiedDirectory().getAsFileTree().visit(fvd -> {
            if (!fvd.isDirectory()) {
                var modifiedFile = fvd.getFile();
                var relativePath = fvd.getRelativePath().getPathString();
                var originalFile = new File(originalDir, relativePath);

                if (originalFile.exists()) {
                    try {
                        var originalLines = FileUtils.readLines(originalFile, StandardCharsets.UTF_8);
                        var modifiedLines = FileUtils.readLines(modifiedFile, StandardCharsets.UTF_8);

                        var diff = DiffUtils.diff(originalLines, modifiedLines);

                        if (!diff.getDeltas().isEmpty()) {
                            this.getLogger().lifecycle("Patching {}", relativePath);
                            counter.incrementAndGet();
                            var header = relativePath.replace("\\", "/");
                            var unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff("original/" + header, "modified/" + header, originalLines, diff, contextLines);
                            var patchFile = new File(patchesDir, header + ".patch");
                            patchFile.delete();
                            FileUtils.writeLines(new File(patchesDir, header + ".patch"), StandardCharsets.UTF_8.name(), unifiedDiff);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error", t); // TODO: skip and log error?
                    }
                } else {
                    throw new RuntimeException("A new file (" + relativePath + ") has been added, this is not supported!");
                }
            }
        });
        this.getLogger().lifecycle("{} files were patched.", counter.get());

        writeMappingsStamp(patchesDir);
    }

    /** Records which names the patches were generated in, for {@link ApplyDiffs} to validate against. */
    private void writeMappingsStamp(File patchesDir) {
        if (!this.getMappingsId().isPresent()) {
            return;
        }
        var json = new JsonObject();
        json.addProperty("names", this.getMappingsId().get());
        if (this.getMcpConfigVersion().isPresent()) {
            json.addProperty("mcpConfig", this.getMcpConfigVersion().get());
        }
        var stamp = new File(patchesDir, ".mappings.json");
        try {
            patchesDir.mkdirs();
            FileUtils.writeStringToFile(stamp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write mapping-identity stamp " + stamp, e);
        }
        this.getLogger().lifecycle("Recorded names identity '{}' in {}", this.getMappingsId().get(), stamp);
    }

}
