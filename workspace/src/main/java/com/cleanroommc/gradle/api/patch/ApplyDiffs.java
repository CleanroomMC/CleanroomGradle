package com.cleanroommc.gradle.api.patch;

import com.cleanroommc.gradle.api.structure.IO;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.PatchFailedException;
import groovy.lang.Closure;
import kotlin.jvm.functions.Function0;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// TODO: up-to-date status for this task when appropriate
public abstract class ApplyDiffs extends DefaultTask {

    private static FileSystem fileSystem(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return switch (Files.probeContentType(path)) {
                case "application/zip", "application/x-zip-compressed", "application/java-archive" -> FileSystems.newFileSystem(path);
                default -> FileSystems.getDefault();
            };
        }
        return FileSystems.getDefault();
    }

    private static FileSystem getOrNewFileSystem(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return switch (Files.probeContentType(path)) {
                case "application/zip", "application/x-zip-compressed", "application/java-archive" -> FileSystems.newFileSystem(path);
                default -> FileSystems.getDefault();
            };
        } else if (!Files.isDirectory(path)) {
            var fileName = path.getFileName().toString();
            if ((fileName.endsWith(".jar") || fileName.endsWith(".zip"))) {
                return IO.openZipFileSystem(path, true);
            }
        }
        return FileSystems.getDefault();
    }

    private static Path root(FileSystem fs, Path path) {
        return fs == FileSystems.getDefault() ? path : fs.getPath("/");
    }

    private static Path patchPath(Path outputPath) {
        var outputFileName = outputPath.getFileName().toString();
        outputFileName = outputFileName + ".patch";
        return outputPath.resolveSibling(outputFileName);
    }

    @Internal
    public abstract Property<Boolean> getCopyOverSource();

    private Object sourceObject, patchObject, modifiedObject;

    public ApplyDiffs() {
        getCopyOverSource().convention(false);
    }

    @TaskAction
    public void applyDiffs() throws IOException {
        var source = getSourcePath().toPath();
        var patch = getPatchPath().toPath();
        var modified = getModifiedPath().toPath();
        final var copyOverSource = getCopyOverSource().get();

        var totalPatches = new AtomicInteger();
        var totalApplied = new AtomicInteger();

        var sourceFs = fileSystem(source);
        var sourceRoot = root(sourceFs, source);
        var patchFs = fileSystem(patch);
        var patchRoot = root(patchFs, patch);
        var modifiedFs = getOrNewFileSystem(modified);
        var modifiedRoot = root(modifiedFs, modified);
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                var patchFile = patchPath(patchRoot.resolve(sourceRoot.relativize(sourceFile).toString()));
                if (Files.isRegularFile(patchFile)) {
                    totalPatches.incrementAndGet();
                    var patch = UnifiedDiffUtils.parseUnifiedDiff(Files.readAllLines(patchFile));
                    var sourceLines = Files.readAllLines(sourceFile);
                    var modifiedFile = modifiedRoot.resolve(sourceRoot.relativize(sourceFile).toString());
                    try {
                        var patchedLines = DiffUtils.patch(sourceLines, patch);
                        Files.createDirectories(modifiedFile.getParent());
                        Files.write(modifiedFile, patchedLines);
                        totalApplied.incrementAndGet();
                    } catch (PatchFailedException e) {
                        getLogger().error("Patch failed due to", e);
                    }
                } else if (copyOverSource) {
                    var modifiedFile = modifiedRoot.resolve(sourceRoot.relativize(sourceFile).toString());
                    Files.createDirectories(modifiedFile.getParent());
                    Files.copy(sourceFile, modifiedFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (sourceFs != FileSystems.getDefault()) {
            sourceFs.close();
        }
        if (patchFs != FileSystems.getDefault()) {
            patchFs.close();
        }
        if (modifiedFs != FileSystems.getDefault()) {
            modifiedFs.close();
        }
        getLogger().lifecycle("{}/{} patches applied.", totalApplied.get(), totalPatches.get());
    }

    @Internal
    public File getSourcePath() {
        return resolve(sourceObject);
    }

    @Internal
    public File getPatchPath() {
        return resolve(patchObject);
    }

    @Internal
    public File getModifiedPath() {
        return resolve(modifiedObject);
    }

    public void source(Object object) {
        this.sourceObject = object;
    }

    public void patch(Object object) {
        this.patchObject = object;
    }

    public void modified(Object object) {
        this.modifiedObject = object;
    }

    private File resolve(Object object) {
        if (object instanceof Function0) {
            return resolve(((Function0) object).invoke());
        }
        if (object instanceof Closure) {
            return resolve(((Closure) object).call());
        }
        if (object instanceof Provider) {
            return resolve(((Provider) object).get());
        }
        if (object instanceof Supplier) {
            return resolve(((Supplier) object).get());
        }
        if (object instanceof Callable<?>) {
            try {
                return resolve(((Callable) object).call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (object instanceof FileSystemLocation fsl) {
            return fsl.getAsFile();
        }
        if (object instanceof File file) {
            return file;
        }
        if (object instanceof Path path) {
            return path.toFile();
        }
        throw new RuntimeException("Path not specified."); // TODO: handle in parent methods
    }

    /*
    @InputDirectory
    public abstract DirectoryProperty getOriginalDirectory();

    @InputDirectory
    public abstract DirectoryProperty getModifiedDirectory();

    @Input
    public abstract Property<Integer> getContextLines();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    public GenerateDiffs() {
        this.getContextLines().convention(3); // Git convention
    }

    @TaskAction
    public void run() {
        var originalDirectory = getOriginalDirectory().getAsFile().get().toPath();
        var modifiedDirectory = getModifiedDirectory().getAsFile().get().toPath();
        var outputDirectory = getOutputDirectory().getAsFile().get().toPath();
        final var loc = getContextLines().get();

        try {
            var action = new GenerateDiffsAction(originalDirectory, modifiedDirectory, outputDirectory, loc);
            action.generate();
            getLogger().lifecycle(action.count() + " patches were generated.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
     */

}
