package com.cleanroommc.gradle.api.util.dist;

import java.nio.file.Path;

public record Artifact(Coordinate coordinate, Path path, String url) { }
