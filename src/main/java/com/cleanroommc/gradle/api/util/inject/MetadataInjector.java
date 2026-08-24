package com.cleanroommc.gradle.api.util.inject;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.util.IO;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;

/**
 * In-process replacement for MCInjector.
 * Takes an SRG named jar plus the MCP {@code access.txt}, {@code constructors.txt} and {@code exceptions.txt} side tables and writes a jar whose {@code net/minecraft}
 * classes carry the metadata a decompiler needs: declared visibilities, {@code throws} clauses, named parameters,
 * repaired inner class constructors and shifted parameter annotations.
 *
 * <p>Only {@code net/minecraft} classes are touched; every other entry is copied through untouched.
 */
public final class MetadataInjector {

    public record InjectResult(int classesProcessed, int entriesCopied, int abstractMethodsRecorded) { }

    private record Entry(String name, byte[] data, boolean directory) {

        private boolean transformable() {
            return !this.directory && this.name.endsWith(".class") && this.name.startsWith(Meta.MINECRAFT_PACKAGE_PATH);
        }

    }

    private static final String SRG_PREFIX = "func_";
    private static final char PLACEHOLDER = '☃';
    private static final String ABSTRACT_PARAMETERS = "fernflower_abstract_parameter_names.txt";
    private static final int ABSTRACT_OR_NATIVE = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;
    private static final int BUFFER = 1 << 16;

    public static InjectResult inject(Path input, Path output, Path accessFile, Path constructorsFile, Path exceptionsFile) throws IOException {
        return new MetadataInjector(AccessMap.load(accessFile), ConstructorMap.load(constructorsFile), ExceptionMap.load(exceptionsFile)).run(input, output);
    }

    private final AccessMap access;
    private final ConstructorMap constructors;
    private final ExceptionMap exceptions;
    private final Map<String, List<String>> abstractParameters = new ConcurrentHashMap<>();

    private MetadataInjector(AccessMap access, ConstructorMap constructors, ExceptionMap exceptions) {
        this.access = access;
        this.constructors = constructors;
        this.exceptions = exceptions;
    }

    private InjectResult run(Path input, Path output) throws IOException {
        var entries = readEntries(input);
        var transformed = this.transform(entries);

        var absolute = output.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        var temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        var processed = 0;
        var copied = 0;
        try {
            try (var out = IO.zipOut(temporary, BUFFER)) {
                var names = new HashSet<String>();
                for (var index = 0; index < entries.size(); index++) {
                    var entry = entries.get(index);
                    out.putNextEntry(reproducible(entry.name()));
                    if (!entry.directory()) {
                        out.write(transformed[index]);
                        names.add(entry.name());
                        if (entry.transformable()) {
                            processed++;
                        } else {
                            copied++;
                        }
                    }
                    out.closeEntry();
                }
                if (!this.abstractParameters.isEmpty() && !names.contains(ABSTRACT_PARAMETERS)) {
                    out.putNextEntry(reproducible(ABSTRACT_PARAMETERS));
                    out.write(this.renderAbstractParameters());
                    out.closeEntry();
                }
            }
            IO.move(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new InjectResult(processed, copied, this.abstractParameters.size());
    }

    private static List<Entry> readEntries(Path input) throws IOException {
        var entries = new ArrayList<Entry>();
        var names = new HashSet<String>();
        try (var in = IO.zipIn(input, BUFFER)) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                var directory = entry.isDirectory();
                if (!directory && !names.add(entry.getName())) {
                    throw new IOException("Duplicate jar entry: " + entry.getName());
                }
                entries.add(new Entry(entry.getName(), directory ? null : in.readAllBytes(), directory));
            }
        }
        return entries;
    }

    private byte[][] transform(List<Entry> entries) {
        var results = new byte[entries.size()][];
        var deferred = new boolean[entries.size()];
        IntStream.range(0, entries.size()).parallel().forEach(index -> {
            var entry = entries.get(index);
            if (!entry.transformable()) {
                results[index] = entry.data();
                return;
            }
            try {
                results[index] = this.processClass(entry.data(), false);
            } catch (DeferredConstructorId ignored) {
                deferred[index] = true;
            }
        });
        for (var index = 0; index < entries.size(); index++) {
            if (deferred[index]) {
                results[index] = this.processClass(entries.get(index).data(), true);
            }
        }
        return results;
    }

    private static ZipEntry reproducible(String name) {
        var entry = new ZipEntry(name);
        entry.setTime(0L);
        return entry;
    }

