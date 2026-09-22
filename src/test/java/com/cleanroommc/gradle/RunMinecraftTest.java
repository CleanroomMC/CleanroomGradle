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
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunMinecraftTest extends BaseFunctionalTest {

    @Test
    void stoppedGamesSucceedAndCrashedGamesFail() throws IOException {
        var stopped = new ArrayList<>(List.of(0, 130, 143));
        if (OS.WINDOWS.isCurrentOs()) {
            // STATUS_CONTROL_C_EXIT, only Windows hands the full 32-bit NTSTATUS back as the exit code
            stopped.add(-1073741510);
        }
        var crashed = List.of(1, 137);
        var source = this.projectDir.resolve("src/exiter/java/exiter/Exiter.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                """
                package exiter;
                public final class Exiter {
                    public static void main(String[] args) {
                        System.exit(Integer.parseInt(args[args.length - 1]));
                    }
                }
                """
        );
        this.project.vanilla(
                """
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft
                sourceSets { exiter }
                def exiterJar = tasks.register('exiterJar', Jar) {
                    from sourceSets.exiter.output
                    archiveClassifier = 'exiter'
                }
                (%s + %s).each { code ->
                    tasks.register('runExit' + code, RunMinecraft) {
                        side = 'client'
                        env = 'mcp'
                        minecraftVersion = '1.12.2'
                        assetIndexVersion = '1.12'
                        getUUID().set('00000000-0000-0000-0000-000000000000')
                        vanillaAssetsLocation = layout.buildDirectory.dir('assets')
                        mainClass = 'exiter.Exiter'
                        classpath = files(exiterJar)
                        args code.toString()
                    }
                }
                """.formatted(
                        stopped,
                        crashed
                )
        );

        this.project.runner(stopped.stream().map(code -> "runExit" + code).toArray(String[]::new)).build();

        var arguments = new ArrayList<>(crashed.stream().map(code -> "runExit" + code).toList());
        arguments.add("--continue");
        var failure = this.project.runner(arguments.toArray(String[]::new)).buildAndFail().getOutput();
        for (var code : crashed) {
            assertThat(failure).as(failure).contains("Minecraft crashed (exit code " + code + ")");
        }
    }

}
