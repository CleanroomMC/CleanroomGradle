package com.cleanroommc.gradle.api.util.sas;

import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** In-process, deterministic handling of legacy Forge {@code @SideOnly} metadata. */
public final class SideOnlyHandler {

    public static final String SIDE_ONLY_DESCRIPTOR = "Lnet/minecraftforge/fml/relauncher/SideOnly;";
    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    public enum TargetKind {

        CLASS,
        FIELD,
        METHOD

    }

    public record Target(TargetKind kind, String owner, String name, String descriptor) implements Comparable<Target> {

        private static final Comparator<Target> ORDER = Comparator
                .comparing(Target::owner)
                .thenComparing(Target::name)
                .thenComparing(Target::kind)
                .thenComparing(Target::descriptor);

        public Target {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("SAS target has no class name");
            }
            owner = owner.replace('.', '/');
            name = name == null ? "" : name;
            descriptor = descriptor == null ? "" : descriptor;
        }

        public String format() {
            return switch (this.kind) {
                case CLASS -> this.owner;
                case FIELD -> this.owner + " " + this.name;
                case METHOD -> this.owner + " " + this.name + this.descriptor;
            };
        }

        @Override
        public int compareTo(Target other) {
            return ORDER.compare(this, other);
        }
    }

    public record SasLine(Target target, String comment, boolean generated) { }

    public record TransformResult(int classesRemoved, int fieldsRemoved, int methodsRemoved, int annotationsRemoved) { }

    public static List<SasLine> readSas(Collection<Path> files) throws IOException {
        var sortedFiles = files.stream().map(Path::toAbsolutePath).sorted().toList();
        var result = new ArrayList<SasLine>();
        for (var file : sortedFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            for (var raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                var parsed = parseLine(raw);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    public static SasLine parseLine(String raw) {
        var generated = raw.startsWith("\t");
        var commentAt = raw.indexOf('#');
        var comment = commentAt < 0 ? "" : raw.substring(commentAt + 1).strip();
        var value = (commentAt < 0 ? raw : raw.substring(0, commentAt)).strip();
        if (value.isEmpty()) {
            return null;
        }

        var splitAt = firstWhitespace(value);
        var owner = (splitAt < 0 ? value : value.substring(0, splitAt)).replace('.', '/');
        var member = splitAt < 0 ? "" : value.substring(splitAt).strip();
        if (member.isEmpty()) {
            return new SasLine(new Target(TargetKind.CLASS, owner, "", ""), comment, generated);
        }

        var descriptorAt = member.indexOf('(');
        if (descriptorAt < 0) {
            if (member.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Invalid SAS field target: " + raw);
            }
            return new SasLine(new Target(TargetKind.FIELD, owner, member, ""), comment, generated);
        }

        var name = member.substring(0, descriptorAt).strip();
        var descriptor = member.substring(descriptorAt).replace(" ", "").replace("\t", "");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Invalid SAS method target: " + raw);
        }
        try {
            Type.getMethodType(descriptor);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid SAS method descriptor in: " + raw, exception);
        }
        return new SasLine(new Target(TargetKind.METHOD, owner, name, descriptor), comment, generated);
    }

    public static TransformResult applySas(Path input, Path output, Collection<Path> sasFiles) throws IOException {
        var targets = new HashSet<Target>();
        readSas(sasFiles).forEach(line -> targets.add(line.target()));
        return applySas(input, output, targets);
    }

    public static TransformResult applySas(Path input, Path output, Set<Target> targets) throws IOException {
        var entries = readArchive(input);
        var found = new HashSet<Target>();
        var annotationsRemoved = new int[] { 0 };

        for (var entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            var node = readClass(entry.getValue());
            var classTarget = new Target(TargetKind.CLASS, node.name, "", "");
            if (targets.contains(classTarget) && removeSideOnly(node.visibleAnnotations, node.invisibleAnnotations) > 0) {
                found.add(classTarget);
                annotationsRemoved[0]++;
            }
            for (var field : node.fields) {
                var target = new Target(TargetKind.FIELD, node.name, field.name, "");
                if (targets.contains(target) && removeSideOnly(field.visibleAnnotations, field.invisibleAnnotations) > 0) {
                    found.add(target);
                    annotationsRemoved[0]++;
                }
            }
            for (var method : node.methods) {
                var target = new Target(TargetKind.METHOD, node.name, method.name, method.desc);
                if (targets.contains(target) && removeSideOnly(method.visibleAnnotations, method.invisibleAnnotations) > 0) {
                    found.add(target);
                    annotationsRemoved[0]++;
                }
            }
            entries.put(entry.getKey(), writeClass(node));
        }

        var missing = new TreeSet<>(targets);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("SAS targets did not resolve to legacy @SideOnly annotations:\n  "
                    + String.join("\n  ", missing.stream().map(Target::format).toList()));
        }

        writeArchive(output, entries);
        return new TransformResult(0, 0, 0, annotationsRemoved[0]);
    }

    public static TransformResult strip(Path input, Path output, Side targetSide, boolean validateReferences) throws IOException {
        var entries = readArchive(input);
        var classes = new TreeMap<String, ClassNode>();
        for (var entry : entries.entrySet()) {
            if (entry.getKey().endsWith(".class")) {
                var node = readClass(entry.getValue());
                classes.put(node.name, node);
            }
        }
        var removedClasses = new HashSet<String>();
        for (var node : classes.values()) {
            var side = sideOf(node.visibleAnnotations, node.invisibleAnnotations);
            if (side != null && side != targetSide) {
                removedClasses.add(node.name);
            }
        }
        var changed = true;
        while (changed) {
            changed = false;
            for (var className : classes.keySet()) {
                if (!removedClasses.contains(className) && removedClasses.stream().anyMatch(parent -> className.startsWith(parent + "$"))) {
                    removedClasses.add(className);
                    changed = true;
                }
            }
        }
        var fieldsRemoved = 0;
        var methodsRemoved = 0;
        var annotationsRemoved = 0;
        var removedFields = new HashSet<FieldKey>();
        var removedMethods = new HashSet<MethodKey>();
        for (var node : classes.values()) {
            var entryName = node.name + ".class";
            if (removedClasses.contains(node.name)) {
                entries.remove(entryName);
                continue;
            }
            annotationsRemoved += removeSideOnly(node.visibleAnnotations, node.invisibleAnnotations);
            node.innerClasses.removeIf(inner -> removedClasses.contains(inner.name));
            var fieldIterator = node.fields.iterator();
            while (fieldIterator.hasNext()) {
                var field = fieldIterator.next();
                var side = sideOf(field.visibleAnnotations, field.invisibleAnnotations);
                if (side != null && side != targetSide) {
                    removedFields.add(new FieldKey(node.name, field.name, field.desc));
                    fieldIterator.remove();
                    fieldsRemoved++;
                } else {
                    annotationsRemoved += removeSideOnly(field.visibleAnnotations, field.invisibleAnnotations);
                }
            }
            var removedLambdaTargets = new ArrayDeque<MethodKey>();
            var retainedLambdaTargets = new HashSet<MethodKey>();
            var methodIterator = node.methods.iterator();
            while (methodIterator.hasNext()) {
                var method = methodIterator.next();
                var side = sideOf(method.visibleAnnotations, method.invisibleAnnotations);
                if (side != null && side != targetSide) {
                    removedMethods.add(new MethodKey(node.name, method.name, method.desc));
                    methodIterator.remove();
                    methodsRemoved++;
                    collectLambdaTargets(method, removedLambdaTargets::add);
                } else {
                    annotationsRemoved += removeSideOnly(method.visibleAnnotations, method.invisibleAnnotations);
                    collectLambdaTargets(method, retainedLambdaTargets::add);
                }
            }
            while (!removedLambdaTargets.isEmpty()) {
                var candidate = removedLambdaTargets.remove();
                if (!candidate.owner().equals(node.name) || retainedLambdaTargets.contains(candidate)) {
                    continue;
                }
                var lambda = findSyntheticMethod(node, candidate);
                if (lambda != null && node.methods.remove(lambda)) {
                    removedMethods.add(candidate);
                    methodsRemoved++;
                    collectLambdaTargets(lambda, removedLambdaTargets::add);
                }
            }
            sanitizeSignatures(node, removedClasses);
            entries.put(entryName, writeClass(node));
        }

        if (validateReferences) {
            validateNoRemovedReferences(entries, removedClasses, removedFields, removedMethods, targetSide);
        }
        writeArchive(output, entries);
        return new TransformResult(removedClasses.size(), fieldsRemoved, methodsRemoved, annotationsRemoved);
    }

    private static void sanitizeSignatures(ClassNode node, Set<String> removedClasses) {
        if (signatureReferencesRemoved(node.signature, removedClasses)) {
            node.signature = null;
        }
        for (var field : node.fields) {
            if (signatureReferencesRemoved(field.signature, removedClasses)) {
                field.signature = null;
            }
        }
        for (var method : node.methods) {
            if (signatureReferencesRemoved(method.signature, removedClasses)) {
                method.signature = null;
            }
            if (method.localVariables != null) {
                for (var local : method.localVariables) {
                    if (signatureReferencesRemoved(local.signature, removedClasses)) {
                        local.signature = null;
                    }
                }
            }
        }
        if (node.recordComponents != null) {
            for (var component : node.recordComponents) {
                if (signatureReferencesRemoved(component.signature, removedClasses)) {
                    component.signature = null;
                }
            }
        }
    }

    private static boolean signatureReferencesRemoved(String signature, Set<String> removedClasses) {
        if (signature == null || removedClasses.isEmpty()) {
            return false;
        }
        var removed = new AtomicBoolean(false);
        try {
            new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
                @Override
                public void visitClassType(String name) {
                    if (removedClasses.contains(name)) {
                        removed.set(true);
                    }
                }
            });
        } catch (IllegalArgumentException ignored) {
            // Preserve malformed signatures, transform is responsible only for side pruning
        }
        return removed.get();
    }

    private static void validateNoRemovedReferences(Map<String, byte[]> entries, Set<String> removedClasses,
            Set<FieldKey> removedFields, Set<MethodKey> removedMethods, Side targetSide) {
        var errors = new TreeSet<String>();
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            var node = readClass(entry.getValue());
            checkReference(node.name, "superclass", node.superName, removedClasses, errors);
            checkReference(node.name, "outer class", node.outerClass, removedClasses, errors);
            checkReference(node.name, "nest host", node.nestHostClass, removedClasses, errors);
            node.interfaces.forEach(type -> checkReference(node.name, "interface", type, removedClasses, errors));
            if (node.nestMembers != null) {
                node.nestMembers.forEach(type -> checkReference(node.name, "nest member", type, removedClasses, errors));
            }
            if (node.permittedSubclasses != null) {
                node.permittedSubclasses.forEach(type -> checkReference(node.name, "permitted subclass", type, removedClasses, errors));
            }
            node.fields.forEach(field -> checkDescriptor(node.name + "." + field.name, field.desc, removedClasses, errors));
            for (var method : node.methods) {
                var location = node.name + "." + method.name + method.desc;
                checkDescriptor(location, method.desc, removedClasses, errors);
                method.exceptions.forEach(type -> checkReference(location, "exception", type, removedClasses, errors));
                method.tryCatchBlocks.forEach(block -> checkReference(location, "catch type", block.type, removedClasses, errors));
                for (var instruction : method.instructions) {
                    switch (instruction) {
                        case TypeInsnNode type -> checkTypeLike(location, type.desc, removedClasses, errors);
                        case FieldInsnNode field -> {
                            checkReference(location, "field owner", field.owner, removedClasses, errors);
                            checkDescriptor(location, field.desc, removedClasses, errors);
                            if (removedFields.contains(new FieldKey(field.owner, field.name, field.desc))) {
                                errors.add(location + " -> removed field " + field.owner + "." + field.name + field.desc);
                            }
                        }
                        case MethodInsnNode call -> {
                            checkReference(location, "method owner", call.owner, removedClasses, errors);
                            checkDescriptor(location, call.desc, removedClasses, errors);
                            if (removedMethods.contains(new MethodKey(call.owner, call.name, call.desc))) {
                                errors.add(location + " -> removed method " + call.owner + "." + call.name + call.desc);
                            }
                        }
                        case InvokeDynamicInsnNode dynamic -> {
                            checkDescriptor(location, dynamic.desc, removedClasses, errors);
                            checkHandle(location, dynamic.bsm, removedClasses, removedFields, removedMethods, errors);
                            for (var argument : dynamic.bsmArgs) {
                                checkConstant(location, argument, removedClasses, removedFields, removedMethods, errors);
                            }
                        }
                        case LdcInsnNode ldc -> checkConstant(
                                location, ldc.cst, removedClasses, removedFields, removedMethods, errors);
                        case MultiANewArrayInsnNode array -> checkDescriptor(location, array.desc, removedClasses, errors);
                        case FrameNode frame -> {
                            checkFrame(location, frame.local, removedClasses, errors);
                            checkFrame(location, frame.stack, removedClasses, errors);
                        }
                        default -> { }
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            var shown = errors.stream().limit(50).toList();
            var suffix = errors.size() > shown.size() ? "\n  ... and " + (errors.size() - shown.size()) + " more" : "";
            throw new IllegalStateException("Invalid " + targetSide.name().toLowerCase(Locale.ROOT)
                    + " jar: retained bytecode references classes removed by @SideOnly:\n  "
                    + String.join("\n  ", shown) + suffix);
        }
    }

    private static void checkFrame(String location, List<Object> values, Set<String> removed, Set<String> errors) {
        if (values == null) {
            return;
        }
        values.stream().filter(String.class::isInstance).map(String.class::cast)
                .forEach(value -> checkTypeLike(location, value, removed, errors));
    }

    private static void checkConstant(String location, Object value, Set<String> removedClasses,
            Set<FieldKey> removedFields, Set<MethodKey> removedMethods, Set<String> errors) {
        if (value instanceof Type type) {
            checkType(location, type, removedClasses, errors);
        } else if (value instanceof Handle handle) {
            checkHandle(location, handle, removedClasses, removedFields, removedMethods, errors);
        } else if (value instanceof ConstantDynamic dynamic) {
            checkDescriptor(location, dynamic.getDescriptor(), removedClasses, errors);
            checkHandle(location, dynamic.getBootstrapMethod(),
                    removedClasses, removedFields, removedMethods, errors);
            for (var index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                checkConstant(location, dynamic.getBootstrapMethodArgument(index),
                        removedClasses, removedFields, removedMethods, errors);
            }
        }
    }

    private static void checkHandle(String location, Handle handle, Set<String> removedClasses,
            Set<FieldKey> removedFields, Set<MethodKey> removedMethods, Set<String> errors) {
        checkReference(location, "handle owner", handle.getOwner(), removedClasses, errors);
        checkDescriptor(location, handle.getDesc(), removedClasses, errors);
        if (handle.getTag() >= Opcodes.H_GETFIELD && handle.getTag() <= Opcodes.H_PUTSTATIC) {
            if (removedFields.contains(new FieldKey(handle.getOwner(), handle.getName(), handle.getDesc()))) {
                errors.add(location + " -> removed field handle "
                        + handle.getOwner() + "." + handle.getName() + handle.getDesc());
            }
        } else if (removedMethods.contains(new MethodKey(handle.getOwner(), handle.getName(), handle.getDesc()))) {
            errors.add(location + " -> removed method handle "
                    + handle.getOwner() + "." + handle.getName() + handle.getDesc());
        }
    }

    private static void checkTypeLike(String location, String value, Set<String> removed, Set<String> errors) {
        if (value == null) {
            return;
        }
        if (value.startsWith("[") || value.startsWith("L") || value.startsWith("(")) {
            checkDescriptor(location, value, removed, errors);
        } else {
            checkReference(location, "type", value, removed, errors);
        }
    }

    private static void checkDescriptor(String location, String descriptor, Set<String> removed, Set<String> errors) {
        if (descriptor == null || descriptor.isEmpty()) {
            return;
        }
        try {
            if (descriptor.charAt(0) == '(') {
                for (var type : Type.getArgumentTypes(descriptor)) {
                    checkType(location, type, removed, errors);
                }
                checkType(location, Type.getReturnType(descriptor), removed, errors);
            } else {
                checkType(location, Type.getType(descriptor), removed, errors);
            }
        } catch (IllegalArgumentException ignored) {
            // Some bootstrap handles expose a non-descriptor name and their owner will still be validated.
        }
    }

    private static void checkType(String location, Type type, Set<String> removed, Set<String> errors) {
        while (type.getSort() == Type.ARRAY) {
            type = type.getElementType();
        }
        if (type.getSort() == Type.OBJECT) {
            checkReference(location, "descriptor", type.getInternalName(), removed, errors);
        } else if (type.getSort() == Type.METHOD) {
            checkDescriptor(location, type.getDescriptor(), removed, errors);
        }
    }

    private static void checkReference(String location, String kind, String referenced, Set<String> removed, Set<String> errors) {
        if (referenced != null && removed.contains(referenced)) {
            errors.add(location + " -> " + kind + " " + referenced);
        }
    }

    private static MethodNode findSyntheticMethod(ClassNode owner, MethodKey key) {
        for (var method : owner.methods) {
            if ((method.access & Opcodes.ACC_SYNTHETIC) != 0 && method.name.equals(key.name()) && method.desc.equals(key.descriptor())) {
                return method;
            }
        }
        return null;
    }

    private static void collectLambdaTargets(MethodNode method, Consumer<MethodKey> consumer) {
        for (var instruction : method.instructions) {
            if (!(instruction instanceof InvokeDynamicInsnNode dynamic)) {
                continue;
            }
            var bootstrapOwner = dynamic.bsm.getOwner();
            var bootstrapName = dynamic.bsm.getName();
            if (!"java/lang/invoke/LambdaMetafactory".equals(bootstrapOwner)
                    || !("metafactory".equals(bootstrapName) || "altMetafactory".equals(bootstrapName))) {
                continue;
            }
            for (var argument : dynamic.bsmArgs) {
                if (argument instanceof Handle handle) {
                    consumer.accept(new MethodKey(handle.getOwner(), handle.getName(), handle.getDesc()));
                }
            }
        }
    }

    private static Side sideOf(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        Side found = null;
        @SuppressWarnings("unchecked")
        List<AnnotationNode>[] annotationLists = new List[] { visible, invisible };
        for (var annotations : annotationLists) {
            if (annotations == null) {
                continue;
            }
            for (var annotation : annotations) {
                if (!SIDE_ONLY_DESCRIPTOR.equals(annotation.desc)) {
                    continue;
                }
                var side = sideValue(annotation);
                if (found != null && found != side) {
                    throw new IllegalArgumentException("Conflicting legacy @SideOnly annotations");
                }
                found = side;
            }
        }
        return found;
    }

    private static Side sideValue(AnnotationNode annotation) {
        if (annotation.values != null) {
            for (var index = 0; index + 1 < annotation.values.size(); index += 2) {
                if (!"value".equals(annotation.values.get(index))) {
                    continue;
                }
                var value = annotation.values.get(index + 1);
                if (value instanceof String[] enumValue && enumValue.length == 2) {
                    return Side.valueOf(enumValue[1]);
                }
            }
        }
        throw new IllegalArgumentException("Legacy @SideOnly annotation has no valid side value");
    }

    @SafeVarargs
    private static int removeSideOnly(List<AnnotationNode>... annotationLists) {
        var removed = 0;
        for (var annotations : annotationLists) {
            if (annotations == null) {
                continue;
            }
            var before = annotations.size();
            annotations.removeIf(annotation -> SIDE_ONLY_DESCRIPTOR.equals(annotation.desc));
            removed += before - annotations.size();
        }
        return removed;
    }

    private static ClassNode readClass(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] writeClass(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, byte[]> readArchive(Path input) throws IOException {
        var result = new TreeMap<String, byte[]>();
        try (var zip = new ZipFile(input.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || signatureFile(entry.getName())) {
                    continue;
                }
                try (var stream = zip.getInputStream(entry)) {
                    if (result.put(entry.getName(), stream.readAllBytes()) != null) {
                        throw new IOException("Duplicate jar entry: " + entry.getName());
                    }
                }
            }
        }
        return result;
    }

    private static void writeArchive(Path output, Map<String, byte[]> entries) throws IOException {
        var absolute = output.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        var temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        try {
            var ordered = new LinkedHashMap<String, byte[]>();
            if (entries.containsKey(MANIFEST)) {
                ordered.put(MANIFEST, entries.get(MANIFEST));
            }
            entries.entrySet().stream()
                    .filter(entry -> !MANIFEST.equals(entry.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            try (var outputStream = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (var entry : ordered.entrySet()) {
                    var zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(0L);
                    outputStream.putNextEntry(zipEntry);
                    outputStream.write(entry.getValue());
                    outputStream.closeEntry();
                }
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean signatureFile(String name) {
        var upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return false;
        }
        var leaf = upper.substring("META-INF/".length());
        return leaf.endsWith(".SF") || leaf.endsWith(".RSA") || leaf.endsWith(".DSA")
                || leaf.endsWith(".EC") || leaf.startsWith("SIG-");
    }

    private static int firstWhitespace(String value) {
        for (var index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private record FieldKey(String owner, String name, String descriptor) { }

    private record MethodKey(String owner, String name, String descriptor) { }

    private SideOnlyHandler() { }
}
