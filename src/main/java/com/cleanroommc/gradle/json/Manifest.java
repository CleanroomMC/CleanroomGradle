package com.cleanroommc.gradle.json;

import javax.annotation.Nullable;
import java.net.URL;

public class Manifest {

    public VersionInfo[] versions;

    @Nullable
    public URL getUrl(@Nullable String version) {
        if (version == null) {
            return null;
        }
        for (VersionInfo info : versions) {
            if (version.equals(info.id)) {
                return info.url;
            }
        }
        return null;
    }

    public static class VersionInfo {
        public String id;
        public URL url;
    }

}
