package com.cleanroommc.gradle.env.common;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import org.gradle.api.Project;

import java.io.IOException;

public final class Preparation {

    public static void initialize(Project project) {
        var manifestLocation = Locations.global(project, "version_manifest_v2.json");
        if (!IO.exists(manifestLocation)) {
            try {
                var result = IO.download(project, Meta.VERSION_MANIFEST_V2_URL, manifestLocation, dl -> {
                    dl.overwrite(false);
                    dl.onlyIfModified(true);
                    dl.onlyIfNewer(true);
                    dl.useETag(true);
                });
                result.join();
            } catch (IOException e) {
                throw new RuntimeException("Unable to download version manifest!", e);
            }
        }
    }

    private Preparation() { }

}
