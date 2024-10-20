package com.cleanroommc.gradle.api.patch;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import groovy.lang.Closure;
import kotlin.jvm.functions.Function0;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// TODO: up-to-date status for this task when appropriate
public abstract class GenerateDiffs extends DefaultTask {

    private static FileSystem fileSystem(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return switch (Files.probeContentType(path)) {
                case "application/zip", "application/x-zip-compressed", "application/java-archive" -> FileSystems.newFileSystem(path);
                default -> FileSystems.getDefault();
            };
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

    @Input
    public abstract Property<Integer> getContextLines();

    private Object sourceObject, modifiedObject, outputObject;

    public GenerateDiffs() {
        getContextLines().convention(3);
    }

    @TaskAction
    public void generateDiffs() throws IOException { // TODO: handle exception properly
        var source = getSourcePath();
        var modified = getModifiedPath();
        var output = getOutputPath();

        var counter = new AtomicInteger();
        final int contextLines = getContextLines().get();

        var sourceFs = fileSystem(source);
        var sourceRoot = root(sourceFs, source);
        var modifiedFs = fileSystem(modified);
        var modifiedRoot = root(modifiedFs, modified);
        var outputFs = fileSystem(output);
        var outputRoot = root(outputFs, output);
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                var modifiedFile = modifiedRoot.resolve(sourceRoot.relativize(sourceFile).toString());
                if (Files.isRegularFile(modifiedFile)) {
                    var sourceLines = Files.readAllLines(sourceFile);
                    var modifiedLines = Files.readAllLines(modifiedFile);
                    var patch = DiffUtils.diff(sourceLines, modifiedLines);
                    var header = sourceRoot.relativize(sourceFile).toString().replace("\\", "/");
                    var unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff("source/" + header, "modified/" + header, sourceLines, patch, contextLines);
                    if (!unifiedDiff.isEmpty()) {
                        counter.incrementAndGet();
                        var outputPath = patchPath(outputRoot.resolve(sourceRoot.relativize(sourceFile).toString()));
                        Files.createDirectories(outputPath.getParent());
                        Files.write(outputPath, unifiedDiff);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (sourceFs != FileSystems.getDefault()) {
            sourceFs.close();
        }
        if (modifiedFs != FileSystems.getDefault()) {
            modifiedFs.close();
        }
        if (outputFs != FileSystems.getDefault()) {
            outputFs.close();
        }
        getLogger().lifecycle("{} patches generated", counter.get());
    }

    @Internal
    public Path getSourcePath() {
        return resolveToPath(sourceObject);
    }

    @Internal
    public Path getModifiedPath() {
        return resolveToPath(modifiedObject);
    }

    @Internal
    public Path getOutputPath() {
        return resolveToPath(outputObject);
    }

    public void source(Object object) {
        this.sourceObject = object;
    }

    public void modified(Object object) {
        this.modifiedObject = object;
    }

    public void output(Object object) {
        this.outputObject = object;
    }

    private Path resolveToPath(Object object) {
        if (object instanceof Function0) {
            return resolveToPath(((Function0) object).invoke());
        }
        if (object instanceof Closure) {
            return resolveToPath(((Closure) object).call());
        }
        if (object instanceof Provider) {
            return resolveToPath(((Provider) object).get());
        }
        if (object instanceof Supplier) {
            return resolveToPath(((Supplier) object).get());
        }
        if (object instanceof Callable<?>) {
            try {
                return resolveToPath(((Callable) object).call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (object instanceof FileSystemLocation fsl) {
            return fsl.getAsFile().toPath();
        }
        if (object instanceof File file) {
            return file.toPath();
        }
        if (object instanceof Path path) {
            return path;
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
