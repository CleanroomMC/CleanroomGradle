/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.userdev;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.mcp.SplitJar;
import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches;
import com.cleanroommc.gradle.api.util.Execs;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.inject.MetadataInjector;
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

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
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Rebuilds every Minecraft piece a workspace needs from the vanilla jars, so the userdev artifact ships none.
 * The role transforms each take what they need out of the directory this produces.
 */
@CacheableTransform
public abstract class PrepareUserdev implements TransformAction<PrepareUserdev.Parameters> {

    /** Patched Minecraft in SRG names. */
    public static final String MINECRAFT_SRG = "minecraft-srg.jar";
    public static final String CLIENT_EXTRA = "client-extra.jar";
    public static final String SERVER_EXTRA = "server-extra.jar";

    static final String USERDEV = "userdev.jar";
    /** Vanilla Minecraft in SRG names, which the shipped source patches apply to once decompiled. */
    static final String SOURCE_INPUT = "source-input.jar";

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var config = UserdevConfig.readFromJar(input);
        var layout = config.layout();
        var output = outputs.dir("userdev-prepared");
        var work = output.toPath().resolveSibling("prepare-work");
        try {
            UserdevArchive.delete(work);
            Files.createDirectories(work);
            var userdev = new File(output, USERDEV);
            Files.copy(input.toPath(), userdev.toPath());
            var cache = getParameters()
                .getSharedCacheDirectory()
                .getAsFile()
                .get()
                .toPath()
                .resolve("versions")
                .resolve(config.minecraftVersion());
            var client = acquire(config.minecraft().client(), cache.resolve("client.jar"));
            var server = acquire(config.minecraft().server(), cache.resolve("server.jar"));
            var obfToSrg = UserdevArchive.extract(input, layout.obfToSrg(), work.resolve("obf2srg.tsrg").toFile());

            var vanilla = srg(
                input, config, work.resolve("vanilla"), client, server, obfToSrg, List.of(),
                new File(output, CLIENT_EXTRA), new File(output, SERVER_EXTRA)
            );
            var stripped = work.resolve("vanilla-sas.jar");
            SideOnlyHandler.applySas(
                vanilla.toPath(), stripped,
                List.of(
                    UserdevArchive.extract(
                        input, layout.sideAnnotationStrippers(), work.resolve("cleanroom.sas").toFile()
                    )
                        .toPath()
                )
            );
            UserdevArchive.accessTransform(
                getExecOperations(), getParameters().getAccessTransformerClasspath(), stripped.toFile(),
                new File(output, SOURCE_INPUT),
                UserdevArchive.accessTransformers(input, layout.accessTransformers(), work), work.toFile()
            );

            var binpatches = UserdevArchive.extract(
                getParameters().getUniversal().getSingleFile(), layout.binpatches(),
                work.resolve("binpatches.zip").toFile()
            )
                .toPath();
            var clientPatched = work.resolve("client-patched.jar");
            var serverPatched = work.resolve("server-patched.jar");
            ApplyBinPatches.apply(client, binpatches, layout.clientBinpatches(), clientPatched);
            ApplyBinPatches.apply(server, binpatches, layout.serverBinpatches(), serverPatched);
            var loaderSrg = work.resolve("loader-srg.jar").toFile();
            UserdevArchive.select(input, loaderSrg, name -> !name.startsWith(UserdevConfig.META + "/"), "");
            var loaderNotch = work.resolve("loader-notch.jar").toFile();
            UserdevArchive.rename(
                getExecOperations(), getParameters().getRenamerClasspath(), loaderSrg, loaderNotch, obfToSrg,
                getParameters().getLibraries(), "--reverse", "--naive-srg"
            );
            var patched = srg(
                input, config, work.resolve("patched"), clientPatched, serverPatched, obfToSrg, List.of(loaderNotch),
                work.resolve("client-extra.jar").toFile(), work.resolve("server-extra.jar").toFile()
            );
            IO.move(patched.toPath(), output.toPath().resolve(MINECRAFT_SRG));
            UserdevArchive.delete(work);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare Minecraft for " + input, e);
        }
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    /** Splits, merges, renames to SRG and injects metadata, the same steps the loader workspace runs. */
    private File srg(
        File input, UserdevConfig config, Path work, Path client, Path server, File obfToSrg, List<File> libraries,
        File clientExtra, File serverExtra
    ) throws IOException {
        Files.createDirectories(work);
        var layout = config.layout();
        var clientSlim = work.resolve("client-slim.jar").toFile();
        var serverSlim = work.resolve("server-slim.jar").toFile();
        SplitJar.split(client.toFile(), obfToSrg, clientSlim, clientExtra);
        SplitJar.split(server.toFile(), obfToSrg, serverSlim, serverExtra);
        var merged = work.resolve("merged.jar").toFile();
        Execs.quietJavaExec(getExecOperations(), spec -> {
            spec.setClasspath(getParameters().getMergeToolClasspath());
            spec.getMainClass().set("net.minecraftforge.mergetool.ConsoleMerger");
            spec.args("--client", clientSlim, "--server", serverSlim, "--output", merged, "-ann", config.minecraftVersion(), "--inject", false);
        });
        var renamed = work.resolve("srg.jar").toFile();
        var allLibraries = new ArrayList<>(libraries);
        getParameters().getLibraries().forEach(allLibraries::add);
        UserdevArchive.rename(
            getExecOperations(), getParameters().getRenamerClasspath(), merged, renamed, obfToSrg, allLibraries
        );
        var injected = work.resolve("injected.jar");
        MetadataInjector.inject(
            renamed.toPath(), injected,
            UserdevArchive.extract(input, layout.access(), work.resolve("access.txt").toFile()).toPath(),
            UserdevArchive.extract(input, layout.constructors(), work.resolve("constructors.txt").toFile()).toPath(),
            UserdevArchive.extract(input, layout.exceptions(), work.resolve("exceptions.txt").toFile()).toPath()
        );
        return injected.toFile();
    }

    private Path acquire(UserdevConfig.Download download, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        var lockPath = target.resolveSibling(target.getFileName() + ".lock");
        try (var channel = FileChannel.open(
            lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE
        ); var ignored = channel.lock()) {
            if (IO.sha1Match(target, download.sha1())) {
                return target;
            }
            if (getParameters().getOffline().get()) {
                throw new IllegalStateException(
                    "Minecraft " + target.getFileName() + " is missing or corrupt in the shared cache. " +
                        "Resolve userdev once without --offline to repair it."
                );
            }
            var temporary = target.resolveSibling(target.getFileName() + ".part");
            var request = HttpRequest.newBuilder(URI.create(download.url()))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
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

    public interface Parameters extends TransformParameters {

        /** The universal jar, which carries the binpatches production also runs on. */
        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getUniversal();

        // The api and runtime variants declare different dependencies. Taking the artifact's own would prepare twice.
        @CompileClasspath
        ConfigurableFileCollection getLibraries();

        @Classpath
        ConfigurableFileCollection getRenamerClasspath();

        @Classpath
        ConfigurableFileCollection getAccessTransformerClasspath();

        @Classpath
        ConfigurableFileCollection getMergeToolClasspath();

        @Internal
        DirectoryProperty getSharedCacheDirectory();

        // Toggling --offline must not invalidate an already prepared directory
        @Internal
        Property<Boolean> getOffline();

    }

}
