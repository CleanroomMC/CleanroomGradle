package com.cleanroommc.gradle.api.lazy;

import com.cleanroommc.gradle.api.os.Platform;
import org.gradle.api.Project;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.io.File;
import java.util.concurrent.Callable;

public final class Providers {

    public static <T> Property<T> property(Class<T> clazz, T convention) {
        return property(clazz).convention(convention);
    }

    public static <T> Property<T> property(Class<T> clazz) {
        return new DefaultProperty<>(PropertyHost.NO_OP, clazz);
    }

    public static <V> Provider<V> of(Callable<V> callable) {
        return new DefaultProvider<>(callable);
    }

    public static StringableProvider stringable(Object object) {
        return new StringableProvider(object);
    }

    public static Provider<String> libraryPath(Project project, Provider<File> moreLibraries) {
        return project.getProviders().systemProperty("java.library.path").zip(moreLibraries, (a, b) -> a + ';' + Platform.fixCommandLine(b.getAbsolutePath()));
    }

    public static Provider<JavaLauncher> javaLauncher(Project project, int api) {
        var ext = project.getExtensions();
        return ext.getByType(JavaToolchainService.class).launcherFor(spec -> {
            spec.getLanguageVersion().set(JavaLanguageVersion.of(api));
            spec.getVendor().set(JvmVendorSpec.ADOPTIUM);
            spec.getImplementation().set(JvmImplementation.VENDOR_SPECIFIC);
        });
    }

    public static Provider<JavaCompiler> javaCompiler(Project project, int api) {
        var ext = project.getExtensions();
        return ext.getByType(JavaToolchainService.class).compilerFor(spec -> {
            spec.getLanguageVersion().set(JavaLanguageVersion.of(api));
            spec.getVendor().set(JvmVendorSpec.ADOPTIUM);
            spec.getImplementation().set(JvmImplementation.VENDOR_SPECIFIC);
        });
    }

    private Providers() { }

}
