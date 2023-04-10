package com.cleanroommc.gradle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;

public final class CleanroomMeta {

    // Useful URLs
    public static final String LIBRARIES_BASE_URL = "https://libraries.minecraft.net/";
    public static final String RESOURCES_BASE_URL = "https://resources.download.minecraft.net/";
    public static final String VERSION_MANIFESTS_V2_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    // Useful Repositories
    public static final String CLEANROOM_REPOSITORY = "https://maven.cleanroommc.com/";
    public static final String FABRIC_REPOSITORY = "https://maven.fabricmc.net/";
    public static final String FORGE_REPOSITORY = "https://maven.minecraftforge.net/";

    // Global GSON Instance
    public static final Gson GSON;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.enableComplexMapKeySerialization().setPrettyPrinting();
        GSON = builder.create();
    }

    // TODO
    public static Directory getRelativeDirectory(Project project, String... paths) {
        Directory directory = project.getLayout().getProjectDirectory();
        for (String path : paths) {
            directory = directory.dir(path);
        }
        return directory;
    }

    private CleanroomMeta() {
    }

}
