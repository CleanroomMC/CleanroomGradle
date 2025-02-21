package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.patch.GenerateDiffs;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

import java.util.Locale;

public final class PatchDevelopmentTasks {

    private final Provider<Directory> directory;
    private final NamedDomainObjectProvider<SourceSet> sourceSet;
    private final TaskProvider<Copy> prepareSources, copySources;
    private final TaskProvider<GenerateDiffs> generateDiffs;
    private final TaskProvider<Zip> zipPatches;

    public PatchDevelopmentTasks(Project project, String name, CleanroomExtension ext, Object sourceLocation, Object... dependencies) {
        var lowerName = name.toLowerCase(Locale.ENGLISH);
        var setName = lowerName + "PatchDev";
        var groupName = lowerName + " patch development tasks";

        this.directory = ext.getLocalCacheDirectory().dir(groupName.replace(" ", "_"));
        var sourceDirectory = this.directory.map(dir -> dir.file("sources"));
        var patchesZip = this.directory.map(dir -> dir.file("patches.zip").getAsFile());

        this.sourceSet = SourceSets.of(project, setName);
        var sourceSetDir = SourceSets.source(this.sourceSet);

        this.prepareSources = Tasks.unzip(project, groupName, "prepare" + name + "Sources", sourceLocation, sourceDirectory);
        this.copySources = Tasks.copy(project, groupName, "copy" + name + "Sources", sourceDirectory, sourceSetDir);
        this.generateDiffs = Tasks.of(project, groupName, "generate" + name + "Diffs", GenerateDiffs.class);
        this.zipPatches = Tasks.zip(project, groupName, "zip" + name + "Patches", this.generateDiffs.map(GenerateDiffs::getPatchesDirectory), patchesZip);

        this.sourceSet.configure(sourceSet -> Tasks.named(project, sourceSet.getClassesTaskName()).configure(task -> task.setGroup(groupName)));
        this.prepareSources.configure(task -> task.dependsOn(dependencies));
        this.copySources.configure(task -> task.dependsOn(this.prepareSources));
        this.generateDiffs.configure(task -> {
            task.dependsOn(this.copySources);

            task.getOriginalDirectory().fileProvider(sourceDirectory.map(RegularFile::getAsFile));
            task.getModifiedDirectory().fileProvider(sourceSetDir);
            task.getPatchesDirectory().fileProvider(this.directory.map(dir -> dir.file("patches").getAsFile()));
        });
        this.zipPatches.configure(task -> task.dependsOn(this.generateDiffs));
    }

    public NamedDomainObjectProvider<SourceSet> sourceSet() {
        return sourceSet;
    }

}
