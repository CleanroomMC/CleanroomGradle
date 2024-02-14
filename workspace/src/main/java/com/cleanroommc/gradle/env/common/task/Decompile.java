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
        args("-nls=1", "-asc=1", "-iec=1", "-jvn=1", "-ind=    "); // TODO: make these settings configurable
        getJavaLauncher().set(Providers.javaLauncher(getProject(), 21));
        setup(true);
    }

    @Override
    protected void beforeExec() {
        getLogger().lifecycle("Using Java {} to decompile", getJavaLauncher().get().getMetadata().getLanguageVersion());
        for (var file : getLibraries().getFiles()) {
            args("-e=" + file.getAbsolutePath());
        }
        args(getCompiledJar(), getDecompiledJar());
    }

}
