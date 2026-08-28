package com.cleanroommc.gradle.api.deobf;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * Renames a published SRG-named mod jar into this project's MCP names.
 */
@CacheableTransform
public abstract class DeobfuscateJar implements TransformAction<DeobfuscateJar.Parameters> {

    public interface Parameters extends TransformParameters {

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getMappings();

        @InputFiles
        @CompileClasspath
        ConfigurableFileCollection getSrgLibraries();

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        ConfigurableFileCollection getUserdevInputs();

        @Classpath
        ConfigurableFileCollection getRenamerClasspath();

        /** JVM identity rather than its executable path, so cached entries stay relocatable. */
        @Input
        Property<Integer> getJavaLanguageVersion();

        @Input
        Property<String> getJavaVendor();

    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    /**
     * The mod's own resolved graph.
     * The renamer needs it to see the type hierarchy a mod inherits from its other dependencies.
     */
    @CompileClasspath
    @InputArtifactDependencies
    public abstract FileCollection getArtifactDependencies();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void transform(TransformOutputs outputs) {
        var parameters = getParameters();
        var declared = parameters.getMappings().getFiles();
        var mappings = new ArrayList<>(declared.stream().filter(File::isFile).toList());
        var libraries = new ArrayList<>(parameters.getSrgLibraries().getFiles());
        for (var directory : parameters.getUserdevInputs()) {
            mappings.add(new File(directory, UserdevConfig.SRG2MCP));
            libraries.add(new File(directory, UserdevConfig.DEOBF_LIBRARY));
        }
        if (declared.isEmpty() && mappings.isEmpty()) {
            throw new InvalidUserDataException("deobf() needs MCP mappings, which this project's mode does not build. "
                    + "Set cleanroom.mode to 'userdev' or 'loader'.");
        }
        if (mappings.stream().noneMatch(File::isFile)) {
            throw new InvalidUserDataException("The MCP mappings have not been written yet: " + declared + ". "
                    + "Run './gradlew prepareDeobf' before importing or refreshing the project in an IDE.");
        }

        var input = getInputArtifact().get().getAsFile();
        var name = input.getName();
        var baseName = name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
        var output = outputs.file(baseName + "-deobf.jar");

        var arguments = new ArrayList<String>();
        arguments.add("--input");
        arguments.add(input.getAbsolutePath());
        for (var mapping : mappings) {
            arguments.add("--map");
            arguments.add(mapping.getAbsolutePath());
        }
        arguments.add("--output");
        arguments.add(output.getAbsolutePath());
        for (var library : libraries) {
            arguments.add("--lib");
            arguments.add(library.getAbsolutePath());
        }
        for (var dependency : getArtifactDependencies()) {
            arguments.add("--lib");
            arguments.add(dependency.getAbsolutePath());
        }

        var renamer = parameters.getRenamerClasspath();
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(renamer);
            spec.getMainClass().set(mainClassOf(renamer));
            spec.setArgs(arguments);
        });
    }

    /**
     * The renamer ships as an executable fat jar, so its entry point comes from the manifest rather than
     * from any property the renamer plugin exposes.
     */
    private static String mainClassOf(Iterable<File> classpath) {
        for (var file : classpath) {
            try (var jar = new JarFile(file)) {
                var manifest = jar.getManifest();
                if (manifest != null) {
                    var mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                    if (mainClass != null) {
                        return mainClass;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        throw new InvalidUserDataException("No Main-Class found on the renamer classpath: " + classpath);
    }

}
