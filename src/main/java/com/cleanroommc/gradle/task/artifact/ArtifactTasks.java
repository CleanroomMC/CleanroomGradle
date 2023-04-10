package com.cleanroommc.gradle.task.artifact;

import com.cleanroommc.gradle.dependency.MinecraftDependency;
import com.cleanroommc.gradle.dependency.Side;
import com.cleanroommc.gradle.extension.ManifestExtension;
import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.cleanroommc.gradle.task.ManifestTasks;
import com.cleanroommc.gradle.util.ClosureUtil;
import com.cleanroommc.gradle.util.DirectoryUtil;
import de.undercouch.gradle.tasks.download.Download;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class ArtifactTasks {

    public static void registerAfterEvaluation(Project project) {
        Set<MinecraftDependency> minecraftDependencies = MinecraftDependency.getMinecraftDependencies(project);
        List<String> defaultTasks = new ArrayList<>();
        for (MinecraftDependency mcDep : minecraftDependencies) {

            String downloadDependenciesTaskName = downloadDependenciesTaskName(mcDep.getTaskDescription());
            defaultTasks.add(downloadDependenciesTaskName);
            registerDynamicDownloadTask(project, downloadDependenciesTaskName, mcDep,
                    Artifacts::dependencies, dir -> dir.getLibs(mcDep.getVanillaVersion()));

            String downloadNativesTaskName = downloadNativesTaskName(mcDep.getTaskDescription());
            defaultTasks.add(downloadNativesTaskName);
            registerDynamicDownloadTask(project, downloadNativesTaskName, mcDep,
                    Artifacts::natives, dir -> dir.getNatives(mcDep.getVanillaVersion()));

            String sideDownloadTaskName = downloadSideDownloadTaskName(mcDep.getVanillaVersion());
            defaultTasks.add(sideDownloadTaskName);
            registerDynamicDownloadTask(project, sideDownloadTaskName, mcDep,
                    Artifacts::side, dir -> dir.getSide(mcDep.getVanillaVersion()));

            String assetsDownloadTaskName = downloadAssetsDownloadTaskName(mcDep.getVanillaVersion());
            defaultTasks.add(assetsDownloadTaskName);
            registerDynamicDownloadTask(project, assetsDownloadTaskName, mcDep,
                    Artifacts::assets, dir -> dir.getAssetManifest(mcDep.getVanillaVersion()));

        }
        defaultTasks.addAll(project.getGradle().getStartParameter().getTaskNames());
        project.getGradle().getStartParameter().setTaskNames(defaultTasks); // TODO
    }

    public static String downloadDependenciesTaskName(String version) {
        return "download" + version.replace('.', '_') + "Dependencies";
    }

    public static String downloadNativesTaskName(String version) {
        return "download" + version.replace('.', '_') + "Natives";
    }

    private static String downloadSideDownloadTaskName(String version) {
        return "download" + version.replace('.', '_') + "Side";
    }

    private static String downloadAssetsDownloadTaskName(String version) {
        return "download" + version.replace('.', '_') + "Assets";
    }

    private static void registerDynamicDownloadTask(Project project, String taskName, MinecraftDependency mcDep, Function<Artifacts, List<String>> data, Function<DirectoryUtil.Directories, File> dir) {
        project.getTasks().register(taskName, Download.class, task -> {
            Closure<List<String>> urlGetter = ClosureUtil.of(() -> {
                Artifacts artifacts = new Artifacts(project, mcDep);
                return data.apply(artifacts);
            });
            task.dependsOn(ManifestTasks.readManifestTaskName(mcDep.getVanillaVersion()));
            task.src(urlGetter);
            task.dest(DirectoryUtil.create(project, dir));
            task.overwrite(false);
            task.onlyIfModified(true);
            task.useETag(true);
        });
    }

    private static class Artifacts {
        private final VersionMetadata versionMetadata;
        private final MinecraftDependency mcDep;

        private Artifacts(Project project, MinecraftDependency mcDep) {
            this.mcDep = mcDep;
            MapProperty<String, VersionMetadata> versionMetadataMap = project.getExtensions().getByType(ManifestExtension.class).getMetadataCache();
            versionMetadata = versionMetadataMap.get().get(mcDep.getVanillaVersion());
        }

        public List<String> dependencies() {
            List<String> dependencies = new ArrayList<>();

            for (VersionMetadata.Library library : versionMetadata.libraries()) {
                if (library.isValidForOS()) {
                    var lib = library.artifact();

                    if (lib != null)
                        dependencies.add(lib.url());
                }
            }
            return dependencies;
        }

        public List<String> natives() {
            List<String> natives = new ArrayList<>();

            for (VersionMetadata.Library library : versionMetadata.libraries()) {
                if (library.hasNativesForOS()) {
                    var lib = library.classifierForOS();
                    if (lib != null)
                        natives.add(lib.url());
                }
            }
            return natives;
        }

        public List<String> side() {
            List<String> side = new ArrayList<>();

            switch (mcDep.getInternalSide()) {
                case JOINED -> {
                    side.add(versionMetadata.downloads().get(Side.CLIENT_ONLY.getValue()).url());
                    side.add(versionMetadata.downloads().get(Side.SERVER_ONLY.getValue()).url());
                }

                case CLIENT_ONLY -> side.add(versionMetadata.downloads().get(Side.CLIENT_ONLY.getValue()).url());
                case SERVER_ONLY -> side.add(versionMetadata.downloads().get(Side.SERVER_ONLY.getValue()).url());
            }

            return side;
        }

        List<String> assets() {
            List<String> assets = new ArrayList<>(); // TODO: 10/04/2023
            return assets;
        }

    }

    private ArtifactTasks() {
    }

}