    private byte[] renderAbstractParameters() {
        var builder = new StringBuilder();
        this.abstractParameters.forEach((key, parameters) -> builder
                .append(key)
                .append(' ')
                .append(String.join(" ", parameters))
                .append('\n'));
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] processClass(byte[] original, boolean allowGeneration) {
        var reader = new ClassReader(original);
        var node = new ClassNode();
        ClassVisitor visitor = new ApplyMetadata(node, this, allowGeneration);
        visitor = new PlaceholderNamer(visitor, node);
        visitor = new AccessFixer(visitor, this.access);
        visitor = new ParameterAnnotationFixer(visitor, node);
        visitor = new InnerClassInitAdder(visitor);
        reader.accept(visitor, 0);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private void recordAbstractParameters(String owner, String name, String descriptor, List<String> parameters) {
        this.abstractParameters.put(owner + ' ' + name + ' ' + descriptor, parameters);
    }

    /**
     * @param allowGeneration whether this pass may hand out a new id, or has to defer the class to the serial pass
     * @return the constructor's id, or {@code -1} when it has none and is not entitled to one
     */
    private int constructorId(String owner, String descriptor, boolean entitled, boolean allowGeneration) {
        var id = this.constructors.get(owner, descriptor);
        if (id >= 0 || !entitled) {
            return id;
        }
        if (!allowGeneration) {
            throw DEFERRED;
        }
        return this.constructors.generate(owner, descriptor);
    }

    /** Signals that a class needs a constructor id allocated, which only the serial pass may do. */
    private static final class DeferredConstructorId extends RuntimeException {

        private DeferredConstructorId() {
            super(null, null, false, false);
        }

    }

    private static final DeferredConstructorId DEFERRED = new DeferredConstructorId();

    /**
     * Adds the {@code throws} clauses and the {@code p_*} parameter names.
     */
    private static final class ApplyMetadata extends ClassVisitor {

        private final MetadataInjector injector;
        private final boolean allowGeneration;
        private String className;
        private Map<String, String[]> classExceptions;

        private ApplyMetadata(ClassNode node, MetadataInjector injector, boolean allowGeneration) {
            super(Opcodes.ASM9, node);
            this.injector = injector;
            this.allowGeneration = allowGeneration;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.classExceptions = this.injector.exceptions.get(name);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ("<clinit>".equals(name)) {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
            var declared = this.mergeExceptions(name, descriptor, exceptions);
            var method = (MethodNode) this.cv.visitMethod(access, name, descriptor, signature, declared);
            return new NamingMethodVisitor(this.api, method, this, name, descriptor);
        }

        private String[] mergeExceptions(String name, String descriptor, String[] declared) {
            if (this.classExceptions == null) {
                return declared;
            }
            var known = this.classExceptions.get(name + " " + descriptor);
            if (known == null) {
                return declared;
            }
            var merged = new TreeSet<String>();
            Collections.addAll(merged, known);
            if (declared != null) {
                Collections.addAll(merged, declared);
            }
            if (merged.size() <= (declared == null ? 0 : declared.length)) {
                return declared;
            }
            return merged.toArray(String[]::new);
        }

        /**
         * Names every parameter slot. Abstract and native methods have nowhere to put a local variable table, so
         * their names go to {@link #ABSTRACT_PARAMETERS} instead.
         */
        private void nameParameters(String name, String descriptor, MethodNode method) {
            var instance = (method.access & Opcodes.ACC_STATIC) == 0;
            var arguments = Type.getArgumentTypes(method.desc);
            var count = arguments.length + (instance ? 1 : 0);
            if (count == 0) {
                return;
            }
            var types = new Type[count];
            if (instance) {
                types[0] = Type.getObjectType(this.className);
            }
            System.arraycopy(arguments, 0, types, instance ? 1 : 0, arguments.length);

            // Slot numbers are not parameter numbers: longs and doubles take two of them, and `this` takes slot zero.
            var slots = new int[count];
            for (int index = 0, slot = 0; index < count; index++) {
                slots[index] = slot;
                slot += types[index].getSize();
            }
            var prefix = this.parameterPrefix(name, descriptor, count);
            var parameters = new String[count];
            for (var index = 0; index < count; index++) {
                parameters[index] = instance && index == 0 ? "this" : prefix + slots[index] + "_";
            }

            if ((method.access & ABSTRACT_OR_NATIVE) != 0) {
                var first = instance ? 1 : 0;
                if (count > first) {
                    this.injector.recordAbstractParameters(this.className, name, descriptor,
                            List.of(Arrays.copyOfRange(parameters, first, count)));
                }
                return;
            }
            applyLocalVariables(method, types, parameters, slots);
        }

        private String parameterPrefix(String name, String descriptor, int parameterCount) {
            var srg = srgNumberEnd(name);
            if (srg > 0) {
                return "p_" + name.substring(SRG_PREFIX.length(), srg) + "_";
            }
            if ("<init>".equals(name)) {
                // Only constructors that take something beyond `this` are worth an id of their own.
                return "p_i" + this.injector.constructorId(this.className, descriptor, parameterCount > 1,
                        this.allowGeneration) + "_";
            }
            return "p_" + name + "_";
        }

        /**
         * Recognises {@code func_<digits>_<something>} and locates the end of the digits in one pass.
         *
         * @return the index one past the last digit, or {@code -1} when the name is not an SRG method
         */
        private static int srgNumberEnd(String name) {
            if (!name.startsWith(SRG_PREFIX)) {
                return -1;
            }
            var index = SRG_PREFIX.length();
            while (index < name.length() && name.charAt(index) >= '0' && name.charAt(index) <= '9') {
                index++;
            }
            if (index == SRG_PREFIX.length() || index + 1 >= name.length() || name.charAt(index) != '_') {
                return -1;
            }
            return index;
        }

    }

    /**
     * Names the parameters once the method node behind it has been fully visited.
     */
    private static final class NamingMethodVisitor extends MethodVisitor {

        private final MethodNode method;
        private final ApplyMetadata owner;
        private final String name;
        private final String descriptor;

        private NamingMethodVisitor(int api, MethodNode method, ApplyMetadata owner, String name, String descriptor) {
            super(api, method);
            this.method = method;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            this.owner.nameParameters(this.name, this.descriptor, this.method);
        }

    }

    /**
     * Rewrites the local variable table so every parameter slot carries its generated name over the whole method.
     */
    private static void applyLocalVariables(MethodNode method, Type[] types, String[] parameters, int[] slots) {
        var first = method.instructions.getFirst();
        if (first == null) {
            method.instructions.add(new LabelNode());
        } else if (first.getType() != AbstractInsnNode.LABEL) {
            method.instructions.insertBefore(first, new LabelNode());
        }
        var last = method.instructions.getLast();
        if (last == null) {
            method.instructions.add(new LabelNode());
        } else if (last.getType() != AbstractInsnNode.LABEL) {
            method.instructions.insert(last, new LabelNode());
        }

        // Slots are dense and small, so index the names by slot directly instead of boxing them into a map.
        var bySlot = new String[slots[parameters.length - 1] + 1];
        for (var index = 0; index < parameters.length; index++) {
            bySlot[slots[index]] = parameters[index];
        }
        var start = (LabelNode) method.instructions.getFirst();
        var end = (LabelNode) method.instructions.getLast();
        if (method.localVariables == null) {
            method.localVariables = new ArrayList<>(parameters.length);
        }
        var renamed = new boolean[bySlot.length];
        for (var local : method.localVariables) {
            if (local.index < bySlot.length && bySlot[local.index] != null) {
                local.name = bySlot[local.index];
                renamed[local.index] = true;
            }
        }
        for (var index = 0; index < parameters.length; index++) {
            if (!renamed[slots[index]]) {
                method.localVariables.add(new LocalVariableNode(parameters[index], types[index].getDescriptor(),
                        null, start, end, slots[index]));
            }
        }
        method.localVariables.sort(Comparator.comparingInt(local -> local.index));
    }

    /**
     * Applies the MCP access table to the class, its fields and its methods.
     */
    private static final class AccessFixer extends ClassVisitor {

        private final AccessMap access;
        private AccessMap.ClassChanges changes;

        private AccessFixer(ClassVisitor next, AccessMap access) {
            super(Opcodes.ASM9, next);
            this.access = access;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.changes = this.access.get(name);
            var level = this.changes == null ? null : this.changes.forClass();
            super.visit(version, apply(access, level), name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            var level = this.changes == null ? null : this.changes.forField(name);
            return super.visitField(apply(access, level), name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            var level = this.changes == null ? null : this.changes.forMethod(name, descriptor);
            return super.visitMethod(apply(access, level), name, descriptor, signature, exceptions);
        }

        private static int apply(int access, AccessLevel level) {
            return level == null || AccessLevel.of(access) == level ? access : level.apply(access);
        }

    }

    /**
     * A non-static inner class whose constructor was optimised away still needs one for the decompiler to recognise
     * the outer instance field, so synthesise the constructor the source must have had.
     */
    private static final class InnerClassInitAdder extends ClassVisitor {

        private static final int OUTER_FIELD = Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL;

        private String className;
        private String outerDescriptor;
        private String outerField;
        private boolean hasConstructor;
        private boolean isStatic;

        private InnerClassInitAdder(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (this.className.equals(name)) {
                this.outerDescriptor = "L" + outerName + ";";
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ((access & OUTER_FIELD) == OUTER_FIELD && descriptor.equals(this.outerDescriptor)) {
                this.outerField = name;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ("<init>".equals(name)) {
                this.hasConstructor = true;
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            if (!this.hasConstructor && !this.isStatic && this.outerDescriptor != null && this.outerField != null) {
                var method = this.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                        "<init>", "(" + this.outerDescriptor + ")V", null, null);
                method.visitVarInsn(Opcodes.ALOAD, 0);
                method.visitVarInsn(Opcodes.ALOAD, 1);
                method.visitFieldInsn(Opcodes.PUTFIELD, this.className, this.outerField, this.outerDescriptor);
                method.visitInsn(Opcodes.RETURN);
            }
            super.visitEnd();
        }

    }

    /**
     * javac counts the synthetic leading arguments of enum and inner class constructors when it writes the parameter
     * annotation attributes, but the language does not. Drop the leading entries so the counts line up.
     */
    private static final class ParameterAnnotationFixer extends ClassVisitor {

        private final ClassNode node;

        private ParameterAnnotationFixer(ClassVisitor next, ClassNode node) {
            super(Opcodes.ASM9, next);
            this.node = node;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            var synthetic = this.syntheticParameters();
            if (synthetic == null) {
                return;
            }
            for (var method : this.node.methods) {
                if ("<init>".equals(method.name) && beginsWith(Type.getArgumentTypes(method.desc), synthetic)) {
                    method.visibleParameterAnnotations = trim(method.visibleParameterAnnotations,
                            Type.getArgumentTypes(method.desc).length, synthetic.length);
                    method.invisibleParameterAnnotations = trim(method.invisibleParameterAnnotations,
                            Type.getArgumentTypes(method.desc).length, synthetic.length);
                    if (method.visibleParameterAnnotations != null) {
                        method.visibleAnnotableParameterCount = method.visibleParameterAnnotations.length;
                    }
                    if (method.invisibleParameterAnnotations != null) {
                        method.invisibleAnnotableParameterCount = method.invisibleParameterAnnotations.length;
                    }
                }
            }
        }

        /** @return the arguments javac prepends to this class' constructors, or {@code null} if it prepends none */
        private Type[] syntheticParameters() {
            if ((this.node.access & Opcodes.ACC_ENUM) != 0) {
                return new Type[] {Type.getObjectType("java/lang/String"), Type.INT_TYPE};
            }
            InnerClassNode self = null;
            for (var inner : this.node.innerClasses) {
                if (inner.name.equals(this.node.name)) {
                    self = inner;
                    break;
                }
            }
            if (self == null || self.innerName == null || (self.access & (Opcodes.ACC_STATIC | Opcodes.ACC_INTERFACE)) != 0) {
                return null;
            }
            return new Type[] {Type.getObjectType(self.outerName)};
        }

        private static boolean beginsWith(Type[] values, Type[] prefix) {
            if (values.length < prefix.length) {
                return false;
            }
            for (var index = 0; index < prefix.length; index++) {
                if (!values[index].equals(prefix[index])) {
                    return false;
                }
            }
            return true;
        }

        private static List<AnnotationNode>[] trim(List<AnnotationNode>[] annotations, int parameters, int synthetic) {
            if (annotations == null || annotations.length != parameters) {
                return annotations;
            }
            return Arrays.copyOfRange(annotations, synthetic, annotations.length);
        }

    }

    /**
     * Replaces the snowman placeholders the renamer left in the local variable table with
     * {@code lvt_<slot>_<occurrence>_}, so the names stay the same from one build to the next.
     */
    private static final class PlaceholderNamer extends ClassVisitor {

        private final ClassNode node;

        private PlaceholderNamer(ClassVisitor next, ClassNode node) {
            super(Opcodes.ASM9, next);
            this.node = node;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            for (var method : this.node.methods) {
                if (method.localVariables == null || method.localVariables.isEmpty()) {
                    continue;
                }
                method.localVariables.sort(Comparator
                        .comparingInt((LocalVariableNode local) -> local.index)
                        .thenComparingInt(local -> method.instructions.indexOf(local.start)));
                int[] occurrences = null;
                for (var local : method.localVariables) {
                    if (local.name.isEmpty() || local.name.charAt(0) != PLACEHOLDER) {
                        continue;
                    }
                    if (occurrences == null) {
                        occurrences = new int[highestSlot(method.localVariables) + 1];
                    }
                    local.name = "lvt_" + local.index + "_" + ++occurrences[local.index] + "_";
                }
            }
        }

        private static int highestSlot(List<LocalVariableNode> locals) {
            var highest = 0;
            for (var local : locals) {
                highest = Math.max(highest, local.index);
            }
            return highest;
        }

    }

}
