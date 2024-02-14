package com.cleanroommc.gradle.api.named.task.type;

import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.lazy.Streams;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaExecSpec;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class LazilyConstructedJavaExec extends JavaExec {

    private static Object mapToLazyIfPossible(Object object) {
        if (object instanceof Provider<?> provider) {
            return Providers.stringable(provider);
        }
        return object;
    }

    @Internal
    public abstract RegularFileProperty getLogFile();

    @Override
    @TaskAction
    public void exec() {
        beforeExec();
        getWorkingDir().mkdirs();
        if (getLogFile().isPresent()) {
            try {
                setStandardOutput(FileUtils.newOutputStream(getLogFile().get().getAsFile(), false));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        super.exec();
    }

    protected void beforeExec() { }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        var newArguments = Streams.of(arguments).map(LazilyConstructedJavaExec::mapToLazyIfPossible).toList();
        super.setAllJvmArgs(newArguments);
    }

    @Override
    public void setJvmArgs(Iterable<?> arguments) {
        super.setJvmArgs(Streams.of(arguments).map(LazilyConstructedJavaExec::mapToLazyIfPossible).toList());
    }

    @Override
    public JavaExec jvmArgs(Iterable<?> arguments) {
        return super.jvmArgs(Streams.of(arguments).map(LazilyConstructedJavaExec::mapToLazyIfPossible).toList());
    }

    @Override
    public JavaExec jvmArgs(Object... arguments) {
        return jvmArgs(List.of(arguments));
    }

    @Override
    public JavaExec setArgs(Iterable<?> applicationArgs) {
        return super.setArgs(Streams.of(applicationArgs).map(LazilyConstructedJavaExec::mapToLazyIfPossible).toList());
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        return super.args(Streams.of(args).map(LazilyConstructedJavaExec::mapToLazyIfPossible).toList());
    }

    @Override
    public JavaExec args(Object... args) {
        return (JavaExec) args(List.of(args));
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        super.setEnvironment(Streams.convertValues(environmentVariables, LazilyConstructedJavaExec::mapToLazyIfPossible));
    }

    @Override
    public JavaExec environment(String name, Object value) {
        return super.environment(name, mapToLazyIfPossible(value));
    }

    @Override
    public JavaExec environment(Map<String, ?> environmentVariables) {
        return super.environment(Streams.convertValues(environmentVariables, LazilyConstructedJavaExec::mapToLazyIfPossible));
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        super.setSystemProperties(Streams.convertValues(properties, LazilyConstructedJavaExec::mapToLazyIfPossible));
    }

    @Override
    public JavaExec systemProperties(Map<String, ?> properties) {
        return super.systemProperties(Streams.convertValues(properties, LazilyConstructedJavaExec::mapToLazyIfPossible));
    }

    @Override
    public JavaExec systemProperty(String name, Object value) {
        return super.systemProperty(name, mapToLazyIfPossible(value));
    }

}
