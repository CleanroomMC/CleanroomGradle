package com.cleanroommc.gradle.task.rename;

import org.cadixdev.vignette.VignetteMain;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

// TODO
public abstract class JarRenamingTask extends DefaultTask {

    @TaskAction
    public void rename() {
        VignetteMain.main(
                new String[] {
                        "--jar-in", getInputJar().getAsFile().get().getAbsolutePath(),
                        "--jar-out", getOutputJar().getAsFile().get().getAbsolutePath(),
                        "--mapping-format", "tsrg2",
                        "--mappings", getMapping().getAsFile().get().getAbsolutePath(),
                        "--fernflower-meta"
                }
        );
    }

    @InputFile
    public abstract RegularFileProperty getMapping();

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

}
