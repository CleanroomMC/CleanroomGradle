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

import org.junit.jupiter.api.Test;

import org.gradle.api.InvalidUserDataException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumValuesTest {

    @Test
    void parsesDslSpellings() {
        assertThat(EnumValues.parse(ProjectMode.class, " Loader ")).isEqualTo(ProjectMode.LOADER);
        assertThat(EnumValues.parse(Environment.class, "reobf-srg")).isEqualTo(Environment.REOBF_SRG);
    }

    @Test
    void unknownValueListsConstants() {
        assertThatThrownBy(() -> EnumValues.parse(ProjectMode.class, "nope"))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("Unknown ProjectMode 'nope'")
                .hasMessageContaining("VANILLA, LOADER, USERDEV");
    }

}
