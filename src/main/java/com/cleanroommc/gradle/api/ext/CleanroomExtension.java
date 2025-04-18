package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.Manifest;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.patch.GenerateDiffs;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import de.undercouch.gradle.tasks.download.DownloadAction;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.*;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

public abstract class CleanroomExtension {

    public static CleanroomExtension get(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class);
    }

    @Inject
    public abstract Project getProject();

    public abstract DirectoryProperty getCacheDirectory();

    public abstract DirectoryProperty getVersionCacheDirectory();

    public abstract DirectoryProperty getLocalCacheDirectory();

    public abstract Property<Boolean> getDebug();

    public abstract Property<Manifest> getManifest();

    public abstract Property<VersionMeta> getVersionMeta();

    public abstract Property<Boolean> getDevelopInitialPatches();

    public abstract NamedDomainObjectContainer<PatchDevEnvironment> getPatchDev();

    public CleanroomExtension() {
        final var project = this.getProject();

        this.getCacheDirectory().fileValue(new File(project.getGradle().getGradleUserHomeDir(), "caches/" + Meta.CG_FOLDER));
        this.getVersionCacheDirectory().convention(this.getCacheDirectory().dir("versions/1.12.2"));
        this.getLocalCacheDirectory().convention(this.getProject().getLayout().getBuildDirectory().dir(Meta.CG_FOLDER));
        this.getDebug().convention(false);
        this.getManifest().convention(this.getCacheDirectory().map(dir -> {
            var file = dir.file("version_manifest.json").getAsFile();
            if (!file.exists()) {
                var downloadAction = new DownloadAction(project);
                downloadAction.src(Meta.VERSION_MANIFEST_V2_URL);
                downloadAction.dest(file);
                downloadAction.useETag(true);
                try {
                    downloadAction.execute().join();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return IO.readJson(file, Manifest.class);
        }));
        this.getVersionMeta().convention(this.getVersionCacheDirectory().map(dir -> {
            var file = dir.file("meta.json").getAsFile();
            if (!file.exists()) {
                var downloadAction = new DownloadAction(project);
                downloadAction.src(this.getManifest().map(manifest -> manifest.version("1.12.2").url));
                downloadAction.dest(file);
                downloadAction.useETag(true);
                try {
                    downloadAction.execute().join();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return IO.readJson(file, VersionMeta.class);
        }));
        this.getDevelopInitialPatches().convention(false);

        project.afterEvaluate($ -> this.getPatchDev().all(PatchDevEnvironment::afterEvaluate));
    }

    public static abstract class PatchDevEnvironment implements Named {

        @Inject
        public abstract Project getProject();

        @Inject
        public abstract ProjectLayout getLayout();

        public abstract DirectoryProperty getWorkingDirectory();

        public abstract DirectoryProperty getPatchesDirectory();

        // Not to use DirectoryProperty or RegularFileProperty here
        // As this can be a directory or file
        public abstract Property<File> getSource();

        private String dependsOn;
        private NamedDomainObjectProvider<SourceSet> sourceSet;
        private TaskProvider<DefaultTask> prepareEnvironment;
        private TaskProvider<Copy> copyToSourceSet;
        private TaskProvider<GenerateDiffs> generateDiffs;

        public PatchDevEnvironment() {
            this.getWorkingDirectory().convention(this.getLayout().getBuildDirectory().dir(this.getProject().provider(() ->Meta.CG_FOLDER + "/" + this.getName())));
            this.getPatchesDirectory().convention(this.getWorkingDirectory().map(dir -> dir.dir("patches")));
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

        public TaskProvider<Copy> getCopyToSourceSet() {
            return copyToSourceSet;
        }

        public TaskProvider<GenerateDiffs> getGenerateDiffs() {
            return generateDiffs;
        }

        private void afterEvaluate() {
            var name = this.getName();
            if (!this.getSource().isPresent()) {
                throw new InvalidUserDataException("source for %s must be set!".formatted(name));
            }

            var project = this.getProject();
            this.sourceSet = SourceSets.of(project, name + "PatchDev");

            var groupName = name + " patch development tasks";
            var capitalizedName = StringUtils.capitalize(name);
            this.prepareEnvironment = Tasks.of(project, groupName, "prepare" + capitalizedName + "PatchDevEnvironment");
            this.copyToSourceSet = Tasks.copy(project, groupName, "copy" + capitalizedName + "ToSourceSet", this.getSource(), SourceSets.source(this.sourceSet));
            this.generateDiffs = Tasks.of(project, groupName, "generate" + capitalizedName + "Diffs", GenerateDiffs.class);

            this.prepareEnvironment.configure(task -> {
                if (this.dependsOn != null) {
                    task.dependsOn(this.copyToSourceSet);
                }
                task.doLast($ -> {
                    var file = this.getSource().get();
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
                if (this.dependsOn != null) {
                    task.dependsOn(this.dependsOn);
                }

                task.getOriginalDirectory().fileProvider(this.getSource());
                task.getModifiedDirectory().fileProvider(SourceSets.source(this.sourceSet));
                task.getPatchesDirectory().value(this.getPatchesDirectory());
            });
        }

    }

}
