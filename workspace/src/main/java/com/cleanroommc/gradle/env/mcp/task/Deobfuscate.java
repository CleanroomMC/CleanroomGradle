package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

public abstract class Deobfuscate extends MavenJarExec implements JarTransformer {

    @InputFile
    public abstract RegularFileProperty getObfuscatedJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    @InputFile
    @Optional
    public abstract RegularFileProperty getAccessTransformerFile();

    @OutputFile
    public abstract RegularFileProperty getDeobfuscatedJar();

    public Deobfuscate() {
        super("deobfuscate", "net.md-5:SpecialSource:1.11.3");
        this.getMainClass().set("net.md_5.specialsource.SpecialSource");
        this.args("--in-jar", getObfuscatedJar(),
                "--out-jar", getDeobfuscatedJar(),
                "--srg-in", getSrgMappingFile(),
                "--kill-source");
        this.setup(true);
    }

    @Override
    protected void beforeExec() {
        if (this.getAccessTransformerFile().isPresent() && this.getAccessTransformerFile().get().getAsFile().exists()) {
            this.args("--access-transformer", this.getAccessTransformerFile());
        }
    }

}
