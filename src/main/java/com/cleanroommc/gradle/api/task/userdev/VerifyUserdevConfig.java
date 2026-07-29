package com.cleanroommc.gradle.api.task.userdev;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Checks that the pipeline rebuilding the development environment matches the one the userdev artifact was built by.
 *
 * <p>The binpatches and the loader's classes are keyed against the SRG names of one specific MCP config.
 * A consumer resolving a different one produces a jar whose members silently do not line up.
 * Failing here with the two notations side by side is better than "cannot find symbol" spam later.</p>
 */
@CacheableTask
public abstract class VerifyUserdevConfig extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConfigFile();

    @Input
    public abstract Property<String> getMcpConfig();

    @Input
    public abstract Property<String> getMinecraftVersion();

    /** Stamped with the verified artifact's version so the check is skipped while nothing changes. */
    @OutputFile
    public abstract RegularFileProperty getStamp();

    @TaskAction
    public void verify() {
        var config = UserdevConfig.read(getConfigFile().getAsFile().get());
        if (config.spec() != UserdevConfig.SPEC) {
            throw new InvalidUserDataException(("The userdev artifact declares config spec %d, " +
                    "but this version of CleanroomGradle reads spec %d. Update the plugin.").formatted(config.spec(), UserdevConfig.SPEC));
        }
        if (!config.mcpConfig().equals(getMcpConfig().get())) {
            throw new InvalidUserDataException(
                    """
                    The userdev artifact was built against a different MCP config.
                      artifact: %s
                      project:  %s
                    Its binpatches and classes are keyed against the artifact's SRG names. Remove the mcpConfig dependency from the build script to take the pinned one."""
                            .formatted(config.mcpConfig(), getMcpConfig().get()));
        }
        if (!config.minecraftVersion().equals(getMinecraftVersion().get())) {
            throw new InvalidUserDataException("The userdev artifact targets Minecraft %s, but this project builds against %s."
                    .formatted(config.minecraftVersion(), getMinecraftVersion().get()));
        }
        var stamp = getStamp().getAsFile().get().toPath();
        try {
            var parent = stamp.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(stamp, config.cleanroomVersion() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + stamp, e);
        }
    }

}
