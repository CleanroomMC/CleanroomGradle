package com.cleanroommc.gradle.api.task.sas;

import com.cleanroommc.gradle.api.util.EnumValues;
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Produces one physical-side jar by removing mismatched legacy {@code @SideOnly} bytecode.
 */
@CacheableTask
public abstract class StripSideOnlyJar extends DefaultTask {

    // Held by hand: Gradle will not generate a managed property alongside a setter of another type
    private final Property<Side> targetSide;

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @Input
    public Property<Side> getTargetSide() {
        return targetSide;
    }

    public void setTargetSide(String targetSide) {
        this.targetSide.set(EnumValues.parse(Side.class, targetSide));
    }

    @Input
    public abstract Property<Boolean> getValidateReferences();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Inject
    public StripSideOnlyJar(ObjectFactory objects) {
        this.targetSide = objects.property(Side.class);
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
