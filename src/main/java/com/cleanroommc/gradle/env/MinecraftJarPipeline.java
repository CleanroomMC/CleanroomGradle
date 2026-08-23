package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.task.mcp.InjectMetadata;
import com.cleanroommc.gradle.api.task.mcp.MergeJars;
import com.cleanroommc.gradle.api.task.mcp.SplitJar;
import net.minecraftforge.renamer.gradle.RenameJar;
import net.minecraftforge.renamer.gradle.RenamerExtension;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.util.function.Consumer;

/**
 * Shared {@code split => merge => Notch2SRG => metadata injection} pipeline used by the loader and userdev workspaces.
 */
public final class MinecraftJarPipeline {

    public static MinecraftJarPipeline register(Project project, CachesExtension caches, Spec spec) {
        return new MinecraftJarPipeline(project, caches, spec);
    }

    public final TaskProvider<SplitJar> splitClient, splitServer;
    public final TaskProvider<MergeJars> merge;
    public final TaskProvider<RenameJar> remapNotch2Srg;
    public final TaskProvider<InjectMetadata> inject;

    private MinecraftJarPipeline(Project project, CachesExtension caches, Spec spec) {
        var mergetool = ToolConfigs.get(project, "mergetool");
        var renamer = project.getExtensions().getByType(RenamerExtension.class);

        this.splitClient = Tasks.register(project, spec.splitClientName, SplitJar.class);
        this.splitServer = Tasks.register(project, spec.splitServerName, SplitJar.class);
        this.merge = Tasks.tool(project, caches.getLocalDirectory(), spec.mergeName, MergeJars.class, mergetool);
        this.remapNotch2Srg = Tasks.register(project, spec.remapName, RenameJar.class, renamer);
        this.inject = Tasks.register(project, spec.injectName, InjectMetadata.class);

        this.splitClient.configure(task -> {
            task.dependsOn(spec.extractMcpConfig);
            spec.bindClientJar.accept(task.getSourceJar());
            task.getSrgMappingFile().value(spec.srgMapping);
            task.getSlimJar().set(spec.clientSlim);
            task.getExtraJar().set(spec.clientExtra);
        });
        this.splitServer.configure(task -> {
            task.dependsOn(spec.extractMcpConfig);
            spec.bindServerJar.accept(task.getSourceJar());
            task.getSrgMappingFile().value(spec.srgMapping);
            task.getSlimJar().set(spec.serverSlim);
            task.getExtraJar().set(spec.serverExtra);
        });
        this.merge.configure(task -> {
            task.getClientJar().value(this.splitClient.flatMap(SplitJar::getSlimJar));
            task.getServerJar().value(this.splitServer.flatMap(SplitJar::getSlimJar));
            task.getSrgMappingFile().value(spec.srgMapping);
            task.getMinecraftVersion().set(spec.minecraftVersion);
            task.getMergedJar().set(spec.mergedJar);
        });
        this.remapNotch2Srg.configure(task -> {
            task.getInput().set(this.merge.flatMap(MergeJars::getMergedJar));
            task.getMap().setFrom(spec.srgMapping);
            task.getLibraries().setFrom(spec.libraries);
            if (spec.srgJar != null) {
                task.getOutput().set(spec.srgJar);
            }
        });
        this.inject.configure(task -> {
            task.getSrgJar().set(this.remapNotch2Srg.flatMap(RenameJar::getOutput));
            task.getAccessFile().set(spec.mcpConfigDir.map(dir -> dir.file("access.txt")));
            task.getConstructorsFile().set(spec.mcpConfigDir.map(dir -> dir.file("constructors.txt")));
            task.getExceptionsFile().set(spec.mcpConfigDir.map(dir -> dir.file("exceptions.txt")));
            task.getInjectedJar().set(spec.injectedJar);
        });
    }

    public static final class Spec {

        public String splitClientName;
        public String splitServerName;
        public String mergeName;
        public String remapName;
        public String injectName;
        public Consumer<RegularFileProperty> bindClientJar;
        public Consumer<RegularFileProperty> bindServerJar;
        public Provider<? extends RegularFile> srgMapping;
        public Provider<Directory> mcpConfigDir;
        public Provider<String> minecraftVersion;
        public Object libraries;
        public TaskProvider<?> extractMcpConfig;
        public Provider<? extends RegularFile> clientSlim;
        public Provider<? extends RegularFile> clientExtra;
        public Provider<? extends RegularFile> serverSlim;
        public Provider<? extends RegularFile> serverExtra;
        public Provider<? extends RegularFile> mergedJar;
        public Provider<? extends RegularFile> srgJar;
        public Provider<? extends RegularFile> injectedJar;

    }

}
