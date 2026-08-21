package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.MappingsExtension;
import com.cleanroommc.gradle.api.ext.PatchesExtension;
import com.cleanroommc.gradle.api.names.NamesSource;
import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.ext.PatchDevEnvironment;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.lazy.SourceSets;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

/**
 * MCP config/mapping artifacts and derived mapping files shared by the loader and userdev pipelines.
 */
public final class McpMappings {

    public final NamedDomainObjectProvider<Configuration> mcpConfig, mcpMappings;
    public final TaskProvider<Copy> extractMcpConfig, extractMcpMappings;
    public final TaskProvider<WriteMappings> writeSrg2Mcp;
    public final Provider<File> tinyFileWhenPresent;
    public final Provider<String> activeNamesId, mcpVersionId, mcpMappingsId, mcpConfigVersion;

    public McpMappings(Project project, CachesExtension caches, MappingsExtension mappings) {
        this.mcpConfig = Objects.config(project, "mcpConfig", "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735");
        this.mcpMappings = Objects.config(project, "mcpMappings", "de.oceanlabs.mcp:mcp_stable:39-1.12@zip");

        var tinyFile = mappings.getNamesDirectory().file("mappings.tiny");
        this.tinyFileWhenPresent = tinyFile.map(RegularFile::getAsFile).filter(File::isFile);
        var mcpNamesId = this.mcpMappings.map(cfg -> {
            var dep = Objects.firstDependency(cfg);
            return NamesSource.mcpId(dep.getName(), dep.getVersion());
        });
        this.activeNamesId = this.tinyFileWhenPresent.map(NamesSource::tiny2Id).orElse(mcpNamesId);
        this.mcpConfigVersion = this.mcpConfig.map(cfg -> Objects.firstDependency(cfg).getVersion());
        this.mcpVersionId = this.mcpConfig.map(McpMappings::deriveMcpVersion);
        this.mcpMappingsId = this.mcpMappings.map(McpMappings::deriveMcpMappings);

        this.extractMcpConfig = Tasks.unzip(project, "extractMcpConfig", this.mcpConfig, caches.getVersionDirectory().dir("mcp_config"));
        this.extractMcpMappings = Tasks.unzip(project, "extractMcpMappings", this.mcpMappings, caches.getVersionDirectory().dir("mcp_mappings"));
        this.writeSrg2Mcp = write(project, caches, "writeSrg2Mcp", WriteMappings.Direction.SRG_TO_MCP, "srg2mcp.tsrg");
    }

    public TaskProvider<WriteMappings> write(Project project, CachesExtension caches, String name,
                                             WriteMappings.Direction direction, String outputName) {
        var task = Tasks.register(project, name, WriteMappings.class);
        var mcpMappingsDir = this.extractMcpMappings.map(Copy::getDestinationDir);
        var srgMapping = caches.getVersionDirectory().file("mcp_config/config/joined.tsrg");
        task.configure(writeMappings -> {
            writeMappings.dependsOn(this.extractMcpConfig);
            writeMappings.getJoinedSrgFile().set(srgMapping);
            if (direction != WriteMappings.Direction.OBF_TO_SRG) {
                writeMappings.getMethodMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "methods.csv")));
                writeMappings.getFieldMappings().fileProvider(mcpMappingsDir.map(dir -> new File(dir, "fields.csv")));
                writeMappings.getTinyMappings().fileProvider(this.tinyFileWhenPresent);
                writeMappings.getNamesId().set(this.activeNamesId);
            }
            writeMappings.getDirection().set(direction);
            writeMappings.getFormat().set(IMappingFile.Format.TSRG);
            writeMappings.getOutput().set(caches.getLocalDirectory().file("mappings/" + outputName));
        });
        return task;
    }

    public void configurePatchMappings(PatchesExtension patches) {
        patches.getPatchDev().configureEach(env -> {
            if (env.getName().equals("initial")) {
                return;
            }
            env.getGenerateDiffs().configure(task -> {
                task.getMappingsId().set(this.activeNamesId);
                task.getMcpConfigVersion().set(this.mcpConfigVersion);
            });
            env.getApplyDiffs().configure(task -> task.getMappingsId().set(this.activeNamesId));
            env.getInitializeDiffs().configure(task -> task.getMappingsId().set(this.activeNamesId));
        });
    }

    public void configureInitialPatches(Project project, CachesExtension caches, PatchesExtension patches, VanillaTasks vanilla,
                                        TaskProvider<?> prepareApplyInitialDiffs, TaskProvider<ApplyDiffs> applyInitialDiffs) {
        if (!patches.getDevelopInitial().get()) {
            return;
        }
        var initial = patches.getPatchDev().register("initial", env -> {
            env.getInput().set(caches.getLocalDirectory().dir("decompileSrg/files"));
            env.dependsOn(prepareApplyInitialDiffs.getName());
        });
        initial.configure(env -> SourceSets.extendFromConfiguration(project, env.getSourceSet(), vanilla.vanillaConfig));
        applyInitialDiffs.configure(task -> task.getPatchesDirectory().set(
                initial.flatMap(PatchDevEnvironment::getPatches)));
    }

    private static String deriveMcpVersion(Configuration config) {
        var version = Objects.firstDependency(config).getVersion();
        if (version == null) {
            throw new IllegalStateException("mcpConfig dependency has no version to derive MCP_VERSION from.");
        }
        var dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(dash + 1);
    }

    private static String deriveMcpMappings(Configuration config) {
        var dependency = Objects.firstDependency(config);
        var name = dependency.getName();
        var channel = name.startsWith("mcp_") ? name.substring("mcp_".length()) : name;
        var version = dependency.getVersion();
        if (version == null) {
            throw new IllegalStateException("mcpMappings dependency has no version to derive MCP_MAPPINGS from.");
        }
        var dash = version.indexOf('-');
        var mappingVersion = dash < 0 ? version : version.substring(0, dash);
        return channel + "_" + mappingVersion;
    }

}
