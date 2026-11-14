/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.schema.VersionMeta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.function.IOConsumer;

import net.minecraftforge.fml.relauncher.Side;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class IO {

    public static final Gson GSON;
    private static final Object HTTP_CLIENT_LOCK = new Object();
    private static final int ZIP_BUFFER = 1 << 16;

    private static HttpClient httpClient;

    static {
        GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .registerTypeAdapter(VersionMeta.Argument.class, new VersionMeta.ArgumentDeserializer())
            .create();
    }

    private IO() { }

    public static <T> T readJson(InputStream stream, Class<T> type) {
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T readJson(Path jsonPath, Class<T> type) {
        try (var reader = Files.newBufferedReader(jsonPath)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T readJson(File jsonPath, Class<T> type) {
        return readJson(jsonPath.toPath(), type);
    }

    public static <T> T readJson(String jsonString, Class<T> type) {
        try (var reader = new StringReader(jsonString)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static <T> T readJson(Path jsonPath, Type type) {
        try (var reader = Files.newBufferedReader(jsonPath)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T readJson(File jsonPath, Type type) {
        return readJson(jsonPath.toPath(), type);
    }

    public static String sha1(Path path) {
        try (var is = Files.newInputStream(path)) {
            return DigestUtils.sha1Hex(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String sha1(File path) {
        return sha1(path.toPath());
    }

    public static String sha1(String value) {
        return DigestUtils.sha1Hex(value);
    }

    public static boolean sha1Match(Path path, String expectedHash) {
        if (Files.exists(path)) {
            return sha1(path).equalsIgnoreCase(expectedHash);
        }
        return false;
    }

    public static boolean sha1Match(File path, String expectedHash) {
        return sha1Match(path.toPath(), expectedHash);
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static FileOutputStream out(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    public static FileInputStream in(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    public static BufferedInputStream bufferedIn(File file) throws FileNotFoundException {
        return new BufferedInputStream(in(file));
    }

    public static ZipOutputStream zipOut(File file) throws FileNotFoundException {
        return new ZipOutputStream(out(file));
    }

    public static ZipInputStream zipIn(File file) throws FileNotFoundException {
        return new ZipInputStream(in(file));
    }

    public static ZipOutputStream zipOut(Path path, int bufferSize) throws IOException {
        return new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path), bufferSize));
    }

    public static ZipInputStream zipIn(Path path, int bufferSize) throws IOException {
        return new ZipInputStream(new BufferedInputStream(Files.newInputStream(path), bufferSize));
    }

    public static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (var input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    public static void writeEntry(ZipOutputStream output, String name, byte[] data) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(data);
        output.closeEntry();
    }

    public static void writeString(Path path, String content) throws IOException {
        var parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    public static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Rewrites a ZIP with stable entry order and timestamps.
     *
     * @param path the archive to rewrite in place
     */
    public static void normalizeZip(Path path) {
        try {
            writeZip(path, output -> {
                try (var input = new ZipFile(path.toFile())) {
                    for (var entry : sortedEntries(input).entrySet()) {
                        writeEntry(output, entry.getKey(), readEntry(input, entry.getValue()));
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to normalize ZIP " + path, e);
        }
    }

    /**
     * Writes a ZIP to a temporary sibling and moves it over {@code target} once {@code writer} finishes,
     * so a failed write never leaves a truncated archive behind.
     *
     * @param target the archive to create or replace
     * @param writer writes the entries
     */
    public static void writeZip(Path target, IOConsumer<ZipOutputStream> writer) throws IOException {
        var absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        var temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        try {
            try (var output = zipOut(temporary, ZIP_BUFFER)) {
                writer.accept(output);
            }
            move(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * The file entries of an archive, sorted by name.
     *
     * @param zip the archive to index
     * @throws IOException if two entries share a name
     */
    public static TreeMap<String, ZipEntry> sortedEntries(ZipFile zip) throws IOException {
        var entries = new TreeMap<String, ZipEntry>();
        var enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            var entry = enumeration.nextElement();
            if (!entry.isDirectory() && entries.put(entry.getName(), entry) != null) {
                throw new IOException("Duplicate entry " + entry.getName() + " in " + zip.getName());
            }
        }
        return entries;
    }

    /**
     * Reads the file entry {@code name}, or returns {@code null} if the archive has no such file.
     *
     * @param archive the archive to read from
     * @param name the entry's path inside the archive
     */
    public static byte[] readEntry(File archive, String name) throws IOException {
        try (var zip = new ZipFile(archive)) {
            var entry = zip.getEntry(name);
            return entry == null || entry.isDirectory() ? null : readEntry(zip, entry);
        }
    }

    /**
     * Copies the file entry {@code name} to {@code output}.
     * Returns {@code false} if the archive has no such file.
     *
     * @param archive the archive to read from
     * @param name the entry's path inside the archive
     * @param output where the entry is copied to
     */
    public static boolean extractEntry(File archive, String name, Path output) throws IOException {
        try (var zip = new ZipFile(archive)) {
            var entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) {
                return false;
            }
            Files.createDirectories(output.toAbsolutePath().getParent());
            try (var input = zip.getInputStream(entry)) {
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }

    public static void writeLines(Path path, List<String> lines) throws IOException {
        FileUtils.writeLines(path.toFile(), StandardCharsets.UTF_8.name(), lines, "\n", false);
    }

    public static BufferedReader reader(Path path, Charset charset) throws IOException {
        return Files.newBufferedReader(path, charset);
    }

    public static BufferedReader reader(InputStream is, Charset charset) {
        return new BufferedReader(new InputStreamReader(is, charset));
    }

    public static void downloadWithETag(String url, File dest) {
        try {
            var parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create destination directory " + parent);
            }
            var etagFile = new File(dest.getParent(), dest.getName() + ".etag");
            var builder = HttpRequest.newBuilder(URI.create(url));
            if (dest.exists() && etagFile.exists()) {
                builder.header("If-None-Match", Files.readString(etagFile.toPath()).strip());
            }
            var response = httpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                Files.write(dest.toPath(), response.body());
                response.headers().firstValue("ETag").ifPresent(etag -> {
                    try {
                        Files.writeString(etagFile.toPath(), etag);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } else if (response.statusCode() != 304) {
                throw new RuntimeException(
                    "HTTP " + response.statusCode() + " downloading " + url + " to " + dest +
                        ". Check the URL, proxy, and repository availability."
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Interrupted while downloading " + url + " to " + dest + ". Rerun the task to retry.", e
            );
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to download " + url + " to " + dest +
                    ". Check network access and filesystem permissions, then rerun the task.",
                e
            );
        }
    }

    public static File runDir(File projectDir, String version, Environment env, Side side) {
        var runDir = FileUtils.getFile(
            projectDir, "run", version, env.toString(), side.name().toLowerCase(Locale.ENGLISH)
        );
        runDir.mkdirs();
        return runDir;
    }

    public static HttpClient httpClient() {
        synchronized (HTTP_CLIENT_LOCK) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            }
            return httpClient;
        }
    }

    static void closeHttpClient() {
        final HttpClient client;
        synchronized (HTTP_CLIENT_LOCK) {
            client = httpClient;
            httpClient = null;
        }
        if (client != null) {
            client.close();
        }
    }

}
