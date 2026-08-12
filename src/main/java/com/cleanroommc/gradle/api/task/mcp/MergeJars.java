package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.task.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

@CacheableTask
public abstract class MergeJars extends MavenJarExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getClientJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getServerJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSrgMappingFile();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Boolean> getInjectAnnotationMarkers();

    @OutputFile
    public abstract RegularFileProperty getMergedJar();

    public MergeJars() {
        this.getMainClass().convention("net.minecraftforge.mergetool.ConsoleMerger");
        this.getInjectAnnotationMarkers().convention(false);
    }

    @Override
    protected void beforeExec() {
        if (!this.getUseDefaultToolArguments().get()) {
            return;
        }
        this.args("--client", this.getClientJar(),
                "--server", this.getServerJar(),
                "--output", this.getMergedJar(),
                "--whitelist-map", this.getSrgMappingFile(),
                "-ann", this.getMinecraftVersion(),
                "--inject", this.getInjectAnnotationMarkers());
    }

}
