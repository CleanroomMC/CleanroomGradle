package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.Meta;
import com.google.gson.JsonObject;
import kotlin.jvm.functions.Function0;
import org.apache.commons.io.FileUtils;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

public final class Objects {

    public static <T> T extension(Project project, String name, Class<T> extensionClass, Object... args) {
        return project.getExtensions().create(name, extensionClass, args);
    }

    public static NamedDomainObjectProvider<Configuration> config(Project project, String name, String description) {
        var provider = project.getConfigurations().register(name);
        provider.configure(config -> {
            config.setDescription(description);
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
        });
        return provider;
    }

    public static NamedDomainObjectProvider<Configuration> archive(Project project, String name, String description, String defaultNotation) {
        var provider = config(project, name, description);
        var factory = project.getDependencyFactory();
        provider.configure(config -> {
            config.setTransitive(false);
            config.defaultDependencies(deps -> deps.add(factory.create(defaultNotation)));
        });
        return provider;
    }

    public static Dependency firstDependency(Configuration configuration) {
        var dependencies = configuration.getAllDependencies();
        if (dependencies.isEmpty()) {
            configuration.getIncoming().getDependencies();
            dependencies = configuration.getAllDependencies();
        }
        if (dependencies.isEmpty()) {
            throw new IllegalStateException("Configuration '" + configuration.getName() + "' has no dependencies.");
        }
        return dependencies.iterator().next();
    }

    public static String notation(Configuration configuration) {
        var dependency = firstDependency(configuration);
        var version = dependency.getVersion();
        if (version == null) {
            throw new IllegalStateException("Dependency '" + dependency + "' of configuration '" + configuration.getName() + "' has no version.");
        }
        return dependency.getGroup() + ":" + dependency.getName() + ":" + version;
    }

    /**
     * Like {@link #notation(Configuration)}, but keeps the classifier and extension the dependency declares.
     * A coordinate such as {@code de.oceanlabs.mcp:mcp_stable:39-1.12@zip} resolves to a different file
     * without them, so anything republished for another build to resolve has to carry them.
     */
    public static String fullNotation(Configuration configuration) {
        var notation = notation(configuration);
        if (!(firstDependency(configuration) instanceof ModuleDependency module) || module.getArtifacts().isEmpty()) {
            return notation;
        }
        var artifact = module.getArtifacts().iterator().next();
        var classifier = artifact.getClassifier() == null ? "" : ":" + artifact.getClassifier();
        var extension = artifact.getExtension() == null ? "" : "@" + artifact.getExtension();
        return notation + classifier + extension;
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

    public static UUID resolveUuid(boolean isOffline, File cache, String username) {
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
                var request = HttpRequest.newBuilder(url).GET().build();
                var json = IO.httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
                var root = IO.readJson(json, JsonObject.class);
                if (root != null && root.has("id")) {
                    String encid = root.get("id").getAsString();
                    String dashed = encid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                    uuid = UUID.fromString(dashed);
                    cacheProperties.setProperty(username, uuid.toString());
                    try (var os = FileUtils.openOutputStream(cache)) {
                        cacheProperties.store(os, "Mojang's Username => UUID Mapping");
                    }
                }
            } catch (IOException ignore) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        // Fallback if no cached UUID nor internet, this is wrong but at least deterministic
        return uuid == null ? UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)) : uuid;
    }

    private Objects() { }

}
