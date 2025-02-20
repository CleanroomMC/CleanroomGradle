package com.cleanroommc.gradle.newapi.util;

import com.cleanroommc.gradle.newapi.Meta;
import com.cleanroommc.gradle.newapi.ext.CleanroomExtension;
import com.google.gson.JsonObject;
import kotlin.jvm.functions.Function0;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Objects {

    public static <T> T extension(Project project, String name, Class<T> extensionClass, Object... args) {
        return project.getExtensions().create(name, extensionClass, args);
    }

    public static <T> T extension(Project project, Class<T> extensionClass) {
        return project.getExtensions().getByType(extensionClass);
    }

    public static NamedDomainObjectProvider<Configuration> config(Project project, String name) {
        return project.getConfigurations().register(name);
    }

    public static Configuration resolvedConfig(Project project, String name) {
        return project.getConfigurations().getByName(name);
    }

    public static NamedDomainObjectProvider<SourceSet> sourceSet(Project project, String name) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().register(name);
    }

    public static ModuleDependency dependency(Project project, NamedDomainObjectProvider<Configuration> configuration, String notation) {
        return (ModuleDependency) project.getDependencies().add(configuration.getName(), notation);
    }

    public static Set<File> artifacts(NamedDomainObjectProvider<Configuration> configuration) {
        return configuration.get().resolve();
    }

    public static File artifact(NamedDomainObjectProvider<Configuration> configuration) {
        Set<File> artifacts = artifacts(configuration);
        if (artifacts.size() > 1) {
            throw new RuntimeException("There are more than 1 artifact in this configuration.");
        }
        return artifacts.stream().findFirst().get();
    }

    public static File artifact(NamedDomainObjectProvider<Configuration> configuration, String dependencyName) {
        return configuration.get().getResolvedConfiguration().getResolvedArtifacts()
                .stream()
                .filter(artifact -> artifact.getName().equals(dependencyName))
                .findFirst()
                .orElseThrow().getFile();
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

    public static UUID resolveUuid(Project project, CleanroomExtension cleanroomExt, String username) {
        boolean isOffline = project.getGradle().getStartParameter().isOffline();
        var cache = cleanroomExt.getCacheDirectory().file("uuid_cache.properties").get().getAsFile();
        var cacheProperties = new Properties();
        if (cache.exists()) {
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
                var root = IO.readJson(json, JsonObject.class);
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

    private Objects() { }

}
