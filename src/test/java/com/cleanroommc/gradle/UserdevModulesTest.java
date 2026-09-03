package com.cleanroommc.gradle;

import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserdevModulesTest extends BaseFunctionalTest {

    @Test
    void toolingImportSeesOneCombinedModuleAndSourcesWithoutSetup() throws IOException {
        this.project.seedUserdevModule("0.7.0");
        this.project.build(UserdevFixture.PREAMBLE + """
                apply plugin: 'idea'
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                """);

        var model = this.project.ideaModel("--offline", "-Pcg.repos.enableLocal=true",
                "-Dmaven.repo.local=" + this.projectDir.resolve("local-maven"));
        var dependency = model.value().getModules().stream()
                .flatMap(module -> module.getDependencies().stream())
                .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
                .map(IdeaSingleEntryLibraryDependency.class::cast)
                .filter(entry -> entry.getGradleModuleVersion() != null
                        && "cleanroom-userdev".equals(entry.getGradleModuleVersion().getName()))
                .findFirst().orElseThrow(() -> new AssertionError(model.output()));
        assertEquals("com.cleanroommc", dependency.getGradleModuleVersion().getGroup());
        assertEquals("0.7.0", dependency.getGradleModuleVersion().getVersion());
        // The classes transform runs during the sync itself, so what the IDE lists is the combined jar
        assertTrue(dependency.getFile().getName().endsWith("materialized.jar"),
                () -> dependency.getFile() + "\n" + model.output());
        assertNotNull(dependency.getSource(), model::output);
        try (var sources = new ZipFile(dependency.getSource())) {
            assertNotNull(sources.getEntry("net/minecraft/Block.java"));
            assertNotNull(sources.getEntry("com/cleanroommc/Loader.java"));
        }
        assertTrue(!model.output().contains(":setup"), model::output);
    }

}
