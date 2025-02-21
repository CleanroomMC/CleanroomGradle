package com.cleanroommc.gradle.api.util.lazy;

import com.cleanroommc.gradle.api.util.Platform;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.io.File;

public final class Providers {

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
