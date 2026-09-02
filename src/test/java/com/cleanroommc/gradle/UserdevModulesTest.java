package com.cleanroommc.gradle;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserdevModulesTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void toolingImportSeesOneCombinedModuleAndSourcesWithoutSetup() throws IOException {
        this.project.seedUserdevModule("0.7.0");
        this.project.build(UserdevFixture.PREAMBLE + """
                apply plugin: 'idea'
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                """);

        var output = new ByteArrayOutputStream();
        try (var connection = GradleConnector.newConnector()
                .useInstallation(new File(System.getProperty("test.gradle.home")))
                .useGradleUserHomeDir(new File(System.getProperty("testkit.gradle.user.home")))
                .forProjectDirectory(this.projectDir.toFile())
                .connect()) {
            var model = connection.model(IdeaProject.class)
                    .withArguments("--offline", "-Pcg.repos.enableLocal=true",
                            "-Dmaven.repo.local=" + this.projectDir.resolve("local-maven"),
                            "--configuration-cache", "--configuration-cache-problems=fail")
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .get();

            var dependency = model.getModules().stream()
                    .flatMap(module -> module.getDependencies().stream())
                    .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
                    .map(IdeaSingleEntryLibraryDependency.class::cast)
                    .filter(entry -> entry.getGradleModuleVersion() != null
                            && "cleanroom-userdev".equals(entry.getGradleModuleVersion().getName()))
                    .findFirst().orElseThrow(() -> new AssertionError(output.toString()));
            assertEquals("com.cleanroommc", dependency.getGradleModuleVersion().getGroup());
            assertEquals("0.7.0", dependency.getGradleModuleVersion().getVersion());
            // The classes transform runs during the sync itself, so what the IDE lists is the combined jar
            assertTrue(dependency.getFile().getName().endsWith("materialized.jar"),
                    () -> dependency.getFile() + "\n" + output);
            assertNotNull(dependency.getSource(), output::toString);
            try (var sources = new ZipFile(dependency.getSource())) {
                assertNotNull(sources.getEntry("net/minecraft/Block.java"));
                assertNotNull(sources.getEntry("com/cleanroommc/Loader.java"));
            }
            assertTrue(!output.toString().contains(":setup"), output::toString);

        }
    }

}
