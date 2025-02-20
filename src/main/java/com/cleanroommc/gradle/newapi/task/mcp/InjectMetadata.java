package com.cleanroommc.gradle.newapi.task.mcp;

import com.cleanroommc.gradle.newapi.task.JarProcessingTypeTask;
import com.cleanroommc.gradle.newapi.task.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class InjectMetadata extends MavenJarExec implements JarProcessingTypeTask {

    @InputFile
    public abstract RegularFileProperty getSrgJar();

    @InputFile
    public abstract RegularFileProperty getAccessFile();

    @InputFile
    public abstract RegularFileProperty getConstructorsFile();

    @InputFile
    public abstract RegularFileProperty getExceptionsFile();

    @OutputFile
    public abstract RegularFileProperty getInjectedJar();

    public InjectMetadata() {
        super("mcinjector", "de.oceanlabs.mcp:mcinjector:3.7.3");
        this.getMainClass().set("de.oceanlabs.mcp.mcinjector.MCInjector");
        this.args("--in", this.getSrgJar(),
                "--out", this.getInjectedJar(),
                "--lvt=LVT",
                "--acc", this.getAccessFile(),
                "--ctr", this.getConstructorsFile(),
                "--exc", this.getExceptionsFile());
    }

}
