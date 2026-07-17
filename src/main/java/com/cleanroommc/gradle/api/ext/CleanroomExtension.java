package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.source.BundledVersionMetaValueSource;
import com.cleanroommc.gradle.api.source.VersionMetaValueSource;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.patch.GenerateDiffs;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

public abstract class CleanroomExtension {

    public static CleanroomExtension get(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class);
    }

    public abstract DirectoryProperty getCacheDirectory();

    public abstract DirectoryProperty getVersionCacheDirectory();

    public abstract DirectoryProperty getLocalCacheDirectory();

    public abstract Property<Boolean> getDebug();

    public abstract Property<String> getVersionMetaUrl();

    public abstract Property<VersionMeta> getVersionMeta();

    public abstract Property<Boolean> getDevelopInitialPatches();

    public abstract NamedDomainObjectContainer<PatchDevEnvironment> getPatchDev();

    public abstract Property<Boolean> getLoaderProject();

    public abstract ConfigurableFileCollection getAccessTransformers();

    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    public abstract Property<String> getForgeVersion();

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
        this.getDebug().convention(false);

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
        this.getLoaderProject().convention(false);
        this.getForgeVersion().convention("14.23.5.2864");

        project.afterEvaluate($ -> this.getPatchDev().all(env -> env.afterEvaluate(project, this.getLocalCacheDirectory())));
    }

    public static abstract class PatchDevEnvironment implements Named {

        public abstract DirectoryProperty getPatchesDirectory();

        // Not to use DirectoryProperty or RegularFileProperty here
        // As this can be a directory or file
        public abstract Property<File> getSource();

        private final String name;

        private String dependsOn;
        private NamedDomainObjectProvider<SourceSet> sourceSet;
        private TaskProvider<Copy> prepareSources;
        private TaskProvider<DefaultTask> prepareEnvironment;
        private TaskProvider<Copy> copyToSourceSet;
        private TaskProvider<GenerateDiffs> generateDiffs;
        private TaskProvider<Zip> zipPatches;

        @Inject
        public PatchDevEnvironment(String name, ProjectLayout layout) {
            this.name = name;
            this.getPatchesDirectory().convention(layout.getProjectDirectory().dir("patches").dir(name));
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

        public TaskProvider<Copy> getPrepareSources() {
            return prepareSources;
        }

        public TaskProvider<DefaultTask> getPrepareEnvironment() {
            return prepareEnvironment;
        }

        public TaskProvider<Copy> getCopyToSourceSet() {
            return copyToSourceSet;
        }

        public TaskProvider<GenerateDiffs> getGenerateDiffs() {
            return generateDiffs;
        }

        public TaskProvider<Zip> getZipPatches() {
            return zipPatches;
        }

        private void afterEvaluate(Project project, DirectoryProperty localCache) {
            var name = this.name;

            this.sourceSet = SourceSets.of(project, name + "PatchDev");

            var groupName = name + " patch development tasks";
            var capitalizedName = StringUtils.capitalize(name);

            var patchDevDir = localCache.dir("patchDev/" + name);
            var sourcesDir = patchDevDir.map(dir -> dir.dir("sources").getAsFile());
            var patchesZip = patchDevDir.map(dir -> dir.file("patches.zip").getAsFile());
            var source = this.getSource();

            this.prepareSources = Tasks.copy(project, groupName, "prepare" + capitalizedName + "Sources", source, sourcesDir);
            this.prepareEnvironment = Tasks.of(project, groupName, "prepare" + capitalizedName + "PatchDevEnvironment");
            this.copyToSourceSet = Tasks.copy(project, groupName, "copy" + capitalizedName + "ToSourceSet", sourcesDir, SourceSets.source(this.sourceSet));
            this.generateDiffs = Tasks.of(project, groupName, "generate" + capitalizedName + "Diffs", GenerateDiffs.class);
            this.zipPatches = Tasks.zip(project, groupName, "zip" + capitalizedName + "Patches", this.generateDiffs.map(GenerateDiffs::getPatchesDirectory), patchesZip);

            this.prepareSources.configure(task -> {
                if (this.dependsOn != null) {
                    task.dependsOn(this.dependsOn);
                }
            });
            this.copyToSourceSet.configure(task -> task.dependsOn(this.prepareSources));
            this.prepareEnvironment.configure(task -> {
                task.dependsOn(this.copyToSourceSet);
                task.doLast($ -> {
                    if (!source.isPresent()) {
                        throw new InvalidUserDataException("source for %s must be set!".formatted(name));
                    }
                    var file = source.get();
                    if (!file.isDirectory()) {
                        if (file.isFile()) {
                            try (var zipIn = IO.zipIn(file)) {
                                if (zipIn.getNextEntry() == null) {
                                    throw new IOException("Zip is empty.");
                                }
                            } catch (IOException e) {
                                throw new InvalidUserDataException("source for %s is an invalid zip!".formatted(name));
                            }
                        } else {
                            throw new InvalidUserDataException("source for %s is invalid!".formatted(name));
                        }
                    }
                });
            });
            this.generateDiffs.configure(task -> {
                task.dependsOn(this.copyToSourceSet);
                task.getOriginalDirectory().fileProvider(sourcesDir);
                task.getModifiedDirectory().fileProvider(SourceSets.source(this.sourceSet));
                task.getPatchesDirectory().value(this.getPatchesDirectory());
            });
            this.zipPatches.configure(task -> task.dependsOn(this.generateDiffs));
        }

    }

}
