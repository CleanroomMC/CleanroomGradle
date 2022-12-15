package com.cleanroommc.gradle.tasks.makerun;

import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.objectweb.asm.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.MAKE_RUN_TASK;

public abstract class MakeRunTask extends DefaultTask implements Opcodes {

    public static TaskProvider<MakeRunTask> setupMakeRunTask(Project project) {
        return Utils.prepareTask(project, MAKE_RUN_TASK, MakeRunTask.class);
    }

    @TaskAction
    public void task$makeRun() {
        MinecraftExtension mcExt = MinecraftExtension.get(Constants.PROJECT);
        byte[] classBytes = makeClass(mcExt.getVersion());
        writeClassBytesToFile(classBytes, mcExt.getVersion());
    }

    private byte[] makeClass(String version) {
        ClassWriter writer = new ClassWriter(0);
        // writer.visit(V1_8, ACC_PUBLIC, "com/cleanroommc/gradle/generated/" + getRunType().get(), null, "java/lang/Object", null);
        writer.visit(V1_8, ACC_PUBLIC, getRunType().get().toString(), null, "java/lang/Object", null);

        // Constructor (won't be instantiated)
        MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(1, 1);

        MethodVisitor main = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, new String[] { "java/lang/ReflectiveOperationException"} );

        Label startTry = new Label();
        Label endTry = new Label();
        Label startCatch = new Label();
        main.visitTryCatchBlock(startTry, endTry, startCatch, "java/lang/Throwable");

        Label a = new Label();
        main.visitLabel(a);
        main.visitLdcInsn("java.library.path");
        main.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
        main.visitVarInsn(ASTORE, 1);

        Label b = new Label();
        main.visitLabel(b);
        main.visitLdcInsn(Constants.EXTRACTED_NATIVES_FOLDER.apply(version).toString());
        main.visitVarInsn(ASTORE, 2);

        Label c = new Label();
        Label d = new Label();
        main.visitLabel(c);
        main.visitVarInsn(ALOAD, 1);
        main.visitJumpInsn(IFNULL, d);
        main.visitVarInsn(ALOAD, 1);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
        Label e = new Label();
        main.visitJumpInsn(IFEQ, e);

        main.visitLabel(d);
        main.visitFrame(F_APPEND, 2, new Object[] { "java/lang/String", "java/lang/String" }, 0, new Object[0]);
        main.visitVarInsn(ALOAD, 2);
        main.visitVarInsn(ASTORE, 1);
        Label f = new Label();
        main.visitJumpInsn(GOTO, f);

        main.visitLabel(e);
        main.visitFrame(F_SAME, 0, new Object[0], 0, new Object[0]);
        main.visitTypeInsn(NEW, "java/lang/StringBuilder");
        main.visitInsn(DUP);
        main.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        main.visitVarInsn(ALOAD, 1);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        main.visitFieldInsn(GETSTATIC, "java/io/File", "pathSeparator", "Ljava/lang/String;");
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        main.visitVarInsn(ALOAD, 2);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        main.visitVarInsn(ASTORE, 1);

        main.visitLabel(f);
        main.visitFrame(F_SAME, 0, new Object[0], 0, new Object[0]);
        main.visitLdcInsn("java.library.path");
        main.visitVarInsn(ALOAD, 1);
        main.visitMethodInsn(INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        main.visitInsn(POP);

        main.visitLabel(startTry);
        main.visitLdcInsn(Type.getObjectType("java/lang/ClassLoader"));
        main.visitLdcInsn("sys_paths");
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        main.visitVarInsn(ASTORE, 3);

        Label g = new Label();
        main.visitLabel(g);
        main.visitVarInsn(ALOAD, 3);
        main.visitInsn(ICONST_1);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);

        Label h = new Label();
        main.visitLabel(h);
        main.visitVarInsn(ALOAD, 3);
        main.visitInsn(ACONST_NULL);
        main.visitInsn(ACONST_NULL);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);

        main.visitLabel(endTry);
        Label i = new Label();
        main.visitJumpInsn(GOTO, i);

        main.visitLabel(startCatch);
        main.visitFrame(F_SAME1, 0, new Object[0], 1, new Object[] { "java/lang/Throwable" });
        main.visitVarInsn(ASTORE, 3);

        main.visitLabel(i);
        main.visitFrame(F_SAME, 0, new Object[0], 0, new Object[0]);
        main.visitLdcInsn("net.minecraft.client.main.Main"); // TODO: configurable
        main.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        main.visitLdcInsn("main");
        main.visitInsn(ICONST_1);
        main.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        main.visitInsn(DUP);
        main.visitInsn(ICONST_0);
        main.visitLdcInsn(Type.getObjectType("[Ljava/lang/String;"));
        main.visitInsn(AASTORE);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        main.visitInsn(ACONST_NULL);
        main.visitInsn(ICONST_1);
        main.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        main.visitInsn(DUP);
        main.visitInsn(ICONST_0);
        main.visitVarInsn(ALOAD, 0);
        main.visitInsn(AASTORE);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        main.visitInsn(POP);

        Label j = new Label();
        main.visitLabel(j);
        main.visitInsn(RETURN);

        Label k = new Label();
        main.visitLabel(k);
        main.visitLocalVariable("a", "Ljava/lang/reflect/Field;", null, g, endTry, 3);
        main.visitLocalVariable("b", "[Ljava/lang/String;", null, a, k, 0);
        main.visitLocalVariable("c", "[Ljava/lang/String;", null, b, k, 1);
        main.visitLocalVariable("d", "[Ljava/lang/String;", null, c, k, 2);
        main.visitMaxs(6, 4);

        return writer.toByteArray();
    }

    private void writeClassBytesToFile(byte[] classBytes, String version) {
        FileOutputStream stream = null;
        try {
            File folder = Constants.MAKE_RUNS_FOLDER.apply(version);
            folder.mkdirs();
            stream = new FileOutputStream(new File(folder, getRunType().get() + ".class"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            stream.write(classBytes);
            stream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Input
    public abstract Property<RunType> getRunType();

}
