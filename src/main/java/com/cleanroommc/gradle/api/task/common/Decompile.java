package com.cleanroommc.gradle.api.task.common;

import com.cleanroommc.gradle.api.task.MavenJarExec;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.File;

@CacheableTask
public abstract class Decompile extends MavenJarExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCompiledJar();

    @Optional
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @OutputFile
    public abstract RegularFileProperty getDecompiledJar();

    public Decompile() {
        super("vineflower", "org.vineflower:vineflower:1.11.0+cleanroom");
        // TODO: make these settings configurable
        // The default for -nls is OS-dependent for some reason
        // this.args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-ind=    ");
        this.getJavaLauncher().convention(Providers.javaLauncher(this.getProject(), 21));
        this.getLogFile().fileProvider(this.getProject().provider(this::getWorkingDir).map(dir -> new File(dir, "decompile.log")));
    }

    @Override
    protected void beforeExec() {
        this.getLogger().lifecycle("Using Java {} to decompile", this.getJavaLauncher().get().getMetadata().getLanguageVersion());
        for (var file : this.getLibraries().getFiles()) {
            this.args("-e=" + file.getAbsolutePath());
        }
        this.args(this.getCompiledJar(), this.getDecompiledJar());
    }

}
