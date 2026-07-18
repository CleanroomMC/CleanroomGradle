package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.task.MavenJarExec;
import com.cleanroommc.gradle.api.util.IO;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

@DisableCachingByDefault
public abstract class AccessTransform extends MavenJarExec {

    @Optional
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAccessTransformers();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    public AccessTransform() {
        // super("accessTransformer", "net.minecraftforge:accesstransformers:8.2.17");
        this.getLogFile().fileProvider(this.getProject().provider(this::getWorkingDir).map(dir -> new File(dir, "accesstransform.log")));
        this.getMainClass().set("net.minecraftforge.accesstransformer.TransformerProcessor");
        this.args("--inJar", this.getInputJar(),
                "--outJar", this.getOutputJar(),
                "--logFile", this.getLogFile().map(RegularFile::getAsFile).map(File::getName));
    }

    @Override
    protected void beforeExec() {
        for (var accessTransformer : this.getAccessTransformers()) {
            this.args("--atFile", accessTransformer.getAbsolutePath());
        }
        super.beforeExec();
    }

    @Override
    protected void afterExec() {
        IO.normalizeZip(this.getOutputJar().get().getAsFile().toPath());
        super.afterExec();
    }
}
