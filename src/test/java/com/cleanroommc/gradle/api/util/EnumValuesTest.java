/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;

import org.junit.jupiter.api.Test;

import org.gradle.api.InvalidUserDataException;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.srgutils.IMappingFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class EnumValuesTest {

    @Test
    void parsesIgnoreCaseHyphensAndPadding() {
        assertThat(EnumValues.parse(ProjectMode.class, "userdev")).isEqualTo(ProjectMode.USERDEV);
        assertThat(EnumValues.parse(ProjectMode.class, " Loader ")).isEqualTo(ProjectMode.LOADER);
        assertThat(EnumValues.parse(Environment.class, "reobf-srg")).isEqualTo(Environment.REOBF_SRG);
        assertThat(EnumValues.parse(Side.class, "client")).isEqualTo(Side.CLIENT);
        assertThat(EnumValues.parse(WriteMappings.Direction.class, "mcp-to-srg")).isEqualTo(WriteMappings.Direction.MCP_TO_SRG);
        assertThat(EnumValues.parse(IMappingFile.Format.class, "tsrg")).isEqualTo(IMappingFile.Format.TSRG);
    }

    @Test
    void unknownValueListsConstants() {
        var error = catchThrowableOfType(() -> EnumValues.parse(ProjectMode.class, "nope"), InvalidUserDataException.class);
        assertThat(error).hasMessageContaining("Unknown ProjectMode 'nope'");
        assertThat(error).hasMessageContaining("VANILLA, LOADER, USERDEV");
    }

    @Test
    void blankValueListsConstants() {
        var error = catchThrowableOfType(() -> EnumValues.parse(ProjectMode.class, "  "), InvalidUserDataException.class);
        assertThat(error).hasMessageContaining("Missing ProjectMode");
        assertThat(error).hasMessageContaining("VANILLA, LOADER, USERDEV");
    }

}
