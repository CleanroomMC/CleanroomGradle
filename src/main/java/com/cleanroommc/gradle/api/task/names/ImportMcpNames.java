package com.cleanroommc.gradle.api.task.names;

import com.cleanroommc.gradle.api.names.CsvNames;
import com.cleanroommc.gradle.api.names.JarStructure;
import com.cleanroommc.gradle.api.names.TinyV2;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the public MCP names CSVs (plus {@code constructors.txt} from mcp_config) into a Tiny2 file (SRG -> named).
 * Using the SRG jar for accurate descriptors. Writes {@code <namesDirectory>/mappings.tiny} once.
 * The resulting Tiny2 is then editable and becomes the pipeline's names source.
 */
@DisableCachingByDefault(because = "One time generator writing into a user source directory, not worth caching.")
public abstract class ImportMcpNames extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSrgJar();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getMcpNames();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConstructorsFile();

    /** Whether {@code cleanroom.namesDirectory} was configured; validated for a clear error message. */
    @Input
    public abstract Property<Boolean> getNamesDirectoryConfigured();

    @OutputFile
    public abstract RegularFileProperty getTinyFile();

    @TaskAction
    public void importNames() {
        if (!getNamesDirectoryConfigured().getOrElse(false)) {
            throw new InvalidUserDataException(
                    "cleanroom.namesDirectory must be set before running importMcpNames "
                            + "(it determines where mappings.tiny is written).");
        }

        var namesZip = getMcpNames().getFiles().stream()
                .filter(f -> f.getName().endsWith(".zip"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No MCP names zip found: " + getMcpNames().getFiles()));

        var structure = JarStructure.scan(getSrgJar().getAsFile().get());
        var names = CsvNames.fromZip(namesZip);
        var constructors = readConstructors();
        var tiny = TinyV2.write(structure, names, constructors);

        var output = getTinyFile().getAsFile().get();
        try {
            FileUtils.createParentDirectories(output);
            FileUtils.write(output, tiny, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Tiny2 mappings " + output, e);
        }
        getLogger().lifecycle("Tiny2 mappings written: {} classes -> {}", structure.classes().size(), output);
    }

    /** Parses {@code constructors.txt} ({@code <id> <class> <desc>}) grouped by class internal name. */
    private Map<String, List<TinyV2.Constructor>> readConstructors() {
        var byClass = new HashMap<String, List<TinyV2.Constructor>>();
        try {
            for (var line : Files.readAllLines(getConstructorsFile().getAsFile().get().toPath(), StandardCharsets.UTF_8)) {
                var parts = line.trim().split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                byClass.computeIfAbsent(parts[1], k -> new ArrayList<>()).add(new TinyV2.Constructor("i" + parts[0], parts[2]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read constructors.txt", e);
        }
        return byClass;
    }

}
