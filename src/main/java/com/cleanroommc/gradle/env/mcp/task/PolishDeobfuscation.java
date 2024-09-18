package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class PolishDeobfuscation extends MavenJarExec implements JarTransformer {

    @InputFile
    public abstract RegularFileProperty getDeobfuscatedJar();

    @InputFile
    public abstract RegularFileProperty getExceptionsFile();

    @InputFile
    public abstract RegularFileProperty getAccessFile();

    @InputFile
    public abstract RegularFileProperty getConstructorsFile();

    @OutputFile
    public abstract RegularFileProperty getPolishedJar();

    public PolishDeobfuscation() {
        super("polishDeobfuscation", "de.oceanlabs.mcp:mcinjector:3.7.3");
        this.getMainClass().set("de.oceanlabs.mcp.mcinjector.MCInjector");
        this.args("--in", this.getDeobfuscatedJar(),
                "--out", this.getPolishedJar(),
                "--lvt=LVT",
                "--exc", this.getExceptionsFile(),
                "--acc", this.getAccessFile(),
                "--ctr", this.getConstructorsFile());
        this.setup(true);
    }

}
