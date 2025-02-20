package com.cleanroommc.gradle.newapi.task.common;

import com.cleanroommc.gradle.newapi.task.JarProcessingTypeTask;
import com.cleanroommc.gradle.newapi.task.MavenJarExec;
import com.cleanroommc.gradle.newapi.util.lazy.Providers;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

public abstract class Decompile extends MavenJarExec implements JarProcessingTypeTask {

    @InputFile
    public abstract RegularFileProperty getCompiledJar();

    @Classpath
    @InputFiles
    @Optional
    public abstract ConfigurableFileCollection getLibraries();

    @OutputFile
    public abstract RegularFileProperty getDecompiledJar();

    public Decompile() {
        super("vineflower", "org.vineflower:vineflower:1.10.1+cleanroom");
        // TODO: make these settings configurable
        // The default for -nls is OS-dependent for some reason
        // this.args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-ind=    ");
        this.getJavaLauncher().convention(Providers.javaLauncher(this.getProject(), 21));
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
