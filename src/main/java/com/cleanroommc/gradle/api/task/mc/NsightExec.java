package com.cleanroommc.gradle.api.task.mc;

import com.cleanroommc.gradle.api.util.Platform;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import java.util.List;

/**
 * Launches NVIDIA Nsight Graphics ({@code ngfx}) wrapping a fresh Gradle wrapper invocation of the
 * {@link #getRunTaskName() run task}.
 * <p><b>Only supported on Windows and Linux</b></p>.
 */
@DisableCachingByDefault(because = "Launches an external profiler")
public abstract class NsightExec extends Exec {

    @Input
    @Optional
    public abstract Property<String> getActivity();

    @Input
    @Optional
    public abstract Property<String> getNgfxPath();

    @Input
    public abstract Property<String> getRunTaskName();

    @Internal
    public abstract Property<String> getJavaExecutable();

    @Internal
    public abstract RegularFileProperty getGradleWrapperJar();

    public NsightExec() {
        this.doFirst(task -> this.configureCommandLine());
    }

    private void configureCommandLine() {
        var os = Platform.CURRENT.getOperatingSystem();
        if (!os.isWindows() && !os.isLinux()) {
            throw new GradleException(this.getName() + " is only supported on Windows and Linux");
        }
        var platform = os.isWindows() ? "Windows" : "Linux";

        if (!this.getActivity().isPresent() || this.getActivity().get().isEmpty()) {
            throw new InvalidUserDataException("activity must be provided for this task.");
        }
        if (!this.getNgfxPath().isPresent() || this.getNgfxPath().get().isEmpty()) {
            throw new InvalidUserDataException("ngfxPath must be provided for this task.");
        }

        var activity = this.getActivity().get();
        var ngfxPath = this.getNgfxPath().get();
        var javaExecutable = this.getJavaExecutable().get();
        var wrapperJar = this.getGradleWrapperJar().get().getAsFile().getAbsolutePath();
        var workingDir = this.getWorkingDir().getAbsolutePath();

        var logger = this.getLogger();
        logger.lifecycle("\nJava Executable: " + javaExecutable);
        logger.lifecycle("Nsight Graphics NGFX Path: " + ngfxPath + "\n");

        var wrapperArgs = String.join(" ", List.of("-classpath", wrapperJar, "org.gradle.wrapper.GradleWrapperMain", this.getRunTaskName().get()));

        this.commandLine(
                ngfxPath,
                "--activity", activity,
                "--platform", platform,
                "--wait-hotkey",
                "--dir", workingDir,
                "--output-dir", workingDir,
                "--exe", javaExecutable,
                "--args", wrapperArgs
        );
    }

}
