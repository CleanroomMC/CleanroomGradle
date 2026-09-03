package com.cleanroommc.gradle.api.util;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

import java.util.Collection;
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
 * While publications take every classifier in {@code cleanroom.loader.lwjglNativesClassifiers}, one
 * OS and architecture specific variant each, so a consumer resolves only its own.
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
    public static final List<String> CLASSIFIERS = Platform.lwjglNativesClassifiers();

    public static final String CLASSIFIER_PREFIX = "natives-";

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
        // runtimeElements deliberately stays free of natives
        project.getPlugins().withType(JavaPlugin.class, $ -> configurations
                .named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .configure(config -> config.extendsFrom(current.get())));
    }

    public static void addFor(Project project, Collection<Dependency> target, String classifier) {
        create(project.getDependencyFactory(), target,
                project.getConfigurations().getByName(CONFIGURATION_NAME), List.of(classifier));
    }

    private static void create(DependencyFactory factory, Collection<Dependency> target, Configuration declared, List<String> classifiers) {
        for (var dependency : declared.getAllDependencies()) {
            if (dependency.getVersion() == null || dependency.getVersion().isBlank()) {
                throw new InvalidUserDataException(dependency.getGroup() + ":" + dependency.getName()
                        + " is declared in " + CONFIGURATION_NAME + " without a version. Native classifiers are"
                        + " published one variant each, and a platform does not travel with them.");
            }
            var version = dependency.getVersion();
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
