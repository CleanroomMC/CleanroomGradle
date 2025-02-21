package com.cleanroommc.gradle.newapi.task.mcp;

import com.cleanroommc.gradle.newapi.task.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

@CacheableTask
public abstract class RemapNotch2Srg extends MavenJarExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getNotchJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSrgMappingFile();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getAccessTransformerFile();

    @OutputFile
    public abstract RegularFileProperty getSrgJar();

    public RemapNotch2Srg() {
        super("specialSource", "net.md-5:SpecialSource:1.11.3");
        this.getMainClass().set("net.md_5.specialsource.SpecialSource");
        this.args("--in-jar", this.getNotchJar(),
                "--out-jar", this.getSrgJar(),
                "--srg-in", this.getSrgMappingFile(),
                "--kill-source");
    }

    @Override
    protected void beforeExec() {
        if (this.getAccessTransformerFile().isPresent() && this.getAccessTransformerFile().get().getAsFile().exists()) {
            this.args("--access-transformer", this.getAccessTransformerFile());
        }
    }

}
