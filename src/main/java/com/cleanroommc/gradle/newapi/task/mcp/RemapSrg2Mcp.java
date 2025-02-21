package com.cleanroommc.gradle.newapi.task.mcp;

import de.siegmar.fastcsv.reader.CsvReader;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Pretty much ForgeGradle net/minecraftforge/gradle/common/util/McpNames
public abstract class RemapSrg2Mcp extends DefaultTask {

    private static final Pattern SRG_FINDER = Pattern.compile("[fF]unc_\\d+_[a-zA-Z_]+|m_\\d+_|[fF]ield_\\d+_[a-zA-Z_]+|f_\\d+_|p_\\w+_\\d+_|p_\\d+_");
    private static final Pattern CONSTRUCTOR_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(public |private|protected |)(?<generic><[\\w\\W]*>\\s+)?(?<name>[\\w.]+)\\((?<parameters>.*)\\)\\s+(?:throws[\\w.,\\s]+)?\\{");
    private static final Pattern METHOD_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*(?<generic><[\\w\\W]*>\\s+)?(?<return>\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>(?:func_|m_)[0-9]+_[a-zA-Z_]*)\\(");
    private static final Pattern FIELD_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*\\s+(?<name>(?:field_|f_)[0-9]+_[a-zA-Z_]*) *[=;]");
    private static final Pattern CLASS_JAVADOC_PATTERN = Pattern.compile("^(?<indent> *|\\t*)([\\w|@]*\\s)*(class|interface|@interface|enum) (?<name>[\\w]+)");
    private static final Pattern CLOSING_CURLY_BRACE = Pattern.compile("^(?<indent> *|\\t*)}");
    private static final Pattern PACKAGE_DECL = Pattern.compile("^[\\s]*package(\\s)*(?<name>[\\w|.]+);$");
    private static final Pattern LAMBDA_DECL = Pattern.compile("\\((?<args>(?:(?:, ){0,1}p_[\\w]+_\\d+_\\b)+)\\) ->");

    /**
     * Injects javadoc into the given list of lines, if the given line is a method or field declaration.
     * @param docs mapping of members => javadoc strings
     * @param lines The current file content, will be modified in-place
     * @param line Currently-read line (will not be in the list)
     * @param _package the name of the package this file is declared to be in, in com.example format;
     * @param innerClasses current position in inner class
     */
    private static boolean injectJavadoc(Map<String, String> docs, List<String> lines, String line, String _package, Deque<Map.Entry<String, Integer>> innerClasses) {
        // Constructors
        var matcher = CONSTRUCTOR_JAVADOC_PATTERN.matcher(line);
        var isConstructor = matcher.find() && !innerClasses.isEmpty() && innerClasses.peek().getKey().contains(matcher.group("name"));
        // Methods
        if (!isConstructor) {
            matcher = METHOD_JAVADOC_PATTERN.matcher(line);
        }
        if (isConstructor || matcher.find()) {
            var name = isConstructor ? "<init>" : matcher.group("name");
            var javadoc = docs.get(name);
            if (javadoc == null && !innerClasses.isEmpty() && !name.startsWith("func_")) {
                String currentClass = innerClasses.peek().getKey();
                javadoc = docs.get(currentClass + '#' + name);
            }
            if (javadoc != null) {
                insertAboveAnnotations(lines, buildJavadoc(matcher.group("indent"), javadoc, true));
            }
            return true;
        }

        // Fields
        matcher = FIELD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            var name = matcher.group("name");
            var javadoc = docs.get(name);
            if (javadoc == null && !innerClasses.isEmpty() && !name.startsWith("field_")) {
                var currentClass = innerClasses.peek().getKey();
                javadoc = docs.get(currentClass + '#' + name);
            }
            if (javadoc != null) {
                insertAboveAnnotations(lines, buildJavadoc(matcher.group("indent"), javadoc, false));
            }
            return true;
        }

        // Classes
        matcher = CLASS_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            // Maintain a stack of the current (inner) class in com.example.ClassName$Inner format (along with indentation)
            // If the stack is not empty, a new inner class is entered
            var currentClass = (innerClasses.isEmpty() ? _package : innerClasses.peek().getKey() + "$") + matcher.group("name");
            innerClasses.push(Map.entry(currentClass, matcher.group("indent").length()));
            var javadoc = docs.get(currentClass);
            if (javadoc != null) {
                insertAboveAnnotations(lines, buildJavadoc(matcher.group("indent"), javadoc, true));
            }
            return true;
        }

        // Detect curly braces for inner class stacking/end identification
        matcher = CLOSING_CURLY_BRACE.matcher(line);
        if (matcher.find()){
            if (!innerClasses.isEmpty()) {
                int len = matcher.group("indent").length();
                if (len == innerClasses.peek().getValue()) {
                    innerClasses.pop();
                } else if (len < innerClasses.peek().getValue()) {
                    System.err.println("Failed to properly track class blocks around class " + innerClasses.peek().getKey() + ":" + (lines.size() + 1));
                    return false;
                }
            }
        }
        return true;
    }

