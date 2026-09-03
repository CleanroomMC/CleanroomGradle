package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateTest {

    @Test
    void parsesFullAndMinimalNotations() {
        assertEquals(new Coordinate("g", "a", "1", null, "jar"), Coordinate.parse("g:a:1"));
        assertEquals(new Coordinate("g", "a", "1", "natives-linux", "jar"),
                Coordinate.parse("g:a:1:natives-linux"));
        assertEquals(new Coordinate("g", "a", "1", null, "zip"), Coordinate.parse("g:a:1@zip"));
        assertEquals(new Coordinate("g", "a", "1", "classifier", "zip"),
                Coordinate.parse("g:a:1:classifier@zip"));
    }

    @Test
    void blankClassifierMeansNoClassifier() {
        assertNull(Coordinate.parse("g:a:1:").classifier());
    }

    @Test
    void rejectsMalformedCoordinates() {
        for (var bad : new String[] { "", "g", "g:a", "g:a:1:x:y", ":a:1", "g::1", "g:a: ",
                "g:a:1@", "g:a:1@zip@jar" }) {
            assertThrows(GradleException.class, () -> Coordinate.parse(bad), bad);
        }
    }

    @Test
    void serializedRoundTripsAndDropsDefaultExtension() {
        assertEquals("g:a:1", Coordinate.parse("g:a:1").serialized());
        assertEquals("g:a:1:classifier", Coordinate.parse("g:a:1:classifier").serialized());
        assertEquals("g:a:1@zip", Coordinate.parse("g:a:1@zip").serialized());
        assertEquals("g:a:1:classifier@zip", Coordinate.parse("g:a:1:classifier@zip").serialized());
    }

    @Test
    void moduleAndWithoutClassifier() {
        var coordinate = Coordinate.parse("g:a:1:classifier@zip");
        assertEquals("g:a:1", coordinate.module());
        assertEquals(Coordinate.parse("g:a:1@zip"), coordinate.withoutClassifier());
    }

    @Test
    void sameArtifactComparesEveryComponent() {
        var base = Coordinate.parse("g:a:1");
        assertTrue(base.sameArtifact(Coordinate.parse("g:a:1")));
        assertFalse(base.sameArtifact(Coordinate.parse("g:a:2")));
        assertFalse(base.sameArtifact(Coordinate.parse("g:a:1:natives-linux")));
        assertFalse(base.sameArtifact(Coordinate.parse("g:a:1@zip")));
        assertFalse(base.sameArtifact(Coordinate.parse("g:b:1")));
    }

    @Test
    void hasLocalComponentMatchesDotSeparatedSuffix() {
        assertTrue(Coordinate.parse("g:a:1+local").hasLocalComponent());
        assertTrue(Coordinate.parse("g:a:1+build.local.1").hasLocalComponent());
        assertFalse(Coordinate.parse("g:a:1").hasLocalComponent());
        assertFalse(Coordinate.parse("g:a:1+localbuild").hasLocalComponent());
        assertFalse(Coordinate.parse("g:a:1+build.locals").hasLocalComponent());
    }

    @Test
    void mavenPathAndFileName() {
        var coordinate = Coordinate.parse("com.example:mod:1.0:natives-linux@ZIP");
        assertEquals("mod-1.0-natives-linux.zip", coordinate.fileName());
        assertEquals("com/example/mod/1.0/mod-1.0-natives-linux.zip", coordinate.mavenPath());
        assertEquals("mod-1.0.jar", Coordinate.parse("com.example:mod:1.0").fileName());
    }

}
