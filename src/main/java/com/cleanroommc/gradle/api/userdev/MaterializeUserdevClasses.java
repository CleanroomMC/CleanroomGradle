package com.cleanroommc.gradle.api.userdev;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.mcp.SplitJar;
import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.inject.MetadataInjector;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

@CacheableTransform
public abstract class MaterializeUserdevClasses implements TransformAction<MaterializeUserdevClasses.Parameters> {

    public interface Parameters extends TransformParameters {
        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getAccessTransformers();

        @Classpath
        ConfigurableFileCollection getRenamerClasspath();

        @Classpath
        ConfigurableFileCollection getAccessTransformerClasspath();

        @Classpath
        ConfigurableFileCollection getMergeToolClasspath();

        @Internal
        DirectoryProperty getSharedCacheDirectory();

        // Toggling --offline must not invalidate an already materialized jar
        @Internal
        Property<Boolean> getOffline();
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @CompileClasspath
    @InputArtifactDependencies
    public abstract FileCollection getArtifactDependencies();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var config = UserdevConfig.readFromJar(input);
        var output = outputs.file("cleanroom-userdev-materialized.jar");
        var work = output.toPath().resolveSibling("classes-work");
        try {
            delete(work);
            Files.createDirectories(work);
            var loaderSrg = work.resolve("loader-srg.jar").toFile();
            UserdevArchive.select(input, loaderSrg, name -> !name.startsWith(UserdevConfig.META + "/"), "");
            var cache = getParameters().getSharedCacheDirectory().getAsFile().get().toPath()
                    .resolve("versions").resolve(config.minecraftVersion());
            var client = acquire(config.minecraft().client(), cache.resolve("client.jar"));
            var server = acquire(config.minecraft().server(), cache.resolve("server.jar"));
            var binpatches = extract(input, config.layout().binpatches(), work.resolve("binpatches.zip").toFile());
            var obfToSrg = extract(input, config.layout().obfToSrg(), work.resolve("obf2srg.tsrg").toFile());
            var clientPatched = work.resolve("client-patched.jar").toFile();
            var serverPatched = work.resolve("server-patched.jar").toFile();
            ApplyBinPatches.apply(client, binpatches.toPath(),
                    config.layout().clientBinpatches(), clientPatched.toPath());
            ApplyBinPatches.apply(server, binpatches.toPath(),
                    config.layout().serverBinpatches(), serverPatched.toPath());
            var clientSlim = work.resolve("client-slim.jar").toFile();
            var serverSlim = work.resolve("server-slim.jar").toFile();
            SplitJar.split(clientPatched, obfToSrg, clientSlim,
                    work.resolve("client-extra.jar").toFile());
            SplitJar.split(serverPatched, obfToSrg, serverSlim,
                    work.resolve("server-extra.jar").toFile());
            var merged = work.resolve("merged.jar").toFile();
            getExecOperations().javaexec(spec -> {
                spec.setClasspath(getParameters().getMergeToolClasspath());
                spec.getMainClass().set("net.minecraftforge.mergetool.ConsoleMerger");
                spec.args("--client", clientSlim, "--server", serverSlim, "--output", merged,
                        "-ann", config.minecraftVersion(), "--inject", false);
            });
            var loaderNotch = work.resolve("loader-notch.jar").toFile();
            rename(loaderSrg, loaderNotch, obfToSrg, List.of(), true, true);
            var remappedSrg = work.resolve("minecraft-srg.jar").toFile();
            rename(merged, remappedSrg, obfToSrg, List.of(loaderNotch));
            MetadataInjector.inject(remappedSrg.toPath(),
                    work.resolve("minecraft-injected.jar"),
                    extract(input, config.layout().access(), work.resolve("access.txt").toFile()).toPath(),
                    extract(input, config.layout().constructors(), work.resolve("constructors.txt").toFile()).toPath(),
                    extract(input, config.layout().exceptions(), work.resolve("exceptions.txt").toFile()).toPath());
            var minecraftSrg = work.resolve("minecraft-injected.jar").toFile();
            var mappings = extract(input, config.layout().srgToMcp(), work.resolve("srg2mcp.tsrg").toFile());

            var ats = new ArrayList<File>();
            try (var zip = new ZipFile(input)) {
                for (var path : config.layout().accessTransformers()) {
                    var file = work.resolve("ats").resolve(new File(path).getName()).toFile();
                    extract(zip, path, file);
                    ats.add(file);
                }
            }
            ats.addAll(getParameters().getAccessTransformers().getFiles());
            var minecraftAt = work.resolve("minecraft-at.jar").toFile();
            if (ats.isEmpty()) {
                Files.copy(minecraftSrg.toPath(), minecraftAt.toPath());
            } else {
                var arguments = new ArrayList<>(List.of("--inJar", minecraftSrg.getAbsolutePath(),
                        "--outJar", minecraftAt.getAbsolutePath(), "--logFile", "accesstransform.log"));
                for (var at : ats) {
                    arguments.add("--atFile");
                    arguments.add(at.getAbsolutePath());
                }
                getExecOperations().javaexec(spec -> {
                    spec.setClasspath(getParameters().getAccessTransformerClasspath());
                    spec.getMainClass().set("net.minecraftforge.accesstransformer.TransformerProcessor");
                    spec.setArgs(arguments);
                    spec.setWorkingDir(work.toFile());
                });
            }

            var minecraftMcp = work.resolve("minecraft-mcp.jar").toFile();
            var loaderMcp = work.resolve("loader-mcp.jar").toFile();
            rename(minecraftAt, minecraftMcp, mappings, List.of(loaderSrg));
            rename(loaderSrg, loaderMcp, mappings, List.of(minecraftSrg));
            merge(output, minecraftMcp, loaderMcp);
            delete(work);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize classes from " + input, e);
        }
    }

