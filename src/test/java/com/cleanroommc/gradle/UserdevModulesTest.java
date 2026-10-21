/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle;

import org.junit.jupiter.api.Test;

import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

import java.io.IOException;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class UserdevModulesTest extends BaseFunctionalTest {

    @Test
    void toolingImportSeesOneCombinedModuleAndSourcesWithoutSetup() throws IOException {
        this.project.seedUserdevModule("0.7.0-alpha");
        this.project.build(
            UserdevFixture.PREAMBLE +
                """
                apply plugin: 'idea'
                dependencies {
                    implementation cleanroom.userdev('0.7.0-alpha')
                }
                """
        );

        var model = this.project.ideaModel(
            "--offline", "-Pcg.repos.enableLocal=true", "-Pcg.userdev.sources=true",
            "-Dmaven.repo.local=" + this.projectDir.resolve("local-maven")
        );
        var dependencies = model.value()
            .getModules()
            .stream()
            .flatMap(module -> module.getDependencies().stream())
            .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
            .map(IdeaSingleEntryLibraryDependency.class::cast)
            .filter(entry -> entry.getGradleModuleVersion() != null &&
                "cleanroom-userdev".equals(entry.getGradleModuleVersion().getName()))
            .toList();
        // The classes transform runs during the sync itself, so what the IDE lists is its two jars
        assertThat(dependencies)
            .as(model::output)
            .extracting(entry -> entry.getFile().getName())
            .containsExactlyInAnyOrder("minecraft-patched.jar", "cleanroom-userdev.jar");
        for (var dependency : dependencies) {
            assertThat(dependency.getGradleModuleVersion().getGroup()).isEqualTo("com.cleanroommc");
            assertThat(dependency.getGradleModuleVersion().getVersion()).isEqualTo("0.7.0-alpha");
            assertThat(dependency.getSource()).as(model::output).isNotNull();
            try (var sources = new ZipFile(dependency.getSource())) {
                assertThat(sources.getEntry("net/minecraft/Block.java")).isNotNull();
                assertThat(sources.getEntry("com/cleanroommc/Loader.java")).isNotNull();
            }
        }
        assertThat(model.output()).as(model::output).doesNotContain(":setup");
    }

}
