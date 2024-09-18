package com.cleanroommc.gradle.env.common.task;

import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

public abstract class Decompile extends MavenJarExec implements JarTransformer {

    @InputFile
    public abstract RegularFileProperty getCompiledJar();

    @Classpath
    @InputFiles
    @Optional
    public abstract ConfigurableFileCollection getLibraries();

    @OutputFile
    public abstract RegularFileProperty getDecompiledJar();

    public Decompile() {
        super("decompile", "org.vineflower:vineflower:1.9.3");
        // The default for -nls is OS-dependent for some reason
        this.args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-ind=    "); // TODO: make these settings configurable
        this.getJavaLauncher().set(Providers.javaLauncher(this.getProject(), 21));
        this.setup(true);
    }

    @Override
    protected void beforeExec() {
        this.getLogger().lifecycle("Using Java {} to decompile", this.getJavaLauncher().get().getMetadata().getLanguageVersion());
        for (var file : getLibraries().getFiles()) {
            this.args("-e=" + file.getAbsolutePath());
        }
        this.args(getCompiledJar(), getDecompiledJar());
    }

}
