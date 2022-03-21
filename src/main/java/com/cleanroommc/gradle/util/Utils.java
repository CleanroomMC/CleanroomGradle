package com.cleanroommc.gradle.util;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.util.json.deserialization.EnumAdaptorFactory;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersionsAdapter;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.AssetIndex;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import groovy.lang.Closure;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static com.cleanroommc.gradle.Constants.USER_AGENT;

public final class Utils {

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(EnumAdaptorFactory.INSTANCE)
            .registerTypeAdapter(ManifestVersionsAdapter.TYPE, new ManifestVersionsAdapter())
            .enableComplexMapKeySerialization()
            .setPrettyPrinting()
            .create();


    public static AssetIndex loadAssetsIndex(File json) throws JsonSyntaxException, JsonIOException, IOException {
        FileReader reader = new FileReader(json);
        AssetIndex a = GSON.fromJson(reader, AssetIndex.class);
        reader.close();
        return a;
    }

    public static InputStream getResource(String path) {
        return Utils.class.getResourceAsStream("/" + path);
    }

    public static <T> Closure<T> closure(Class<?> caller, Supplier<T> supplier) {
        return new Closure<T>(caller) {
            @Override
            public T call(Object... args) {
                return supplier.get();
            }
        };
    }

    public static <T> Closure<T> closure(Supplier<T> supplier) {
        return new Closure<T>(Utils.class) {
            @Override
            public T call(Object... args) {
                return supplier.get();
            }
        };
    }

    public static String getWithETag(Project project, String urlString, File cache, File etagFile) {
        try {
            if (project.getGradle().getStartParameter().isOffline()) { // No internet access, return early
                return Files.toString(cache, Charsets.UTF_8);
            }
            if (cache.exists() && cache.lastModified() + 60000 >= System.currentTimeMillis()) { // Disable re-requesting within a minute
                return Files.toString(cache, Charsets.UTF_8);
            }
            String etag;
            if (etagFile.exists()) {
                etag = Files.toString(etagFile, Charsets.UTF_8);
            } else {
                etagFile.getParentFile().mkdirs();
                etag = "";
            }

            URL url = new URL(urlString);

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setIfModifiedSince(cache.lastModified());

            if (!Strings.isNullOrEmpty(etag)) {
                con.setRequestProperty("If-None-Match", etag);
            }

            con.connect();

            if (con.getResponseCode() == 304) { // Existing file is fine, no need to replace
                Files.touch(cache); // Update file timestamp
                return Files.toString(cache, Charsets.UTF_8);
            } else if (con.getResponseCode() == 200) { // Update file
                byte[] data;
                try (InputStream stream = con.getInputStream()) {
                    data = ByteStreams.toByteArray(stream);
                }
                Files.write(data, cache);
                // Write ETag
                etag = con.getHeaderField("ETag");
                if (Strings.isNullOrEmpty(etag)) {
                    Files.touch(etagFile);
                } else {
                    Files.write(etag, etagFile, Charsets.UTF_8);
                }
                return new String(data);
            } else {
                CleanroomLogger.error("Etag download for {} failed with code " + con.getResponseCode(), urlString);
            }
            con.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cache.exists()) {
            try {
                return Files.toString(cache, Charsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Unable to obtain url (" + urlString + ") with etag!");
    }

    /**
     * Resolves the supplied object to a string.
     * @param obj object to resolve
     * @return resolved string, from these conventions:
     *         - If the input is null, this will return null.
     *         - Closures and Callables are called with no arguments and recursively called
     *         - Arrays: {@link Arrays#toString}
     *         - File: {@link File#getAbsolutePath()}
     *         - Everything else: {@link Object#toString()}
     */
    @SuppressWarnings("rawtypes")
    public static String resolveString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Closure) {
            return resolveString(((Closure) obj).call());
        }
        if (obj instanceof Callable) {
            try {
                return resolveString(((Callable) obj).call());
            } catch (Exception e) {
                return null;
            }
        } else if (obj instanceof File) {
            return ((File) obj).getAbsolutePath();
        } else if (obj.getClass().isArray()) {
            if (obj instanceof Object[]) {
                return Arrays.toString(((Object[]) obj));
            } else if (obj instanceof byte[]) {
                return Arrays.toString(((byte[]) obj));
            } else if (obj instanceof char[]) {
                return Arrays.toString(((char[]) obj));
            } else if (obj instanceof int[]) {
                return Arrays.toString(((int[]) obj));
            } else if (obj instanceof float[]) {
                return Arrays.toString(((float[]) obj));
            } else if (obj instanceof double[]) {
                return Arrays.toString(((double[]) obj));
            } else if (obj instanceof long[]) {
                return Arrays.toString(((long[]) obj));
            } else {
                return obj.getClass().getSimpleName();
            }
        } else {
            return obj.toString();
        }
    }

    public static void error(boolean throwError, String error) {
        if (throwError) {
            throw new RuntimeException(error);
        } else {
            CleanroomLogger.error(error);
        }
    }

    private Utils() { }

}
