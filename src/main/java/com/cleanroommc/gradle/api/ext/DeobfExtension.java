package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.deobf.DeobfAttributes;
import com.cleanroommc.gradle.api.deobf.DeobfHandler;
import com.cleanroommc.gradle.api.deobf.DeobfuscateJar;
import com.cleanroommc.gradle.api.deobf.ExtractUserdevDeobfInputs;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.util.CleanroomProblems;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * Registration facade for {@code deobf(...)}.
 * <p>The mappings and library collections are filled in by the mode wiring after evaluation.
 * Read lazily by the transform.
 */
public abstract class DeobfExtension {

    public abstract ConfigurableFileCollection getRenamerClasspath();

    public abstract ConfigurableFileCollection getMappings();

    public abstract ConfigurableFileCollection getSrgLibraries();

    protected abstract ConfigurableFileCollection getUserdevInputs();

    @Inject
    public DeobfExtension(Project project) {
        var objects = project.getObjects();

        var dependencies = project.getDependencies();
        dependencies.getArtifactTypes().named(ArtifactTypeDefinition.JAR_TYPE, type ->
                type.getAttributes().attribute(DeobfAttributes.DEOBFUSCATED, DeobfAttributes.NONE));
        dependencies.getExtensions().create("deobf", DeobfHandler.class, dependencies, objects);

        // The renamer tool classpath is owned by the renamer plugin. Reading it off a never-executed
        // RenameJar keeps the transform on the exact artifact every other remap task uses.
        var renamerTask = Tasks.register(project, "deobfRenamerClasspath", RenameJar.class,
                project.getExtensions().getByType(RenamerExtension.class));
        // Realized through a plain callable so the transform neither runs nor depends on that task.
        getRenamerClasspath().convention((Callable<Object>) () -> renamerTask.get().getClasspath().getFiles());

        Tasks.register(project, "prepareDeobf").configure(task -> {
            task.setGroup("cleanroom");
            task.setDescription("Builds the mappings and SRG libraries needed to resolve deobf dependencies during IDE sync.");
            task.dependsOn(getMappings(), getSrgLibraries(), getRenamerClasspath());
        });

        dependencies.registerTransform(DeobfuscateJar.class, spec -> {
            spec.getFrom().attribute(DeobfAttributes.DEOBFUSCATED, DeobfAttributes.NONE)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            spec.getTo().attribute(DeobfAttributes.DEOBFUSCATED, DeobfAttributes.MCP)
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);

            var parameters = spec.getParameters();
            parameters.getMappings().from(getMappings());
            parameters.getSrgLibraries().from(getSrgLibraries());
            parameters.getUserdevInputs().from(getUserdevInputs());
            parameters.getRenamerClasspath().from(getRenamerClasspath());
            parameters.getJavaLanguageVersion().set(Runtime.version().feature());
            parameters.getJavaVendor().set(System.getProperty("java.vendor"));
        });

        dependencies.registerTransform(ExtractUserdevDeobfInputs.class, spec -> {
            spec.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    ArtifactTypeDefinition.JAR_TYPE);
            spec.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    DeobfAttributes.USERDEV_INPUTS_TYPE);
        });
    }

    public void useUserdev(Configuration userdev) {
        getUserdevInputs().from(userdev.getIncoming().artifactView(view -> view.getAttributes().attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, DeobfAttributes.USERDEV_INPUTS_TYPE)).getFiles());
    }

    public void wireTransformOrdering(Project project) {
        var ordering = project.getDependencyFactory()
                .create(project.files().builtBy(getMappings(), getSrgLibraries(), getRenamerClasspath()));
        project.getConfigurations().configureEach(configuration -> configuration.getDependencies().all(dependency -> {
            if (dependency instanceof ExternalModuleDependency module
                    && DeobfAttributes.MCP.equals(module.getAttributes().getAttribute(DeobfAttributes.DEOBFUSCATED))
                    && !configuration.getDependencies().contains(ordering)) {
                configuration.getDependencies().add(ordering);
            }
        }));
    }

    public static void rejectOnCompileClasspath(Project project, Problems problems) {
        var java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return;
        }
        // Only the main compile classpath closes the cycle, since that is what reobfJar takes as libraries
        var mainSourceSet = java.getSourceSets().findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (mainSourceSet == null) {
            return;
        }
        var compileClasspath = project.getConfigurations().findByName(mainSourceSet.getCompileClasspathConfigurationName());
        if (compileClasspath == null) {
            return;
        }
        // Checked at resolution rather than now, so dependencies added by any later callback are seen too
        compileClasspath.withDependencies(_ -> {
            for (var dependency : compileClasspath.getAllDependencies()) {
                if (dependency instanceof ExternalModuleDependency module
                        && DeobfAttributes.MCP.equals(module.getAttributes().getAttribute(DeobfAttributes.DEOBFUSCATED))) {
                    var message = "deobf(" + module.getGroup() + ":" + module.getName() + ") cannot be declared on the '"
                            + compileClasspath.getName() + "' hierarchy in loader mode, because remapping it needs the "
                            + "SRG-named Cleanroom jar that is built from this project's own compile classpath.";
                    throw CleanroomProblems.throwing(problems, new InvalidUserDataException(message),
                            CleanroomProblems.DEOBF_ON_COMPILE_CLASSPATH, message,
                            "Declare it on a runtime or test configuration instead, or switch to userdev mode.");
                }
            }
        });
    }

}
