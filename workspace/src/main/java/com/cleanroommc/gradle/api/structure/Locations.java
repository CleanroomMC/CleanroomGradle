package com.cleanroommc.gradle.api.structure;

import org.gradle.api.Project;

import java.io.File;

public final class Locations {

    public static File file(File file, String... paths) {
        for (String path : paths) {
            file = new File(file, path);
        }
        return file;
    }

    public static File run(Project project) {
        return project.getLayout().getProjectDirectory().dir("run").getAsFile();
    }

    public static File global(Project project, String... paths) {
        return file(new File(project.getGradle().getGradleUserHomeDir(), "caches"), paths);
    }

    public static File temp(Project project, String... paths) {
        return file(new File(project.getLayout().getBuildDirectory().get().getAsFile(), "tmp"), paths);
    }

    public static File generated(Project project, String... paths) {
        return file(new File(project.getLayout().getBuildDirectory().get().getAsFile(), "generated"), paths);
    }

    private Locations() { }

}
