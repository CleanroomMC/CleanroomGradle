package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.names.NamesSource;
import com.cleanroommc.gradle.api.util.EnumValues;
import de.siegmar.fastcsv.reader.CsvReader;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Derives a mapping file from the joined notch-to-srg mapping and
 * where the requested {@link Direction} requires it, the MCP method/field CSVs, using srgutils.
 */
@CacheableTask
public abstract class WriteMappings extends DefaultTask {

    public enum Direction {

        OBF_TO_SRG,
        SRG_TO_MCP,
        MCP_TO_SRG,
        MCP_TO_NOTCH;

    }

    private static void readCsv(File file, Map<String, String> names) {
        try (var reader = CsvReader.builder().ofNamedCsvRecord(file.toPath())) {
            for (var record : reader) {
                names.put(record.getField("searge"), record.getField("name"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read MCP mapping csv: " + file, e);
        }
    }

    private final Property<Direction> direction;
    private final Property<IMappingFile.Format> format;

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getJoinedSrgFile();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMethodMappings();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getFieldMappings();

    /**
     * Optional Tiny2 names source. When set, srg -> name lookups for the
     * {@code SRG_TO_MCP}/{@code MCP_TO_NOTCH} derivations come from it instead of the MCP CSVs.
     * The derived mapping files tracks custom names.
     */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getTinyMappings();

    /**
     * The active names identity string. Declared so a change of names source re-runs the derivation.
     */
    @Optional
    @Input
    public abstract Property<String> getNamesId();

    @Input
    public Property<Direction> getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction.set(EnumValues.parse(Direction.class, direction));
    }

    @Input
    public Property<IMappingFile.Format> getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format.set(EnumValues.parse(IMappingFile.Format.class, format));
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Inject
    public WriteMappings(ObjectFactory objects) {
        this.direction = objects.property(Direction.class);
        this.format = objects.property(IMappingFile.Format.class);
    }

    @TaskAction
    public void writeMappings() throws IOException {
        var direction = this.getDirection().get();

        var base = IMappingFile.load(this.getJoinedSrgFile().get().getAsFile());

        IMappingFile result;
        if (direction == Direction.OBF_TO_SRG) {
            result = base;
        } else {
            var names = new HashMap<String, String>();
            if (this.getTinyMappings().isPresent()) {
                // Tiny2 names source: only method and field members are needed for the renamer.
                var source = NamesSource.fromTiny2(this.getTinyMappings().get().getAsFile());
                names.putAll(source.methods());
                names.putAll(source.fields());
            } else {
                if (!this.getMethodMappings().isPresent() || !this.getFieldMappings().isPresent()) {
                    throw new InvalidUserDataException("Method and field mappings are required for direction " + direction + ".");
                }
                readCsv(this.getMethodMappings().get().getAsFile(), names);
                readCsv(this.getFieldMappings().get().getAsFile(), names);
            }

            var notchToMcp = base.rename(new IRenamer() {
                @Override
                public String rename(IMappingFile.IField field) {
                    return names.getOrDefault(field.getMapped(), field.getMapped());
                }

                @Override
                public String rename(IMappingFile.IMethod method) {
                    return names.getOrDefault(method.getMapped(), method.getMapped());
                }
            });

            result = switch (direction) {
                case SRG_TO_MCP -> base.reverse().chain(notchToMcp);
                case MCP_TO_SRG -> base.reverse().chain(notchToMcp).reverse();
                case MCP_TO_NOTCH -> notchToMcp.reverse();
                case OBF_TO_SRG -> throw new IllegalStateException("unreachable");
            };
        }

        var outputFile = this.getOutput().get().getAsFile();
        FileUtils.createParentDirectories(outputFile);
        result.write(outputFile.toPath(), this.getFormat().get(), false);
    }

}