    /** Inserts the given javadoc line into the list of lines before any annotations */
    private static void insertAboveAnnotations(List<String> list, String line) {
        int back = 0;
        while (list.get(list.size() - 1 - back).trim().startsWith("@")) {
            back++;
        }
        list.add(list.size() - back, line);
    }

    // TODO: formatting/beautifying after instead of in this method
    private static String buildJavadoc(String indent, String javadoc, boolean multiLine) {
        return indent + "/** " + javadoc + " */";
    }

    /*
     * There are certain times, such as Mixin Accessors that we wish to have the name of this method with the first character upper case.
     */
    private static String getMapped(Map<String, String> names, String srg, /*@Nullable*/ Set<String> blacklist) {
        if (blacklist != null && blacklist.contains(srg)) {
            return srg;
        }
        boolean cap = srg.charAt(0) == 'F';
        if (cap) {
            srg = 'f' + srg.substring(1);
        }
        String ret = names.getOrDefault(srg, srg);
        if (cap) {
            ret = ret.substring(0, 1).toUpperCase(Locale.ENGLISH) + ret.substring(1);
        }
        return ret;
    }

    private static String replaceInLine(Map<String, String> names, String line, /*@Nullable*/ Set<String> blacklist) {
        var buf = new StringBuilder();
        var matcher = SRG_FINDER.matcher(line);
        while (matcher.find()) {
            // Since '$' is a valid character in identifiers
            // We need to NOT treat this as a regex group, escape any occurrences
            matcher.appendReplacement(buf, Matcher.quoteReplacement(getMapped(names, matcher.group(), blacklist)));
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    @Inject
    public abstract FileOperations getFileOperations();

    @InputDirectory
    public abstract DirectoryProperty getSrgSource();

    @InputFiles
    public abstract ConfigurableFileCollection getFieldMappings();

    @InputFiles
    public abstract ConfigurableFileCollection getMethodMappings();

    @InputFiles
    public abstract ConfigurableFileCollection getParameterMappings();

    @OutputDirectory
    public abstract DirectoryProperty getMcpSource();


    @TaskAction
    public void remapSrg2Mcp() {
        var names = new HashMap<String, String>();
        var docs = new HashMap<String, String>();

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

        var mcpSourceDir = this.getMcpSource().get().getAsFile();

        this.getFileOperations().fileTree(this.getSrgSource()).visit(fvd -> {
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
                            var newData = new ArrayList<String>();

                            var innerClasses = new LinkedList<Map.Entry<String, Integer>>(); // Pair of inner class name & indentation
                            var _package = ""; // Default Package
                            Set<String> blacklist = null;

                            for (var line : data) {
                                var m = PACKAGE_DECL.matcher(line);
                                if (m.find()) {
                                    _package = m.group("name") + ".";
                                }
                                injectJavadoc(docs, newData, line, _package, innerClasses);
                                newData.add(replaceInLine(names, line, blacklist));
                            }

                            FileUtils.writeLines(newFile, StandardCharsets.UTF_8.name(), newData, null, false);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error", t);
                    }
                }
            }
        });
    }

}
