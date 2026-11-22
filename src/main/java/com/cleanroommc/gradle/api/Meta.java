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

import java.util.regex.Pattern;

public final class Meta {

    public static final String CG_FOLDER = "cleanroom_gradle";
    public static final String ONE_TRUE_MINECRAFT_VERSION = "1.12.2";
    public static final String MINECRAFT_PACKAGE_PATH = "net/minecraft/";

    // Useful URLs
    public static final String MOJANG_PLAYER_API = "https://api.mojang.com/users/profiles/minecraft/";

    // RegEx's
    public static final Pattern NATIVES_PATTERN = Pattern.compile(
        "^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$"
    );

    private Meta() { }

}
