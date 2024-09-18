package com.cleanroommc.gradle.api.structure;

import de.undercouch.gradle.tasks.download.DownloadAction;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class IO {

    public static boolean exists(RegularFileProperty file) {
        return exists(file.get().getAsFile());
    }

    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    public static boolean exists(File file) {
        return file.exists();
    }

    public static InputStream open(String url) throws IOException, URISyntaxException {
        return new URI(url).toURL().openStream();
    }

    public static CompletableFuture<Void> download(Project project, Object location, Object output, Consumer<DownloadAction> actionConsumer) throws IOException {
        var action = new DownloadAction(project);
        action.src(location);
        action.dest(output);
        actionConsumer.accept(action);
        return action.execute(true);
    }

    public static CompletableFuture<Void> download(Project project, Object location, Object output) throws IOException {
        var action = new DownloadAction(project);
        action.src(location);
        action.dest(output);
        return action.execute(true);
    }

    public static void copyResource(String path, File to) {
        var loader = IO.class.getClassLoader();
        try (var stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException("Resource not found: " + path);
            }
            to.getParentFile().mkdirs();
            FileUtils.copyInputStreamToFile(stream, to);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read resource " + path, e);
        }
    }

    public static boolean isZipped(Path path) {
        if (Files.exists(path)) {
            try {
                switch (Files.probeContentType(path)) {
                    case "application/zip", "application/x-zip-compressed", "application/java-archive":
                        return true;
                }
            } catch (IOException ignore) { }
        }
        return false;
    }

    public static FileSystem openZipFileSystem(Path path, boolean create) throws IOException {
        return FileSystems.newFileSystem(path, Map.of("create", create));
    }

    public static void transformZipFiles(Path inputZip, Path outputZip, UnaryOperator<List<String>> linesTransformer) {
        if (isZipped(inputZip)) {
            try (var inFs = openZipFileSystem(inputZip, false)) {
                try (var outFs = openZipFileSystem(outputZip, !isZipped(outputZip))) {
                    for (var root : inFs.getRootDirectories()) {
                        try (var stream = Files.walk(root)) {
                            stream.filter(Files::isRegularFile).forEach(path -> {
                                try {
                                    var lines = Files.readAllLines(path);
                                    lines = linesTransformer.apply(lines);
                                    var outPath = outFs.getPath(root.relativize(path).toString());
                                    Files.createDirectories(outPath.getParent());
                                    Files.write(outPath, lines);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private IO() { }

}
