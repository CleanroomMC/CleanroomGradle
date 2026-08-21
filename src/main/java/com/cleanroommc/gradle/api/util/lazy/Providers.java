package com.cleanroommc.gradle.api.util.lazy;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

public final class Providers {

    public static Provider<JavaLauncher> javaLauncher(Project project, int api) {
        return javaLauncher(project.getExtensions().getByType(JavaToolchainService.class), api);
    }

    public static Provider<JavaLauncher> javaLauncher(JavaToolchainService toolchains, int api) {
        return toolchains.launcherFor(spec -> {
            spec.getLanguageVersion().set(JavaLanguageVersion.of(api));
            spec.getVendor().set(JvmVendorSpec.ADOPTIUM);
            spec.getImplementation().set(JvmImplementation.VENDOR_SPECIFIC);
        });
    }

    private Providers() { }

}
