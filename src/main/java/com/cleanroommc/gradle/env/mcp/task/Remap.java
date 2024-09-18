package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.structure.IO;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecordHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Remap extends DefaultTask implements JarTransformer {

    private static final Pattern SRG_DEFINITION = Pattern.compile("[fF]unc_\\d+_[a-zA-Z_]+|m_\\d+_|[fF]ield_\\d+_[a-zA-Z_]+|f_\\d+_|p_\\w+_\\d+_|p_\\d+_");
    // private static final Pattern FIELD_DEFINITION = Pattern.compile("^((?: {4})+|\\t+)(?:[\\w$.\\[\\]]+ )+(field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");
    // private static final Pattern METHOD_DEFINITION = Pattern.compile("^((?: {4})+|\\t+)(?:[\\w$.\\[\\]]+ )+([0-9a-zA-Z_]+)\\(");
    // private static final Pattern CONSTRUCTOR_DEFINITION = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(public |private|protected |)(?<generic><[\\w\\W]*>\\s+)?(?<name>[\\w.]+)\\((?<parameters>.*)\\)\\s+(?:throws[\\w.,\\s]+)?\\{");

    @InputFile
    public abstract RegularFileProperty getSrgJar();

    @InputFile
    public abstract RegularFileProperty getFieldMappings();

    @InputFile
    public abstract RegularFileProperty getMethodMappings();

    @InputFile
    public abstract RegularFileProperty getParameterMappings();

    @OutputFile
    public abstract RegularFileProperty getRemappedJar();

    // @Input
    // public abstract Property<Boolean> getAddJavadocs();

    // @Input
    // public abstract Property<Integer> getJavadocWrapLength();

    public Remap() {
        // getAddJavadocs().convention(true);
        // getJavadocWrapLength().convention(160);
        // this.allowTaskSkip();
        this.setup(true);
    }

    @TaskAction
    public void remap() throws IOException {
        Map<String, Mapping> mappings = new HashMap<>();
        this.processMappings(mappings);
        this.rename(mappings);
    }

    private void processMappings(Map<String, Mapping> mappings) throws IOException {
        var fieldMappingPath = getFieldMappings().get().getAsFile().toPath();
        var methodMappingPath = getMethodMappings().get().getAsFile().toPath();
        var parameterMappingPath = getParameterMappings().get().getAsFile().toPath();

        String searge, name, desc;
        Side side;
        try (var reader = CsvReader.builder().build(new NamedCsvRecordHandler("searge", "name", "side", "desc"), fieldMappingPath)) {
            for (var record : reader) {
                searge = record.getField("searge");
                name = record.getField("name");
                side = switch (record.getField("side")) {
                    case "0" -> Side.CLIENT;
                    case "1" -> Side.SERVER;
                    default -> null; // Both
                };
                desc = record.getField("desc");
                mappings.put(searge, new MemberMapping(searge, name, side, desc));
            }
        }
        try (var reader = CsvReader.builder().build(new NamedCsvRecordHandler("searge", "name", "side", "desc"), methodMappingPath)) {
            for (var record : reader) {
                searge = record.getField("searge");
                name = record.getField("name");
                side = switch (record.getField("side")) {
                    case "0" -> Side.CLIENT;
                    case "1" -> Side.SERVER;
                    default -> null; // Both
                };
                desc = record.getField("desc");
                mappings.put(searge, new MemberMapping(searge, name, side, desc));
            }
        }
        String param;
        try (var reader = CsvReader.builder().build(new NamedCsvRecordHandler("param", "name", "side"), parameterMappingPath)) {
            for (var record : reader) {
                param = record.getField("param");
                name = record.getField("name");
                side = switch (record.getField("side")) {
                    case "0" -> Side.CLIENT;
                    case "1" -> Side.SERVER;
                    default -> null; // Both
                };
                mappings.put(param, new ParameterMapping(param, name, side));
            }
        }
    }

    private void rename(Map<String, Mapping> mappings) {
        var inputPath = getSrgJar().get().getAsFile().toPath();
        var outputPath = getRemappedJar().get().getAsFile().toPath();
        var srgMatcher = SRG_DEFINITION.matcher("");

        IO.transformZipFiles(inputPath, outputPath, lines -> {
            var newLines = new ArrayList<String>(lines.size());
            var builder = new StringBuilder();
            for (var line : lines) {
                srgMatcher.reset(line);
                while (srgMatcher.find()) {
                    var matched = srgMatcher.group();
                    var replacement = mappings.get(matched);
                    if (replacement != null) {
                        srgMatcher.appendReplacement(builder, Matcher.quoteReplacement(replacement.name()));
                    }
                }
                if (!builder.isEmpty()) {
                    srgMatcher.appendTail(builder);
                    newLines.add(builder.toString());
                    builder.setLength(0);
                } else {
                    newLines.add(line);
                }
            }
            return newLines;
        });
    }

    interface Mapping {

        String name();

    }

    record MemberMapping(String searge, String name, Side side, String desc) implements Mapping { }

    record ParameterMapping(String param, String name, Side side) implements Mapping { }

}
