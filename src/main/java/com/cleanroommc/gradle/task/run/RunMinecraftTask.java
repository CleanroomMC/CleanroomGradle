package com.cleanroommc.gradle.task.run;

import com.cleanroommc.gradle.CleanroomMeta;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;

public abstract class RunMinecraftTask extends JavaExec {

    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract DirectoryProperty getRunDirectory();

    @Input
    public abstract Property<String> getUsername();

    @Input
    public abstract Property<String> getAccessToken();

    @Input
    public abstract ListProperty<String> getTweakClasses();

    public RunMinecraftTask() {
        getRunDirectory().convention(getProject().provider(() -> CleanroomMeta.getRelativeDirectory(getProject(), "run", getVersion().get())));



        getUsername().convention("Dev");
        getAccessToken().convention("0");

        setStandardInput(System.in);
        setStandardOutput(System.out);
        setErrorOutput(System.err);
    }

}
