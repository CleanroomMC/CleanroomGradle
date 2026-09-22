/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.dist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.gradle.api.GradleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinateTest {

    @Test
    void parsesAndSerializesEveryNotation() {
        assertThat(Coordinate.parse("g:a:1")).isEqualTo(new Coordinate("g", "a", "1", null, "jar"));
        assertThat(Coordinate.parse("g:a:1:")).isEqualTo(new Coordinate("g", "a", "1", null, "jar"));
        assertThat(Coordinate.parse("g:a:1:c@zip")).isEqualTo(new Coordinate("g", "a", "1", "c", "zip"));
        for (var notation : new String[] { "g:a:1", "g:a:1:c", "g:a:1@zip", "g:a:1:c@zip" }) {
            assertThat(Coordinate.parse(notation).serialized()).isEqualTo(notation);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "g", "g:a", "g:a:1:x:y", ":a:1", "g::1", "g:a: ", "g:a:1@", "g:a:1@zip@jar" })
    void rejectsMalformedNotation(String notation) {
        assertThatThrownBy(() -> Coordinate.parse(notation)).isInstanceOf(GradleException.class);
    }

    @Test
    void mavenPathLowersTheExtension() {
        assertThat(Coordinate.parse("com.example:mod:1.0:natives-linux@ZIP").mavenPath()).isEqualTo("com/example/mod/1.0/mod-1.0-natives-linux.zip");
    }

}
