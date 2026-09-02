package com.cleanroommc.gradle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quitting the game has to leave the build green, crashing has to fail it.
 */
class RunMinecraftTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
        var source = this.projectDir.resolve("src/exiter/java/exiter");
        Files.createDirectories(source);
        Files.writeString(source.resolve("Exiter.java"), """
                package exiter;

                public final class Exiter {

                    public static void main(String[] args) {
                        System.exit(Integer.parseInt(args[args.length - 1]));
                    }

                }
                """);
    }

    private void runTask(String name, int exitCode) throws IOException {
        this.project.vanilla("""
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft

                sourceSets { exiter }
                def exiterJar = tasks.register('exiterJar', Jar) {
                    from sourceSets.exiter.output
                    archiveClassifier = 'exiter'
                }
                tasks.register('%s', RunMinecraft) {
                    side = 'client'
                    env = 'mcp'
                    minecraftVersion = '1.12.2'
                    assetIndexVersion = '1.12'
                    getUUID().set('00000000-0000-0000-0000-000000000000')
                    vanillaAssetsLocation = layout.buildDirectory.dir('assets')
                    mainClass = 'exiter.Exiter'
                    classpath = files(exiterJar)
                    args '%d'
                }
                """.formatted(name, exitCode));
    }

    @Test
    void quittingSucceeds() throws IOException {
        runTask("runExit", 0);
        this.project.runner("runExit").build();
    }

    @Test
    void beingStoppedBySignalSucceeds() throws IOException {
        // 128 + SIGINT, what a user pressing stop leaves behind
        runTask("runExit", 130);
        this.project.runner("runExit").build();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void beingStoppedWithControlCOnWindowsSucceeds() throws IOException {
        // STATUS_CONTROL_C_EXIT; only Windows hands the full 32-bit NTSTATUS back as the exit code
        runTask("runExit", -1073741510);
        this.project.runner("runExit").build();
    }

    @Test
    void beingKilledFails() throws IOException {
        // 128 + SIGKILL, what an out-of-memory killer leaves behind
        runTask("runExit", 137);
        var failure = this.project.runner("runExit").buildAndFail().getOutput();
        assertTrue(failure.contains("Minecraft crashed (exit code 137)"), failure);
    }

    @Test
    void crashingFails() throws IOException {
        runTask("runExit", 1);
        var failure = this.project.runner("runExit").buildAndFail().getOutput();
        assertTrue(failure.contains("Minecraft crashed (exit code 1)"), failure);
    }

}
