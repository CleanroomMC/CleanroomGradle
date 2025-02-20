package com.cleanroommc.gradle.newapi.util;

import java.util.Locale;

public enum Environment {

    VANILLA,
    SRG,
    MCP,
    FORGE,
    CLEANROOM;

    @Override
    public String toString() {
        return this.name().toLowerCase(Locale.ENGLISH);
    }
}
