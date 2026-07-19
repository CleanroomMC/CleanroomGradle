package com.cleanroommc.gradle.api.task.sas;

import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/** Produces one physical-side jar by removing mismatched legacy {@code @SideOnly} bytecode. */
@CacheableTask
public abstract class StripSideOnlyJar extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @Input
    public abstract Property<Side> getTargetSide();

    @Input
    public abstract Property<Boolean> getValidateReferences();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    public StripSideOnlyJar() {
        this.getValidateReferences().convention(true);
    }

    @TaskAction
    public void strip() throws IOException {
        var side = this.getTargetSide().get();
        var result = SideOnlyHandler.strip(
                this.getInputJar().get().getAsFile().toPath(),
                this.getOutputJar().get().getAsFile().toPath(),
                side,
                this.getValidateReferences().get());
        this.getLogger().lifecycle(
                "Built {} jar: removed {} classes, {} fields, {} methods. Cleared {} @SideOnly annotations",
                side.name().toLowerCase(), result.classesRemoved(), result.fieldsRemoved(),
                result.methodsRemoved(), result.annotationsRemoved());
    }
}
