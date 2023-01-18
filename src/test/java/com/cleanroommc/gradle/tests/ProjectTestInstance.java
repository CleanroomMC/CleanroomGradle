package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.extensions.MinecraftExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

import java.io.File;

import static com.cleanroommc.gradle.Constants.PROJECT_TEST_NAME;

public class ProjectTestInstance {

    private static final Project project;

    static {
        File projectDir = new File(".", "test/project/");
        new File(projectDir, "run").mkdirs();
        File homeDir = new File(".", "test/gradle_home/");
        project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .withGradleUserHomeDir(homeDir)
                .withName(PROJECT_TEST_NAME)
                .build();
        // Load
        project.getPluginManager().apply("com.cleanroommc.gradle");
        MinecraftExtension.get(project).setVersion("1.12.2");
    }

    public static Project getProject() {
        return project;
    }

}
