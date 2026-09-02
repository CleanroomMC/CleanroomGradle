package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.task.patch.GenerateDiffs;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;

public abstract class PatchDevEnvironment implements Named {

    public abstract DirectoryProperty getInput();

    public abstract DirectoryProperty getPatches();

    public abstract DirectoryProperty getOutput();

    private final String name;

    private String dependsOn;
    private NamedDomainObjectProvider<SourceSet> sourceSet;
    private TaskProvider<Copy> prepareSources;
    private TaskProvider<DefaultTask> initializeEnvironment, prepareEnvironment;
    private TaskProvider<ApplyDiffs> initializeDiffs, applyDiffs;
    private TaskProvider<GenerateDiffs> generateDiffs;

    @Inject
    public PatchDevEnvironment(String name, ProjectLayout layout) {
        this.name = name;
        this.getPatches().convention(layout.getProjectDirectory().dir("patches").dir(name));
        this.getOutput().convention(layout.getProjectDirectory().dir("src/" + name + "PatchDev/java"));
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void dependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    public NamedDomainObjectProvider<SourceSet> getSourceSet() {
        return this.sourceSet;
    }

    public TaskProvider<DefaultTask> getPrepareEnvironment() {
        return prepareEnvironment;
    }

    public TaskProvider<ApplyDiffs> getApplyDiffs() {
        return applyDiffs;
    }

    public TaskProvider<ApplyDiffs> getInitializeDiffs() {
        return initializeDiffs;
    }

    public TaskProvider<GenerateDiffs> getGenerateDiffs() {
        return generateDiffs;
    }

    void registerTasks(Project project, DirectoryProperty localCache) {
        var name = this.name;

        this.sourceSet = SourceSets.internal(project, name + "PatchDev");

        var groupName = name + " patch development";
        var capitalizedName = StringUtils.capitalize(name);

        var patchDevDir = localCache.dir("patchDev/" + name);
        var sourcesDir = patchDevDir.map(dir -> dir.dir("sources").getAsFile());
        var dirtyDir = patchDevDir.map(dir -> dir.dir("dirty"));
        var patchesZip = patchDevDir.map(dir -> dir.file("patches.zip").getAsFile());
        var input = this.getInput().map(Directory::getAsFile);
        var output = this.getOutput();
        var patches = this.getPatches();

        SourceSets.linkSource(this.sourceSet, output);

        this.initializeEnvironment = Tasks.register(project, "initialize" + capitalizedName + "PatchDevEnvironment");
        this.prepareSources = Tasks.register(project, "prepare" + capitalizedName + "Sources", Copy.class);
        this.prepareSources.configure(task -> {
            task.from(input);
            task.into(sourcesDir);
        });
        this.prepareEnvironment = Tasks.register(project, "prepare" + capitalizedName + "PatchDevEnvironment");
        var applyTaskName = name.equals("initial") ? "applyInitialPatchDevDiffs" : "apply" + capitalizedName + "Diffs";
        this.applyDiffs = Tasks.register(project, applyTaskName, ApplyDiffs.class);
        this.initializeDiffs = Tasks.register(project, "initialize" + capitalizedName + "PatchDevSources", ApplyDiffs.class);
        this.generateDiffs = Tasks.register(project, "generate" + capitalizedName + "Diffs", GenerateDiffs.class);
        var zipPatches = Tasks.register(project, "zip" + capitalizedName + "Patches", Zip.class);
        zipPatches.configure(task -> {
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
            task.from(this.generateDiffs.flatMap(GenerateDiffs::getPatchesDirectory));
            task.getDestinationDirectory().fileProvider(patchesZip.map(File::getParentFile));
            task.getArchiveFileName().set(patchesZip.map(File::getName));
        });
        Tasks.group(groupName, this.prepareEnvironment, this.applyDiffs, this.generateDiffs, zipPatches);

        this.initializeEnvironment.configure(task -> {
            if (this.dependsOn != null) {
                task.dependsOn(this.dependsOn);
            }
            task.doLast($ -> {
                if (!input.isPresent()) {
                    throw new InvalidUserDataException("Input for %s must be set!".formatted(name));
                }
                createDirectory(input.get(), "input", name);
                createDirectory(sourcesDir.get(), "staged input", name);
                createDirectory(output.get().getAsFile(), "output", name);
                createDirectory(patches.get().getAsFile(), "patches", name);
            });
        });
        this.prepareSources.configure(task -> task.dependsOn(this.initializeEnvironment));
        this.applyDiffs.configure(task -> {
            task.dependsOn(this.prepareSources);
            task.setDescription("Recreates the " + name + " patch-development sources and applies the current patch set.");
            task.getOriginalDirectory().fileProvider(input);
            task.getPatchesDirectory().set(patches);
            task.getModifiedDirectory().set(output);
            task.getCleanOutput().set(true);
            task.getDirtyDirectory().set(dirtyDir);
        });
        this.initializeDiffs.configure(task -> {
            task.dependsOn(this.prepareSources);
            task.getOriginalDirectory().fileProvider(input);
            task.getPatchesDirectory().set(patches);
            task.getModifiedDirectory().set(output);
            task.onlyIf("patch dev source tree is not yet populated", $ -> {
                var dir = output.get().getAsFile();
                var contents = dir.listFiles();
                return contents == null || contents.length == 0;
            });
        });
        this.prepareEnvironment.configure(task -> {
            task.dependsOn(this.initializeDiffs);
            task.doLast($ -> createDirectory(sourcesDir.get(), "staged input", name));
        });
        this.generateDiffs.configure(task -> {
            task.dependsOn(this.prepareEnvironment);
            task.getOriginalDirectory().fileProvider(sourcesDir);
            task.getModifiedDirectory().set(output);
            task.getPatchesDirectory().set(patches);
        });
    }

    private static void createDirectory(File directory, String property, String environment) {
        if (directory.isDirectory()) {
            return;
        }
        if (directory.exists() || !directory.mkdirs()) {
            throw new InvalidUserDataException("%s for %s is not a directory and could not be created!".formatted(property, environment));
        }
    }

}
