package com.cleanroommc.gradle.api.util;

import java.util.Locale;

public enum Environment {

    VANILLA,
    SRG,
    REOBF_SRG,
    MCP,
    CLEANROOM;

    @Override
    public String toString() {
        return this.name().toLowerCase(Locale.ENGLISH);
    }
}
