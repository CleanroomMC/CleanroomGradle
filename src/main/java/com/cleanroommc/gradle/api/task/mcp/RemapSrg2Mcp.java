package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.names.NamesSource;
import com.cleanroommc.gradle.api.names.SourceRenamer;
import de.siegmar.fastcsv.reader.CsvReader;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

@CacheableTask
public abstract class RemapSrg2Mcp extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSrgSource();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getFieldMappings();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMethodMappings();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getParameterMappings();

    /**
     * Optional Tiny2 names source. When set (see {@code cleanroom.namesDirectory}),
     * the srg -> name lookups and javadocs come from it instead of the MCP CSVs.
     * Allowing custom edited names drive the workspace. When unset, the CSV path is used unchanged.
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getTinyMappings();

    /** The active names identity string. Declared so a change of names source re-runs the remap. */
    @Optional
    @Input
    public abstract Property<String> getNamesId();

    @OutputDirectory
    public abstract DirectoryProperty getMcpSource();


    @TaskAction
    public void remapSrg2Mcp() {
        var names = new HashMap<String, String>();
        var docs = new HashMap<String, String>();

        if (this.getTinyMappings().isPresent()) {
            // Tiny2 names source: methods, fields and params merged into the same lookup the CSV path uses.
            var source = NamesSource.fromTiny2(this.getTinyMappings().get().getAsFile());
            names.putAll(source.flatNames());
            docs.putAll(source.docs());
        } else {
            var methodMappings = this.getMethodMappings().getFiles();
            if (methodMappings.isEmpty()) {
                throw new InvalidUserDataException("No method mappings were provided.");
            }
            var fieldMappings = this.getFieldMappings().getFiles();
            if (fieldMappings.isEmpty()) {
                throw new InvalidUserDataException("No field mappings were provided.");
            }
            var parameterMappings = this.getParameterMappings().getFiles();
            if (parameterMappings.isEmpty()) {
                throw new InvalidUserDataException("No parameter mappings were provided.");
            }

            var files = new HashSet<>(methodMappings);
            files.addAll(fieldMappings);
            files.addAll(parameterMappings);

            for (var file : files) {
                try (var reader = CsvReader.builder().ofNamedCsvRecord(file.toPath())) {
                    for (var record : reader) {
                        var searge = record.getField(record.getHeader().contains("searge") ? "searge" : "param");
                        names.put(searge, record.getField("name"));
                        if (record.getHeader().contains("desc")) {
                            var desc = record.getField("desc");
                            if (!desc.isEmpty()) {
                                docs.put(searge, desc);
                            }
                        }
                    }
                } catch (Throwable t) {
                    throw new RuntimeException("Unexpected error", t);
                }
            }
        }

        var mcpSourceDir = this.getMcpSource().get().getAsFile();

        this.getSrgSource().getAsFileTree().visit(fvd -> {
            if (!fvd.isDirectory()) {
                var file = fvd.getFile();
                if (!file.getName().endsWith(".java")) {
                    this.getLogger().lifecycle("Skipping over {} when remapping.", file);
                } else {
                    var relativePath = fvd.getRelativePath().getPathString();
                    var newFile = new File(mcpSourceDir, relativePath);
                    try {
                        var data = FileUtils.readLines(file, StandardCharsets.UTF_8);
                        if (data.isEmpty()) {
                            this.getLogger().lifecycle("Skipping over {} when remapping. As file is empty.", file);
                        } else {
                            FileUtils.writeLines(newFile, StandardCharsets.UTF_8.name(), SourceRenamer.rename(data, names, docs), null, false);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error", t);
                    }
                }
            }
        });
    }

}
