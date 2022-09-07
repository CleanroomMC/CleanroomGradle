package com.cleanroommc.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.internal.jvm.Jvm;
import org.slf4j.Logger;

import java.io.*;
import java.util.List;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;

public abstract class JarExec extends DefaultTask {
    
    protected final Directory workDirectory = getProject().getLayout().getProjectDirectory().dir("logs").dir(getName());
    protected final Provider<RegularFile> logFileProvider = workDirectory.file(getProject().provider(() -> "log.txt"));

    @TaskAction
    public void javaExec$run() throws IOException {
        File jarFile = getJar().get().getAsFile();
        JarFile jar = new JarFile(jarFile);
        String mainClass = jar.getManifest().getMainAttributes().getValue(Name.MAIN_CLASS);
        jar.close();
        Logger logger = getProject().getLogger();
        File logFile = logFileProvider.get().getAsFile();
        if (logFile.getParentFile() != null && !logFile.getParentFile().exists() && !logFile.getParentFile().mkdirs()) {
            logger.warn("Could not create parent directory [{}] for log file", logFile.getParentFile().getAbsolutePath());
        }
        List<String> arguments = getArguments().get();
        ConfigurableFileCollection classpath = getProject().files(getJar(), getClasspath());
        File workingDir = workDirectory.getAsFile();
        try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFile), true)) {
            getProject().javaexec(spec -> {
                spec.setExecutable(Jvm.current().getJavaExecutable().getAbsolutePath());
               // spec.setDebug(false);
                spec.setArgs(arguments);
                spec.setClasspath(classpath);
                spec.setWorkingDir(workingDir);
                spec.getMainClass().set(mainClass);
                spec.setStandardOutput(new OutputStream() {

                    @Override
                    public void flush() {
                        logWriter.flush();
                    }

                    @Override
                    public void close() {}

                    @Override
                    public void write(int b) {
                        logWriter.write(b);
                    }

                });
            }).rethrowFailure().assertNormalExitValue();
        }
        final String[] workingDirContents = workingDir.list();
        if ((workingDirContents == null || workingDirContents.length == 0) && !workingDir.delete()) {
            logger.warn("Could not delete empty working directory '{}'", workingDir.getAbsolutePath());
        }
    }

    @InputFile
    public abstract Provider<RegularFile> getJar();

    @Input
    public abstract ListProperty<String> getArguments();

    @Optional
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();



}
