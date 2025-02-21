package com.cleanroommc.gradle.api.task;

import com.cleanroommc.gradle.api.util.lazy.LazyStringable;
import com.cleanroommc.gradle.api.util.lazy.Streams;
import kotlin.jvm.functions.Function0;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaExecSpec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public abstract class LazilyConstructedJavaExec extends JavaExec {

    private static Object mapToLazyString(Object object) {
        if (object instanceof Provider<?> provider) {
            return LazyStringable.of(provider);
        }
        if (object instanceof Callable<?> callable) {
            return LazyStringable.of(callable);
        }
        if (object instanceof Supplier<?> supplier) {
            return LazyStringable.of(supplier);
        }
        if (object instanceof Function0<?> function) {
            return LazyStringable.of(function);
        }
        return object;
    }

    @Internal
    public abstract RegularFileProperty getLogFile();

    @Override
    @TaskAction
    public void exec() {
        getWorkingDir().mkdirs();
        if (getLogFile().isPresent()) {
            try {
                setStandardOutput(FileUtils.newOutputStream(getLogFile().get().getAsFile(), false));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        beforeExec();
        super.exec();
    }

    protected void beforeExec() { }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        var newArguments = Streams.of(arguments).map(LazilyConstructedJavaExec::mapToLazyString).toList();
        super.setAllJvmArgs(newArguments);
    }

    @Override
    public void setJvmArgs(Iterable<?> arguments) {
        super.setJvmArgs(Streams.of(arguments).map(LazilyConstructedJavaExec::mapToLazyString).toList());
    }

    @Override
    public JavaExec jvmArgs(Iterable<?> arguments) {
        return super.jvmArgs(Streams.of(arguments).map(LazilyConstructedJavaExec::mapToLazyString).toList());
    }

    @Override
    public JavaExec jvmArgs(Object... arguments) {
        return jvmArgs(List.of(arguments));
    }

    @Override
    public JavaExec setArgs(Iterable<?> applicationArgs) {
        return super.setArgs(Streams.of(applicationArgs).map(LazilyConstructedJavaExec::mapToLazyString).toList());
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        return super.args(Streams.of(args).map(LazilyConstructedJavaExec::mapToLazyString).toList());
    }

    @Override
    public JavaExec args(Object... args) {
        return (JavaExec) args(List.of(args));
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        super.setEnvironment(Streams.convertValues(environmentVariables, LazilyConstructedJavaExec::mapToLazyString));
    }

    @Override
    public JavaExec environment(String name, Object value) {
        return super.environment(name, mapToLazyString(value));
    }

    @Override
    public JavaExec environment(Map<String, ?> environmentVariables) {
        return super.environment(Streams.convertValues(environmentVariables, LazilyConstructedJavaExec::mapToLazyString));
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        super.setSystemProperties(Streams.convertValues(properties, LazilyConstructedJavaExec::mapToLazyString));
    }

    @Override
    public JavaExec systemProperties(Map<String, ?> properties) {
        return super.systemProperties(Streams.convertValues(properties, LazilyConstructedJavaExec::mapToLazyString));
    }

    @Override
    public JavaExec systemProperty(String name, Object value) {
        return super.systemProperty(name, mapToLazyString(value));
    }

}
