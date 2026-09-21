/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.CleanroomInfo;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import com.cleanroommc.gradle.api.util.Environment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.gradle.testfixtures.ProjectBuilder;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.srgutils.IMappingFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StringEnumAssignmentTest {

    @TempDir
    Path projectDir;

    @Test
    void extensionAndTasksAcceptStringEnums() {
        var project = ProjectBuilder.builder().withProjectDir(this.projectDir.toFile()).build();
        var ext = project.getExtensions().create("cleanroom", CleanroomExtension.class);
        ext.setMode("loader");
        assertThat(ext.getMode().get()).isEqualTo(ProjectMode.LOADER);

        var run = project.getTasks().register("run", RunMinecraft.class).get();
        run.setSide("server");
        run.setEnv("reobf-srg");
        assertThat(run.getSide().get()).isEqualTo(Side.SERVER);
        assertThat(run.getEnv().get()).isEqualTo(Environment.REOBF_SRG);

        var strip = project.getTasks().register("strip", StripSideOnlyJar.class).get();
        strip.setTargetSide("client");
        assertThat(strip.getTargetSide().get()).isEqualTo(Side.CLIENT);

        var write = project.getTasks().register("write", WriteMappings.class).get();
        write.setDirection("mcp-to-srg");
        write.setFormat("tsrg");
        assertThat(write.getDirection().get()).isEqualTo(WriteMappings.Direction.MCP_TO_SRG);
        assertThat(write.getFormat().get()).isEqualTo(IMappingFile.Format.TSRG);

        var info = project.getTasks().register("info", CleanroomInfo.class).get();
        info.setMode("vanilla");
        assertThat(info.getMode().get()).isEqualTo(ProjectMode.VANILLA);
    }

}
