package com.cleanroommc.gradle.api.types;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import kotlin.jvm.functions.Function0;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Types {

    private static final Gson GSON;

    static {
        GSON = new GsonBuilder().create();
    }

    public static <T> Supplier<T> memoizedSupplier(Supplier<T> supplier) {
        return new MemoizedSupplier<>(supplier);
    }

    public static Gson gson() {
        return GSON;
    }

    public static <T> T readJson(String jsonString, Class<T> clazz) throws IOException {
        return GSON.fromJson(jsonString, clazz);
    }

    public static <T> T readJson(File jsonFile, Class<T> clazz) throws IOException {
        return readJson(jsonFile.toPath(), clazz);
    }

    public static <T> T readJson(Path jsonFile, Class<T> clazz) throws IOException {
        return GSON.fromJson(Files.newBufferedReader(jsonFile), clazz);
    }

    public static Object unravel(Object object) {
        if (object instanceof Provider<?> provider) {
            return unravel(provider.get());
        }
        if (object instanceof Callable<?> callable) {
            try {
                return unravel(callable.call());
            } catch (Exception e) {
                throw new RuntimeException("Encountered exception while unravelling object", e);
            }
        }
        if (object instanceof Supplier<?> supplier) {
            return unravel(supplier.get());
        }
        if (object instanceof Function0<?> function) {
            return unravel(function.invoke());
        }
        return object;
    }

    public static String resolveString(Object object) {
        var unravelled = unravel(object);
        if (unravelled instanceof File file) {
            file.getParentFile().mkdirs();
            return file.getAbsolutePath();
        }
        if (unravelled instanceof Path path) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return path.toAbsolutePath().toString();
        }
        if (unravelled instanceof FileSystemLocation location) {
            location.getAsFile().getParentFile().mkdirs();
            return location.getAsFile().getAbsolutePath();
        }
        if (unravelled instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).collect(Collectors.joining(", "));
        }
        // TODO: array?
        return unravelled.toString();
    }

    /* Thanks to RetroFuturaGradle */
    public static UUID resolveUuid(Project project, String username) {
        boolean isOffline = project.getGradle().getStartParameter().isOffline();
        var cache = Locations.global(project, "uuid_cache.properties");
        var cacheProperties = new Properties();
        if (IO.exists(cache)) {
            try (var is = FileUtils.openInputStream(cache)) {
                cacheProperties.load(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (cacheProperties.containsKey(username)) {
            return UUID.fromString(cacheProperties.getProperty(username));
        }
        UUID uuid = null;
        if (!isOffline) {
            try {
                var url = new URI(Meta.MOJANG_PLAYER_API + URLEncoder.encode(username, StandardCharsets.UTF_8));
                var json = IOUtils.toString(url, StandardCharsets.UTF_8);
                var root = readJson(json, JsonObject.class);
                JsonObject rootObj = root.getAsJsonObject();
                if (rootObj.has("id")) {
                    String encid = rootObj.get("id").getAsString();
                    String dashed = encid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                    uuid = UUID.fromString(dashed);
                    cacheProperties.setProperty(username, uuid.toString());
                    try (var os = FileUtils.openOutputStream(cache)) {
                        cacheProperties.store(os, "Mojang's Username => UUID Mapping");
                    }
                }
            } catch (IOException ignore) {
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        // Fallback if no cached UUID nor internet, this is wrong but at least deterministic
        return uuid == null ? UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)) : uuid;
    }

    private Types() { }

}
