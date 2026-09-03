package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomToolTest extends BaseFunctionalTest {

    @Test
    void customMergeJarsReusesConfigurationCache() throws IOException {
        var sourceDir = this.projectDir.resolve("src/main/java/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("CustomMerge.java"), """
                package example;

                import java.nio.file.Files;
                import java.nio.file.Path;

                public final class CustomMerge {
                    public static void main(String[] args) throws Exception {
                        var output = Path.of(args[0]);
                        Files.createDirectories(output.getParent());
                        Files.writeString(output, args[1]);
                    }
                }
                """);
        this.project.vanilla("""
                import com.cleanroommc.gradle.api.task.mcp.MergeJars

                def customOutput = layout.buildDirectory.file('custom-merge.txt')
                tasks.register('customMerge', MergeJars) {
                    dependsOn tasks.named('classes')
                    toolClasspath.setFrom(sourceSets.main.output)
                    useDefaultToolArguments = false
                    mainClass = 'example.CustomMerge'
                    setArgs([customOutput.get().asFile.absolutePath, 'replacement-tool'])

                    clientJar = layout.projectDirectory.file('build.gradle')
                    serverJar = layout.projectDirectory.file('build.gradle')
                    minecraftVersion = 'replacement'
                    mergedJar = customOutput
                }
                """);

        var first = this.project.runner("customMerge").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":customMerge").getOutcome());
        assertEquals("replacement-tool", Files.readString(this.projectDir.resolve("build/custom-merge.txt")));

        Files.delete(this.projectDir.resolve("build/custom-merge.txt"));
        var second = this.project.runner("customMerge").build();
        PluginBuild.reused(second.getOutput());
        assertEquals(TaskOutcome.SUCCESS, second.task(":customMerge").getOutcome());
        assertEquals("replacement-tool", Files.readString(this.projectDir.resolve("build/custom-merge.txt")));
    }

}
