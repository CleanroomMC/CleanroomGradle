package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class IO {

    private static final Gson GSON;
    private static final Object HTTP_CLIENT_LOCK = new Object();

    private static HttpClient httpClient;

    static {
        GSON = new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .registerTypeAdapter(VersionMeta.Argument.class, new VersionMeta.ArgumentDeserializer())
                .create();
    }

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

    public static boolean sha1Match(Path path, String expectedHash) {
        if (Files.exists(path)) {
            return sha1(path).equalsIgnoreCase(expectedHash);
        }
        return false;
    }

    public static boolean sha1Match(File path, String expectedHash) {
        return sha1Match(path.toPath(), expectedHash);
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

    public static BufferedReader reader(InputStream is, Charset charset) {
        return new BufferedReader(new InputStreamReader(is, charset));
    }

    public static void downloadWithETag(String url, File dest) {
        try {
            dest.getParentFile().mkdirs();
            var etagFile = new File(dest.getParent(), dest.getName() + ".etag");
            var builder = HttpRequest.newBuilder(URI.create(url));
            if (dest.exists() && etagFile.exists()) {
                builder.header("If-None-Match", Files.readString(etagFile.toPath()).strip());
            }
            var response = httpClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                Files.write(dest.toPath(), response.body());
                response.headers().firstValue("ETag").ifPresent(etag -> {
                    try { Files.writeString(etagFile.toPath(), etag); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
            } else if (response.statusCode() != 304) {
                throw new RuntimeException("HTTP " + response.statusCode() + " downloading " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to download " + url, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download " + url, e);
        }
    }

    static HttpClient httpClient() {
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

    public static File runDir(File projectDir, String version, Environment env, Side side) {
        var runDir = FileUtils.getFile(projectDir, "run", version, env.toString(), side.name().toLowerCase(Locale.ENGLISH));
        runDir.mkdirs();
        return runDir;
    }

    private IO() { }

}
