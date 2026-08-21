package com.cleanroommc.gradle.api.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

import java.util.List;

/**
 * Wires the {@value #CONFIGURATION_NAME} configuration, usage is as follows:
 *
 * <pre>{@code
 * dependencies {
 *     lwjglNative 'org.lwjgl:lwjgl'
 *     lwjglNative 'org.lwjgl:lwjgl-opengl'
 * }
 * }</pre>
 *
 * <p>LWJGL ships one native jar per platform. But the published metadata has to serve every distribution.
 * Runtime classpath takes {@link Platform#lwjglNativesClassifier()}.
 * While publications take every classifier in {@code cleanroom.loader.lwjglNativesClassifiers}.
 */
public final class LwjglNatives {

    public static final String CONFIGURATION_NAME = "lwjglNative";
    public static final String CURRENT_CONFIGURATION_NAME = "lwjglNativeCurrent";
    public static final String ALL_CONFIGURATION_NAME = "lwjglNativeAll";
    /**
     * Every platform LWJGL 3 publishes natives for.
     * Listing them all is what lets a workspace be developed and run on any of them.
     * The publication includes everything, and each machine resolves their own classifier.
     *
     * <p>Complete as of LWJGL <b>3.3.6</b>, which is when ppc64le, riscv64 and freebsd were first published.
     */
    public static final List<String> CLASSIFIERS = List.of(
            "natives-windows",
            "natives-windows-x86",
            "natives-windows-arm64",
            "natives-linux",
            "natives-linux-arm32",
            "natives-linux-arm64",
            "natives-linux-ppc64le",
            "natives-linux-riscv64",
            "natives-macos",
            "natives-macos-arm64",
            "natives-freebsd"
    );

    private static final String CLASSIFIER_PREFIX = "natives-";

    public static void register(Project project, ListProperty<String> classifiers) {
        var configurations = project.getConfigurations();
        var factory = project.getDependencyFactory();
        var declared = configurations.register(CONFIGURATION_NAME, config -> {
            config.setDescription("LWJGL modules that need natives. Use without declaring the classifiers");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
        });
        var current = configurations.register(CURRENT_CONFIGURATION_NAME, config -> {
            config.setDescription("LWJGL natives for the current platform");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.withDependencies(dependencies -> create(factory, dependencies, declared.get(), List.of(Platform.CURRENT.lwjglNativesClassifier())));
        });
        var all = configurations.register(ALL_CONFIGURATION_NAME, config -> {
            config.setDescription("LWJGL natives for every platform");
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.withDependencies(dependencies -> create(factory, dependencies, declared.get(), classifiers.get()));
        });
        project.getPlugins().withType(JavaPlugin.class, $ -> {
            configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).configure(config -> config.extendsFrom(current.get()));
            configurations.named(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME).configure(config -> config.extendsFrom(all.get()));
        });
    }

    public static Provider<List<String>> publishedCoordinates(Project project, ListProperty<String> classifiers) {
        return project.getConfigurations().named(CONFIGURATION_NAME).map(config -> config.getAllDependencies().stream()
                .filter(dependency -> dependency.getGroup() != null)
                .flatMap(dependency -> classifiers.get().stream()
                        .map(classifier -> dependency.getGroup() + ":" + dependency.getName() + ":" + classifier))
                .toList()
        );
    }

    public static boolean isForCurrentPlatform(String notation) {
        var parts = notation.split(":");
        if (parts.length < 4) {
            return true;
        }
        var classifier = parts[3];
        return !classifier.startsWith(CLASSIFIER_PREFIX) || classifier.equals(Platform.CURRENT.lwjglNativesClassifier());
    }

    private static void create(DependencyFactory factory, DependencySet target, Configuration declared, List<String> classifiers) {
        for (var dependency : declared.getAllDependencies()) {
            var version = dependency.getVersion() == null ? "" : dependency.getVersion();
            for (var classifier : classifiers) {
                var notation = "%s:%s:%s:%s".formatted(dependency.getGroup(), dependency.getName(), version, classifier);
                var created = factory.create(notation);
                created.setTransitive(false);
                target.add(created);
            }
        }
    }

    private LwjglNatives() { }

}
