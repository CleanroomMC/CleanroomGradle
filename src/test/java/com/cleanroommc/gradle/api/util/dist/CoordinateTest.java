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

import org.gradle.api.GradleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinateTest {

    @Test
    void parsesFullAndMinimalNotations() {
        assertThat(Coordinate.parse("g:a:1")).isEqualTo(new Coordinate("g", "a", "1", null, "jar"));
        assertThat(Coordinate.parse("g:a:1:natives-linux")).isEqualTo(new Coordinate("g", "a", "1", "natives-linux", "jar"));
        assertThat(Coordinate.parse("g:a:1@zip")).isEqualTo(new Coordinate("g", "a", "1", null, "zip"));
        assertThat(Coordinate.parse("g:a:1:classifier@zip")).isEqualTo(new Coordinate("g", "a", "1", "classifier", "zip"));
    }

    @Test
    void blankClassifierMeansNoClassifier() {
        assertThat(Coordinate.parse("g:a:1:").classifier()).isNull();
    }

    @Test
    void rejectsMalformedCoordinates() {
        for (var bad : new String[] { "", "g", "g:a", "g:a:1:x:y", ":a:1", "g::1", "g:a: ", "g:a:1@", "g:a:1@zip@jar" }) {
            assertThatThrownBy(() -> Coordinate.parse(bad)).as(bad).isInstanceOf(GradleException.class);
        }
    }

    @Test
    void serializedRoundTripsAndDropsDefaultExtension() {
        assertThat(Coordinate.parse("g:a:1").serialized()).isEqualTo("g:a:1");
        assertThat(Coordinate.parse("g:a:1:classifier").serialized()).isEqualTo("g:a:1:classifier");
        assertThat(Coordinate.parse("g:a:1@zip").serialized()).isEqualTo("g:a:1@zip");
        assertThat(Coordinate.parse("g:a:1:classifier@zip").serialized()).isEqualTo("g:a:1:classifier@zip");
    }

    @Test
    void moduleAndWithoutClassifier() {
        var coordinate = Coordinate.parse("g:a:1:classifier@zip");
        assertThat(coordinate.module()).isEqualTo("g:a:1");
        assertThat(coordinate.withoutClassifier()).isEqualTo(Coordinate.parse("g:a:1@zip"));
    }

    @Test
    void sameArtifactComparesEveryComponent() {
        var base = Coordinate.parse("g:a:1");
        assertThat(base.sameArtifact(Coordinate.parse("g:a:1"))).isTrue();
        assertThat(base.sameArtifact(Coordinate.parse("g:a:2"))).isFalse();
        assertThat(base.sameArtifact(Coordinate.parse("g:a:1:natives-linux"))).isFalse();
        assertThat(base.sameArtifact(Coordinate.parse("g:a:1@zip"))).isFalse();
        assertThat(base.sameArtifact(Coordinate.parse("g:b:1"))).isFalse();
    }

    @Test
    void hasLocalComponentMatchesDotSeparatedSuffix() {
        assertThat(Coordinate.parse("g:a:1+local").hasLocalComponent()).isTrue();
        assertThat(Coordinate.parse("g:a:1+build.local.1").hasLocalComponent()).isTrue();
        assertThat(Coordinate.parse("g:a:1").hasLocalComponent()).isFalse();
        assertThat(Coordinate.parse("g:a:1+localbuild").hasLocalComponent()).isFalse();
        assertThat(Coordinate.parse("g:a:1+build.locals").hasLocalComponent()).isFalse();
    }

    @Test
    void mavenPathAndFileName() {
        var coordinate = Coordinate.parse("com.example:mod:1.0:natives-linux@ZIP");
        assertThat(coordinate.fileName()).isEqualTo("mod-1.0-natives-linux.zip");
        assertThat(coordinate.mavenPath()).isEqualTo("com/example/mod/1.0/mod-1.0-natives-linux.zip");
        assertThat(Coordinate.parse("com.example:mod:1.0").fileName()).isEqualTo("mod-1.0.jar");
    }

}
