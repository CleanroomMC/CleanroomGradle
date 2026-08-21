package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.source.BundledVersionMetaValueSource;
import com.cleanroommc.gradle.api.source.VersionMetaValueSource;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.task.patch.GenerateDiffs;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;

public abstract class CleanroomExtension {

    public static CleanroomExtension get(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class);
    }

    public abstract DirectoryProperty getCacheDirectory();

    public abstract DirectoryProperty getVersionCacheDirectory();

    public abstract DirectoryProperty getLocalCacheDirectory();

    public abstract Property<Boolean> getDiscardIntermediates();

    public abstract Property<String> getVersionMetaUrl();

    public abstract Property<VersionMeta> getVersionMeta();

    public abstract Property<Boolean> getDevelopInitialPatches();

    public abstract NamedDomainObjectContainer<PatchDevEnvironment> getPatchDev();

    public abstract NamedDomainObjectContainer<VanillaEnvironment> getVanilla();

    public abstract Property<ProjectMode> getMode();

    public abstract ConfigurableFileCollection getAccessTransformers();

    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    public abstract Property<String> getForgeVersion();

    // TODO: just getVersion, but we have getForgeVersion atm that will be removed.
    public abstract Property<String> getVersion();

    public abstract ListProperty<String> getLwjglNativesClassifiers();

    public abstract Property<String> getInstallerVersion();

    public abstract ListProperty<String> getInstallerJvmArgs();

    /**
     * Directory holding a hand-edited Tiny2 names source ({@code mappings.tiny}).
     * Unset by default as the pipeline uses the MCP CSVs from the {@code mcpMappings} dependency.
     */
    public abstract DirectoryProperty getNamesDirectory();

    public CleanroomExtension(Project project) {
        final var providers = project.getProviders();

        this.getCacheDirectory().fileValue(new File(project.getGradle().getGradleUserHomeDir(), "caches/" + Meta.CG_FOLDER));
        this.getVersionCacheDirectory().convention(this.getCacheDirectory().dir("versions/1.12.2"));
        this.getLocalCacheDirectory().convention(project.getLayout().getBuildDirectory().dir(Meta.CG_FOLDER));

        var versionMetaCacheFile = this.getVersionCacheDirectory().file("meta.json");
        var offline = project.getGradle().getStartParameter().isOffline();
        this.getVersionMeta().convention(
            this.getVersionMetaUrl()
                .flatMap(url -> providers.of(VersionMetaValueSource.class, spec -> {
                    spec.getParameters().getCacheFile().set(versionMetaCacheFile);
                    spec.getParameters().getVersionMetaUrl().set(url);
                    spec.getParameters().getOffline().set(offline);
                }))
                .orElse(providers.of(BundledVersionMetaValueSource.class, spec -> {}))
        );
        this.getDevelopInitialPatches().convention(false);
        this.getMode().convention(ProjectMode.USERDEV);
        this.getDiscardIntermediates().convention(
                providers.gradleProperty("cleanroom.discardIntermediates")
                        .map(Boolean::parseBoolean)
                        .orElse(this.getMode().map(mode -> mode != ProjectMode.LOADER)));
        this.getForgeVersion().convention("14.23.5.2864");
        this.getInstallerVersion().convention("0.1.0");
        this.getLwjglNativesClassifiers().convention(LwjglNatives.CLASSIFIERS);
        this.getPatchDev().all(env -> env.registerTasks(project, this.getLocalCacheDirectory()));
        this.getVanilla().all(env -> env.register(project, this));
    }

    public static abstract class PatchDevEnvironment implements Named {

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

        private void registerTasks(Project project, DirectoryProperty localCache) {
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
            this.prepareSources = Tasks.copy(project, "prepare" + capitalizedName + "Sources", input, sourcesDir);
            this.prepareEnvironment = Tasks.register(project, "prepare" + capitalizedName + "PatchDevEnvironment");
            var applyTaskName = name.equals("initial") ? "applyInitialPatchDevDiffs" : "apply" + capitalizedName + "Diffs";
            this.applyDiffs = Tasks.register(project, applyTaskName, ApplyDiffs.class);
            this.initializeDiffs = Tasks.register(project, "initialize" + capitalizedName + "PatchDevSources", ApplyDiffs.class);
            this.generateDiffs = Tasks.register(project, "generate" + capitalizedName + "Diffs", GenerateDiffs.class);
            var zipPatches = Tasks.zip(project, "zip" + capitalizedName + "Patches", this.generateDiffs.flatMap(GenerateDiffs::getPatchesDirectory), patchesZip);
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

}
