package com.cleanroommc.gradle.tasks.jarmanipulation;

import com.cleanroommc.gradle.api.DelegatedPatternFilterable;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static com.cleanroommc.gradle.Constants.*;

// TODO: Need to update this to work with modern versions where jars are structured differently
public class SplitServerJarTask extends DefaultTask implements DelegatedPatternFilterable<PatternSet> {

    public static void setupSplitJarTask(Project project) {
        SplitServerJarTask splitServerJarTask = Utils.createTask(project, SPLIT_SERVER_JAR_TASK, SplitServerJarTask.class);
        splitServerJarTask.setRawJar(Utils.closure(() -> MINECRAFT_SERVER_FILE.apply(MinecraftExtension.get(project).getVersion())));
        splitServerJarTask.setPureJar(Utils.closure(() -> MINECRAFT_SERVER_PURE_FILE.apply(MinecraftExtension.get(project).getVersion())));
        splitServerJarTask.setDependenciesJar(Utils.closure(() -> MINECRAFT_SERVER_FILE_WITH_DEPS.apply(MinecraftExtension.get(project).getVersion())));
        // TODO: these change in different versions... Works with 1.12 though
        splitServerJarTask.exclude("org/bouncycastle", "org/bouncycastle/*", "org/bouncycastle/**");
        splitServerJarTask.exclude("org/apache", "org/apache/*", "org/apache/**");
        splitServerJarTask.exclude("com/google", "com/google/*", "com/google/**");
        splitServerJarTask.exclude("com/mojang/authlib", "com/mojang/authlib/*", "com/mojang/authlib/**");
        splitServerJarTask.exclude("com/mojang/util", "com/mojang/util/*", "com/mojang/util/**");
        splitServerJarTask.exclude("gnu/trove", "gnu/trove/*", "gnu/trove/**");
        splitServerJarTask.exclude("io/netty", "io/netty/*", "io/netty/**");
        splitServerJarTask.exclude("javax/annotation", "javax/annotation/*", "javax/annotation/**");
        splitServerJarTask.exclude("argo", "argo/*", "argo/**");
        splitServerJarTask.exclude("it", "it/*", "it/**");
        splitServerJarTask.dependsOn(Utils.getTask(project, DL_MINECRAFT_SERVER_TASK));
    }

    @Input private PatternSet patternSet = new PatternSet();

    @InputFile private Closure<File> rawJar;
    @OutputFile private Closure<File> pureJar;
    @OutputFile private Closure<File> dependenciesJar;

    @TaskAction
    public void run() throws IOException {
        final Spec<FileTreeElement> spec = patternSet.getAsSpec();
        File input = getRawJar();
        File out1 = getPureJar();
        File out2 = getDependenciesJar();
        out1.getParentFile().mkdirs();
        out2.getParentFile().mkdirs();
        // Read Jar
        try (JarOutputStream zout1 = new JarOutputStream(new FileOutputStream(out1));
             JarOutputStream zout2 = new JarOutputStream(new FileOutputStream(out2))) {
            getProject().zipTree(input).visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails details) { }

                @Override
                public void visitFile(FileVisitDetails details) {
                    JarEntry entry = new JarEntry(details.getPath());
                    entry.setSize(details.getSize());
                    entry.setTime(details.getLastModified());
                    try {
                        if (spec.isSatisfiedBy(details)) {
                            zout1.putNextEntry(entry);
                            details.copyTo(zout1);
                            zout1.closeEntry();
                        } else {
                            zout2.putNextEntry(entry);
                            details.copyTo(zout2);
                            zout2.closeEntry();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    public File getRawJar() {
        return rawJar.call();
    }

    public void setRawJar(Closure<File> rawJar) {
        this.rawJar = rawJar;
    }

    public File getPureJar() {
        return pureJar.call();
    }

    public void setPureJar(Closure<File> pureJar) {
        this.pureJar = pureJar;
    }

    public File getDependenciesJar() {
        return dependenciesJar.call();
    }

    public void setDependenciesJar(Closure<File> dependenciesJar) {
        this.dependenciesJar = dependenciesJar;
    }

    public void setPatternSet(PatternSet patternSet) {
        this.patternSet = patternSet;
    }

    @Override
    public PatternSet getDelegated() {
        return patternSet;
    }

}