    private Path acquire(UserdevConfig.Download download, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        var lockPath = target.resolveSibling(target.getFileName() + ".lock");
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var ignored = channel.lock()) {
            if (IO.sha1Match(target, download.sha1())) {
                return target;
            }
            if (getParameters().getOffline().get()) {
                throw new IllegalStateException("Minecraft " + target.getFileName() + " is missing or corrupt in the shared cache. "
                        + "Resolve userdev once without --offline to repair it.");
            }
            var temporary = target.resolveSibling(target.getFileName() + ".part");
            var request = HttpRequest.newBuilder(URI.create(download.url()))
                    .timeout(Duration.ofMinutes(5))
                    .GET().build();
            var builder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30));
            var proxy = ProxySelector.getDefault();
            if (proxy != null) {
                builder.proxy(proxy);
            }
            try (var client = builder.build()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode() + " downloading " + download.url());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted downloading " + download.url(), e);
            }
            if (!IO.sha1Match(temporary, download.sha1())) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException("SHA-1 mismatch downloading " + download.url() + ".");
            }
            IO.move(temporary, target);
            return target;
        }
    }

    private void rename(File input, File output, File mappings, List<File> libraries) {
        rename(input, output, mappings, libraries, false, false);
    }

    private void rename(File input, File output, File mappings, List<File> libraries,
                        boolean reverse, boolean naiveSrg) {
        var arguments = new ArrayList<>(List.of("--input", input.getAbsolutePath(), "--map",
                mappings.getAbsolutePath(), "--output", output.getAbsolutePath()));
        if (reverse) {
            arguments.add("--reverse");
        }
        if (naiveSrg) {
            arguments.add("--naive-srg");
        }
        for (var library : libraries) {
            arguments.add("--lib");
            arguments.add(library.getAbsolutePath());
        }
        for (var library : getArtifactDependencies()) {
            arguments.add("--lib");
            arguments.add(library.getAbsolutePath());
        }
        var classpath = getParameters().getRenamerClasspath();
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(classpath);
            spec.getMainClass().set(mainClassOf(classpath));
            spec.setArgs(arguments);
        });
    }

    private static void merge(File output, File... inputs) throws IOException {
        try (var out = IO.zipOut(output)) {
            var names = new TreeMap<String, byte[]>();
            for (var input : inputs) {
                try (var zip = new ZipFile(input)) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (!entry.isDirectory() && !entry.getName().matches("META-INF/[^/]+\\.(SF|RSA|DSA)")) {
                            names.put(entry.getName(), IO.readEntry(zip, entry));
                        }
                    }
                }
            }
            for (var entry : names.entrySet()) {
                IO.writeEntry(out, entry.getKey(), entry.getValue());
            }
        }
    }

    private static File extract(File jar, String path, File output) throws IOException {
        try (var zip = new ZipFile(jar)) {
            extract(zip, path, output);
            return output;
        }
    }

    private static void extract(ZipFile zip, String path, File output) throws IOException {
        var entry = zip.getEntry(path);
        if (entry == null) {
            throw new IllegalStateException("Missing required userdev entry " + path + ".");
        }
        Files.createDirectories(output.toPath().getParent());
        try (var source = zip.getInputStream(entry)) {
            Files.copy(source, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String mainClassOf(Iterable<File> classpath) {
        for (var file : classpath) {
            try (var jar = new JarFile(file)) {
                var manifest = jar.getManifest();
                if (manifest != null) {
                    var main = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                    if (main != null) {
                        return main;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        throw new IllegalStateException("No Main-Class found on the renamer classpath.");
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

}
