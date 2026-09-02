package com.cleanroommc.gradle.api.task.userdev;

import com.cleanroommc.gradle.api.userdev.UserdevSourceMaterializer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;

@CacheableTask
public abstract class MaterializeUserdevSourcesJar extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUserdevArtifact();

    @CompileClasspath
    public abstract ConfigurableFileCollection getLibraries();

    @Classpath
    public abstract ConfigurableFileCollection getDecompilerClasspath();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void materialize() {
        try {
            UserdevSourceMaterializer.materialize(getUserdevArtifact().getAsFile().get(),
                    getOutput().getAsFile().get().toPath(), getTemporaryDir().toPath(), getLibraries(),
                    getDecompilerClasspath(), getExecOperations());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize " + getUserdevArtifact().getAsFile().get(), e);
        }
    }

}
