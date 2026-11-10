/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.userdev;

// CHECKSTYLE:OFF
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

// CHECKSTYLE:ON

import net.minecraftforge.srgutils.IMappingFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Remaps the member names of access transformers and applies them to sources. Mods may name members in MCP or SRG,
 * while the bytecode tool and FML at runtime only match SRG names.
 */
public final class AccessTransformers {

    private static final List<String> ACCESS = List.of("private", "default", "protected", "public");
    private static final Set<String> PRIMITIVES = Set.of(
        "boolean", "byte", "char", "short", "int", "long", "float", "double"
    );
    private static final Pattern KEYWORD = Pattern.compile(
        "\\b(public|protected|private|static|final|abstract|synchronized|native|transient|volatile|strictfp|default)\\b"
    );

    private AccessTransformers() { }

    /**
     * Rewrites each member to its mapped name, leaving members the mappings do not know as they are.
     *
     * @param text     contents of an access transformer
     * @param mappings names to map from, which may be either side of MCP and SRG
     */
    public static String remap(String text, IMappingFile mappings) {
        var lines = text.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            var comment = line.indexOf('#');
            var code = comment == -1 ? line : line.substring(0, comment);
            var parts = code.trim().split("\\s+");
            if (parts.length != 3) {
                continue;
            }
            var owner = mappings.getClass(parts[1].replace('.', '/'));
            var member = parts[2];
            var descriptor = member.indexOf('(');
            var name = descriptor == -1 ? member : member.substring(0, descriptor);
            if (owner == null || name.equals("*") || name.equals("<init>")) {
                continue;
            }
            var mapped = descriptor == -1 ? owner.remapField(name) :
                owner.remapMethod(name, member.substring(descriptor)) + member.substring(descriptor);
            var at = code.lastIndexOf(member);
            lines[i] = line.substring(0, at) + mapped + line.substring(at + member.length());
        }
        return String.join("\n", lines);
    }

    /**
     * Applies an access transformer, in the names of the sources, to the {@code .java} files under {@code root}. Like
     * the bytecode tool, it only ever widens access. Line numbers are kept so the sources still match the classes.
     *
     * @param root directory of {@code .java} files, laid out by package
     * @param text contents of an access transformer
     */
    public static void applyToSources(Path root, String text) throws IOException {
        Map<String, List<Rule>> files = new LinkedHashMap<>();
        for (var rule : parse(text)) {
            var outer = rule.owner().contains("$") ? rule.owner().substring(0, rule.owner().indexOf('$')) :
                rule.owner();
            files.computeIfAbsent(outer.replace('.', '/') + ".java", _ -> new ArrayList<>()).add(rule);
        }
        for (var entry : files.entrySet()) {
            var file = root.resolve(entry.getKey());
            if (!Files.isRegularFile(file)) {
                continue;
            }
            var source = Files.readString(file, StandardCharsets.UTF_8);
            var transformed = apply(source, entry.getValue());
            if (!transformed.equals(source)) {
                Files.writeString(file, transformed, StandardCharsets.UTF_8);
            }
        }
    }

    private static List<Rule> parse(String text) {
        List<Rule> rules = new ArrayList<>();
        for (var line : text.split("\n")) {
            var comment = line.indexOf('#');
            var parts = (comment == -1 ? line : line.substring(0, comment)).trim().split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            var modifier = parts[0];
            Boolean finality = null;
            if (modifier.endsWith("-f") || modifier.endsWith("+f")) {
                finality = modifier.endsWith("+f");
                modifier = modifier.substring(0, modifier.length() - 2);
            }
            var access = ACCESS.indexOf(modifier);
            if (access == -1) {
                continue;
            }
            String member = null;
            String descriptor = null;
            if (parts.length > 2) {
                member = parts[2];
                var open = member.indexOf('(');
                if (open != -1) {
                    descriptor = member.substring(open);
                    member = member.substring(0, open);
                }
            }
            rules.add(new Rule(access, finality, parts[1].replace('/', '.'), member, descriptor));
        }
        return rules;
    }

    private static String apply(String source, List<Rule> rules) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Applying access transformers to sources needs Gradle to run on a JDK.");
        }
        var file = new SimpleJavaFileObject(URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {

            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }

        };
        var task = (JavacTask) compiler.getTask(null, null, _ -> { }, List.of("-proc:none"), null, List.of(file));
        var unit = task.parse().iterator().next();
        var scan = new Scan(unit, Trees.instance(task).getSourcePositions(), source, rules);
        var prefix = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
        for (var type : unit.getTypeDecls()) {
            if (type instanceof ClassTree declared) {
                scan.visit(declared, prefix + declared.getSimpleName(), false, Set.of());
            }
        }
        return scan.edit();
    }

    private record Rule(int access, Boolean finality, String owner, String member, String descriptor) { }

    private record Edit(int start, int end, String text, int order) { }

    private static final class Change {

        final Tree declaration;
        int access = -1;
        Boolean finality;

        Change(Tree declaration) {
            this.declaration = declaration;
        }

    }

    private static final class Scan {

        final CompilationUnitTree unit;
        final SourcePositions positions;
        final String source;
        final List<Rule> rules;
        final Map<ModifiersTree, Change> changes = new IdentityHashMap<>();

        Scan(CompilationUnitTree unit, SourcePositions positions, String source, List<Rule> rules) {
            this.unit = unit;
            this.positions = positions;
            this.source = source;
            this.rules = rules;
        }

        void visit(ClassTree type, String name, boolean inner, Set<String> typeVariables) {
            var variables = new HashSet<>(typeVariables);
            type.getTypeParameters().stream().map(TypeParameterTree::getName).forEach(n -> variables.add(n.toString()));
            for (var rule : this.rules) {
                if (!rule.owner().equals(name)) {
                    continue;
                }
                if (rule.member() == null) {
                    change(type, type.getModifiers(), rule);
                    continue;
                }
                for (var member : type.getMembers()) {
                    if (rule.descriptor() == null && member instanceof VariableTree field &&
                        (rule.member().equals("*") || field.getName().contentEquals(rule.member())) &&
                        !implicit(field.getModifiers())) {
                        change(field, field.getModifiers(), rule);
                    } else if (rule.descriptor() != null && member instanceof MethodTree method &&
                        (rule.member().equals("*") ||
                            method.getName().contentEquals(rule.member()) &&
                                matches(method, rule.descriptor(), type, inner, variables))) {
                        change(method, method.getModifiers(), rule);
                    }
                }
            }
            for (var member : type.getMembers()) {
                if (member instanceof ClassTree nested) {
                    var isInner = nested.getKind() == Tree.Kind.CLASS &&
                        type.getKind() != Tree.Kind.INTERFACE &&
                        !nested.getModifiers().getFlags().contains(Modifier.STATIC);
                    visit(nested, name + "$" + nested.getSimpleName(), isInner, isInner ? variables : Set.of());
                }
            }
        }

        // Enum constants carry modifiers that are not written in the source
        private boolean implicit(ModifiersTree modifiers) {
            return !modifiers.getFlags().isEmpty() && this.positions.getStartPosition(this.unit, modifiers) == -1;
        }

        private boolean matches(
            MethodTree method, String descriptor, ClassTree owner, boolean inner, Set<String> typeVariables) {
            var parameters = descriptorParameters(descriptor);
            if (method.getName().contentEquals("<init>")) {
                // Constructors of enums and inner classes take leading parameters the source does not declare
                var synthetic = owner.getKind() == Tree.Kind.ENUM ? 2 : inner ? 1 : 0;
                parameters = parameters.subList(Math.min(synthetic, parameters.size()), parameters.size());
            }
            if (parameters.size() != method.getParameters().size()) {
                return false;
            }
            var variables = new HashSet<>(typeVariables);
            method.getTypeParameters().forEach(parameter -> variables.add(parameter.getName().toString()));
            for (var i = 0; i < parameters.size(); i++) {
                var expected = parameters.get(i);
                var actual = simpleName(method.getParameters().get(i).getType().toString());
                // A type variable erases to its bound, which the source does not spell out here
                if (variables.contains(actual.replace("[]", ""))) {
                    if (PRIMITIVES.contains(expected.replace("[]", "")) || dimensions(expected) != dimensions(actual)) {
                        return false;
                    }
                } else if (!expected.equals(actual)) {
                    return false;
                }
            }
            return true;
        }

        private void change(Tree declaration, ModifiersTree modifiers, Rule rule) {
            var change = this.changes.computeIfAbsent(modifiers, _ -> new Change(declaration));
            change.access = Math.max(change.access, rule.access());
            if (rule.finality() != null) {
                change.finality = rule.finality();
            }
        }

        String edit() {
            List<Edit> edits = new ArrayList<>();
            for (var entry : this.changes.entrySet()) {
                var modifiers = entry.getKey();
                var change = entry.getValue();
                var flags = modifiers.getFlags();
                var current = flags.contains(Modifier.PUBLIC) ? 3 : flags.contains(Modifier.PROTECTED) ? 2 :
                    flags.contains(Modifier.PRIVATE) ? 0 : 1;
                var keywords = keywords(modifiers);
                var start = afterModifiers(change.declaration, modifiers);
                var first = keywords.isEmpty() ? start : keywords.values().iterator().next();
                if (change.access > current) {
                    var keyword = ACCESS.get(current);
                    var replacement = ACCESS.get(change.access);
                    // Package access has no keyword, and "default" there would be an interface's default method
                    var at = current == 1 ? null : keywords.get(keyword);
                    if (at == null) {
                        edits.add(new Edit(first, first, replacement + " ", 0));
                    } else if (change.access == 1) {
                        edits.add(new Edit(at, endOfSpaces(at + keyword.length()), "", 0));
                    } else {
                        edits.add(new Edit(at, at + keyword.length(), replacement, 0));
                    }
                }
                if (Boolean.FALSE.equals(change.finality) && keywords.containsKey("final")) {
                    var at = keywords.get("final");
                    edits.add(new Edit(at, endOfSpaces(at + "final".length()), "", 1));
                } else if (Boolean.TRUE.equals(change.finality) && !flags.contains(Modifier.FINAL)) {
                    edits.add(new Edit(start, start, "final ", 1));
                }
            }
            // Applied back to front, so earlier edits keep their offsets and inserts at one place keep their order
            edits.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::order).reversed());
            var text = new StringBuilder(this.source);
            for (var edit : edits) {
                text.replace(edit.start(), edit.end(), edit.text());
            }
            return text.toString();
        }

        private Map<String, Integer> keywords(ModifiersTree modifiers) {
            Map<String, Integer> keywords = new LinkedHashMap<>();
            var start = (int) this.positions.getStartPosition(this.unit, modifiers);
            var end = (int) this.positions.getEndPosition(this.unit, modifiers);
            if (start < 0 || end <= start) {
                return keywords;
            }
            var text = new StringBuilder(this.source.substring(start, end));
            for (var annotation : modifiers.getAnnotations()) {
                var from = (int) this.positions.getStartPosition(this.unit, annotation) - start;
                var to = (int) this.positions.getEndPosition(this.unit, annotation) - start;
                text.replace(from, to, " ".repeat(to - from));
            }
            var matcher = KEYWORD.matcher(text);
            while (matcher.find()) {
                keywords.putIfAbsent(matcher.group(), start + matcher.start());
            }
            return keywords;
        }

        private int afterModifiers(Tree declaration, ModifiersTree modifiers) {
            var start = (int) this.positions.getStartPosition(this.unit, modifiers);
            var end = (int) this.positions.getEndPosition(this.unit, modifiers);
            if (start < 0 || end <= start) {
                return (int) this.positions.getStartPosition(this.unit, declaration);
            }
            while (end < this.source.length() && Character.isWhitespace(this.source.charAt(end))) {
                end++;
            }
            return end;
        }

        private int endOfSpaces(int position) {
            while (position < this.source.length() &&
                (this.source.charAt(position) == ' ' || this.source.charAt(position) == '\t')) {
                position++;
            }
            return position;
        }

    }

    private static List<String> descriptorParameters(String descriptor) {
        List<String> parameters = new ArrayList<>();
        var i = 1;
        while (descriptor.charAt(i) != ')') {
            var dimensions = 0;
            while (descriptor.charAt(i) == '[') {
                dimensions++;
                i++;
            }
            String type;
            if (descriptor.charAt(i) == 'L') {
                var end = descriptor.indexOf(';', i);
                type = simpleName(descriptor.substring(i + 1, end).replace('/', '.').replace('$', '.'));
                i = end + 1;
            } else {
                type = switch (descriptor.charAt(i)) {
                    case 'Z' -> "boolean";
                    case 'B' -> "byte";
                    case 'C' -> "char";
                    case 'S' -> "short";
                    case 'I' -> "int";
                    case 'J' -> "long";
                    case 'F' -> "float";
                    case 'D' -> "double";
                    default -> throw new IllegalArgumentException("Invalid method descriptor " + descriptor);
                };
                i++;
            }
            parameters.add(type + "[]".repeat(dimensions));
        }
        return parameters;
    }

    /** The simple name of a type as written in source, keeping array brackets and dropping type arguments. */
    private static String simpleName(String type) {
        var erased = new StringBuilder();
        var depth = 0;
        for (var c : type.toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0 && !Character.isWhitespace(c)) {
                erased.append(c);
            }
        }
        var name = erased.toString().replace("...", "[]");
        return name.substring(name.lastIndexOf('.', name.contains("[") ? name.indexOf('[') : name.length()) + 1);
    }

    private static int dimensions(String type) {
        return (type.length() - type.replace("[]", "").length()) / 2;
    }

}
