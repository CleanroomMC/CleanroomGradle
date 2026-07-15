package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.task.MavenJarExec;
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
        this.getMainClass().convention("de.oceanlabs.mcp.mcinjector.MCInjector");
    }

    @Override
    protected void beforeExec() {
        if (!this.getUseDefaultToolArguments().get()) {
            return;
        }
        this.args("--in", this.getSrgJar(),
                "--out", this.getInjectedJar(),
                "--lvt=LVT",
                "--acc", this.getAccessFile(),
                "--ctr", this.getConstructorsFile(),
                "--exc", this.getExceptionsFile());
    }

}
