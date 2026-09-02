package com.cleanroommc.gradle.api.deobf;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;

/**
 * Exposes the mapping and SRG hierarchy packaged in a userdev artifact as inputs to the mod transform.
 */
@CacheableTransform
public abstract class ExtractUserdevDeobfInputs implements TransformAction<TransformParameters.None> {

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var layout = UserdevConfig.readFromJar(input).layout();
        var output = outputs.dir("userdev-deobf-inputs");
        try (var zip = new ZipFile(input)) {
            extract(zip, input, layout.srgToMcp(), UserdevConfig.SRG2MCP, output);
            extract(zip, input, layout.deobfLibrary(), UserdevConfig.DEOBF_LIBRARY, output);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not extract deobf inputs from " + input, e);
        }
    }

    private static void extract(ZipFile zip, File input, String entryName, String name, File output) throws IOException {
        var entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new InvalidUserDataException(input + " does not contain " + entryName
                    + ". Use a userdev artifact produced by a CleanroomGradle version that supports native deobf().");
        }
        try (var stream = zip.getInputStream(entry)) {
            Files.copy(stream, output.toPath().resolve(name));
        }
    }

}
