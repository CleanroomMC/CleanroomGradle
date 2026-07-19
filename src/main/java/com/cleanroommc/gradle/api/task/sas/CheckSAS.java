package com.cleanroommc.gradle.api.task.sas;

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler;
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler.SasLine;
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler.Target;
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler.TargetKind;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Strictly validates legacy SAS roots, expands annotated overrides and writes deterministic build data.
 * Source SAS files remain untouched.
 */
@CacheableTask
public abstract class CheckSAS extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInheritance();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void check() throws IOException {
        Map<String, JsonObject> inheritance;
        try (var reader = IO.reader(this.getInheritance().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
            Map<String, JsonObject> parsed = new Gson().fromJson(reader, new TypeToken<Map<String, JsonObject>>() { }.getType());
            inheritance = parsed == null ? Map.of() : parsed;
        }

        var sourceFiles = this.getSideAnnotationStrippers().getFiles().stream().map(File::toPath).toList();
        var roots = new TreeMap<Target, SasLine>();
        var duplicates = new TreeSet<Target>();
        for (var line : SideOnlyHandler.readSas(sourceFiles)) {
            if (!line.generated() && roots.putIfAbsent(line.target(), line) != null) {
                duplicates.add(line.target());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new GradleException("Duplicate legacy side annotation stripper roots:\n  "
                    + String.join("\n  ", duplicates.stream().map(Target::format).toList()));
        }
        validateAndWrite(inheritance, roots);
    }

    private void validateAndWrite(Map<String, JsonObject> inheritance, Map<Target, SasLine> roots) throws IOException {
        var classRoots = new HashSet<String>();
        roots.keySet().stream()
                .filter(target -> target.kind() == TargetKind.CLASS)
                .map(Target::owner)
                .forEach(classRoots::add);
        var errors = new TreeSet<String>();
        var expanded = new HashMap<Target, Set<Target>>();
        for (var line : roots.values()) {
            var generated = validateRoot(inheritance, classRoots, errors, line.target());
            generated.remove(line.target());
            expanded.put(line.target(), generated);
        }
        finishValidation(roots, errors, expanded);
    }

    private static Set<Target> validateRoot(Map<String, JsonObject> inheritance, Set<String> classRoots, Set<String> errors, Target root) {
        var generated = new TreeSet<Target>();
        var cls = inheritance.get(root.owner());
        if (cls == null) {
            errors.add(root.format() + ": class does not exist");
            return generated;
        }
        validateKind(inheritance, classRoots, errors, root, generated, cls);
        return generated;
    }

    private static void validateKind(Map<String, JsonObject> inheritance, Set<String> classRoots, Set<String> errors,
                                     Target root, Set<Target> generated, JsonObject cls) {
        switch (root.kind()) {
            case CLASS -> validateClassRoot(inheritance, root, cls, generated, errors);
            case FIELD -> validateFieldRoot(root, cls, classRoots, errors);
            case METHOD -> validateMethodRoot(inheritance, root, cls, classRoots, generated, errors);
        }
    }

    private static void validateClassRoot(Map<String, JsonObject> inheritance, Target root, JsonObject cls,
                                          Set<Target> generated, Set<String> errors) {
        if (!hasSideOnly(cls)) {
            errors.add(root.format() + ": class is not annotated with legacy @SideOnly");
            return;
        }
        var fields = objectOf(cls, "fields");
        if (fields != null) {
            for (var field : fields.entrySet()) {
                if (hasSideOnly(field.getValue().getAsJsonObject())) {
                    generated.add(new Target(TargetKind.FIELD, root.owner(), field.getKey(), ""));
                }
            }
        }
        var methods = objectOf(cls, "methods");
        if (methods != null) {
            for (var method : methods.entrySet()) {
                if (hasSideOnly(method.getValue().getAsJsonObject())) {
                    var target = methodTarget(root.owner(), method.getKey());
                    generated.add(target);
                    addAnnotatedOverrides(inheritance, target, generated);
                }
            }
        }
    }

    private static void validateFieldRoot(Target root, JsonObject cls, Set<String> classRoots, Set<String> errors) {
        if (hasSideOnly(cls) && !classRoots.contains(root.owner())) {
            errors.add(root.format() + ": declaring class is also @SideOnly; add a class SAS root");
            return;
        }
        var fields = objectOf(cls, "fields");
        if (fields == null || !fields.has(root.name())) {
            errors.add(root.format() + ": field does not exist");
        } else if (!hasSideOnly(fields.getAsJsonObject(root.name()))) {
            errors.add(root.format() + ": field is not annotated with legacy @SideOnly");
        }
    }

    private static void validateMethodRoot(Map<String, JsonObject> inheritance, Target root, JsonObject cls,
                                           Set<String> classRoots, Set<Target> generated, Set<String> errors) {
        if (hasSideOnly(cls) && !classRoots.contains(root.owner())) {
            errors.add(root.format() + ": declaring class is also @SideOnly; add a class SAS root");
            return;
        }
        var methods = objectOf(cls, "methods");
        var key = root.name() + " " + root.descriptor();
        if (methods == null || !methods.has(key)) {
            errors.add(root.format() + ": method does not exist");
        } else if (!hasSideOnly(methods.getAsJsonObject(key))) {
            errors.add(root.format() + ": method is not annotated with legacy @SideOnly");
        } else {
            addAnnotatedOverrides(inheritance, root, generated);
        }
    }

    private static void addAnnotatedOverrides(Map<String, JsonObject> inheritance, Target root, Set<Target> generated) {
        var key = root.name() + " " + root.descriptor();
        for (var candidate : inheritance.entrySet()) {
            var methods = objectOf(candidate.getValue(), "methods");
            if (methods == null || !methods.has(key)) {
                continue;
            }
            var method = methods.getAsJsonObject(key);
            if (!hasSideOnly(method) || !method.has("override") || !root.owner().equals(method.get("override").getAsString())) {
                continue;
            }
            var owner = candidate.getValue().has("name") ? candidate.getValue().get("name").getAsString() : candidate.getKey();
            generated.add(new Target(TargetKind.METHOD, owner, root.name(), root.descriptor()));
        }
    }

    private static Target methodTarget(String owner, String key) {
        var separator = key.indexOf(' ');
        if (separator < 0) {
            throw new GradleException("Invalid inheritance method key: " + key);
        }
        return new Target(TargetKind.METHOD, owner, key.substring(0, separator), key.substring(separator + 1));
    }

    private static JsonObject objectOf(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonObject() ? parent.getAsJsonObject(name) : null;
    }

    private static boolean hasSideOnly(JsonObject annotated) {
        if (annotated == null || !annotated.has("annotations")
                || !annotated.get("annotations").isJsonArray()) {
            return false;
        }
        JsonArray annotations = annotated.getAsJsonArray("annotations");
        for (var annotation : annotations) {
            if (annotation.isJsonObject() && annotation.getAsJsonObject().has("desc")
                    && SideOnlyHandler.SIDE_ONLY_DESCRIPTOR.equals(annotation.getAsJsonObject().get("desc").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private void finishValidation(Map<Target, SasLine> roots, Set<String> errors, Map<Target, Set<Target>> expanded) throws IOException {
        if (!errors.isEmpty()) {
            throw new GradleException("Invalid legacy side annotation stripper entries:\n  " + String.join("\n  ", errors));
        }
        writeOutput(roots, expanded);
        this.getLogger().lifecycle("Validated {} SAS roots and generated {} dependent targets",
                roots.size(), expanded.values().stream().mapToInt(Set::size).sum());
    }

    private void writeOutput(Map<Target, SasLine> roots, Map<Target, Set<Target>> expanded) throws IOException {
        var output = this.getOutput().get().getAsFile().toPath();
        Files.createDirectories(output.toAbsolutePath().getParent());
        var lines = new ArrayList<String>();
        lines.add("# Generated by CleanroomGradle. Edit source SAS files, not this file.");
        for (var root : roots.values()) {
            var formatted = root.target().format();
            if (!root.comment().isBlank()) {
                formatted += " # " + root.comment();
            }
            lines.add(formatted);
            expanded.get(root.target()).forEach(target -> lines.add("\t" + target.format()));
        }
        Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }
}
