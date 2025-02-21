package com.cleanroommc.gradle.newapi.task.mcp;

import com.cleanroommc.gradle.newapi.task.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

@CacheableTask
public abstract class InjectMetadata extends MavenJarExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSrgJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getAccessFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConstructorsFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
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
