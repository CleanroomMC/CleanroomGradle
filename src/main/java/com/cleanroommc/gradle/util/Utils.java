package com.cleanroommc.gradle.util;

import java.io.InputStream;

public final class Utils {

    public static InputStream getResource(String path) {
        return Utils.class.getResourceAsStream("/" + path);
    }

    private Utils() { }

}
