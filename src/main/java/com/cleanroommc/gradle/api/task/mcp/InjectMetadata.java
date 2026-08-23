package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.util.inject.MetadataInjector;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;

/**
 * Adds the metadata a decompiler needs to the SRG named jar.
 * <ul>
 *     <li>Visibilities from {@code access.txt}</li>
 *     <li>{@code throws} clauses from {@code exceptions.txt}</li>
 *     <li>Parameter names keyed off {@code constructors.txt}</li>
 * </ul>
 */
@CacheableTask
public abstract class InjectMetadata extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSrgJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getAccessFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConstructorsFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getExceptionsFile();

    @OutputFile
    public abstract RegularFileProperty getInjectedJar();

    @TaskAction
    public void inject() throws IOException {
        var result = MetadataInjector.inject(
                this.getSrgJar().get().getAsFile().toPath(),
                this.getInjectedJar().get().getAsFile().toPath(),
                this.getAccessFile().get().getAsFile().toPath(),
                this.getConstructorsFile().get().getAsFile().toPath(),
                this.getExceptionsFile().get().getAsFile().toPath()
        );
        this.getLogger().lifecycle("Injected metadata into {} classes, copied {} entries, recorded {} abstract methods",
                result.classesProcessed(), result.entriesCopied(), result.abstractMethodsRecorded());
    }

}
