package com.cleanroommc.gradle.tasks;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.api.DelegatedPatternFilterable;
import com.cleanroommc.gradle.api.ExtractionVisitor;
import com.cleanroommc.gradle.extensions.MappingsExtension;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.tasks.download.PureDownloadTask;
import com.cleanroommc.gradle.util.Utils;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.io.IOException;

import static com.cleanroommc.gradle.Constants.*;

public class ExtractConfigTask extends PureDownloadTask implements DelegatedPatternFilterable<PatternSet> {

    public static void setupExtractConfigTasks(Project project) {
        ExtractConfigTask task = Utils.createTask(project, EXTRACT_NATIVES_TASK, ExtractConfigTask.class);
        task.setDestinationFolder(Utils.closure(() -> NATIVES_FOLDER.apply(MinecraftExtension.get(project).getVersion())));
        task.setConfig(CONFIG_NATIVES);
        task.exclude("META-INF/**");
        task.dependsOn(Utils.getTask(project, DL_MINECRAFT_VERSIONS_TASK));

        task = Utils.createTask(project, EXTRACT_MCP_DATA_TASK, ExtractConfigTask.class);
        task.setUrl(Utils.closure(() -> MCP_ARCHIVES_LINK_MCP_DATA_FORMAT.apply(MappingsExtension.get(project), "srg.zip")));
        task.setDestinationFolder(Utils.closure(() -> MCP_DATA_CACHE_FOLDER.apply(MinecraftExtension.get(project).getVersion())));
        // task.setConfig(CONFIG_MCP_DATA);

        task = Utils.createTask(project, EXTRACT_MCP_MAPPINGS_TASK, ExtractConfigTask.class);
        task.setUrl(Utils.closure(() -> MCP_ARCHIVES_LINK_MCP_MAPPINGS_FORMAT.apply(MappingsExtension.get(project))));
        task.setDestinationFolder(Utils.closure(() -> MCP_MAPPINGS_CACHE_FOLDER_FROM_EXT.apply(MappingsExtension.get(project))));
        // task.setConfig(CONFIG_MCP_MAPPINGS);
    }

    @Input private PatternSet patternSet = new PatternSet();
    @Input private String config;
    @Input private boolean shouldClean, includeEmptyFolders;

    @OutputDirectory private Closure<File> destinationFolder;

    @Override
    @TaskAction
    public void downloadAndGet() throws IOException {
        if (config != null) { // Grab from config's dependencies
            extract(getProject().getConfigurations().getByName(config));
        } else {
            PROJECT_TEMP_FOLDER.mkdirs();
            File temp = File.createTempFile("cleanroom", ".zip", PROJECT_TEMP_FOLDER);
            setOutputFile(Utils.closure(() -> temp));
            super.downloadAndGet();
            extract(getProject().files(temp));
            Utils.recursivelyDelete(PROJECT_TEMP_FOLDER);
        }
    }

    private void extract(FileCollection from) {
        File destinationFolder = getDestinationFolder();
        if (shouldClean) {
            Utils.recursivelyDelete(destinationFolder);
        }
        destinationFolder.mkdirs();
        ExtractionVisitor visitor = new ExtractionVisitor(destinationFolder, includeEmptyFolders(), patternSet.getAsSpec());
        for (File file : from) {
            CleanroomLogger.debug("Extracting: {}", file);
            getProject().zipTree(file).visit(visitor);
        }
    }

    public void setPatternSet(PatternSet patternSet) {
        this.patternSet = patternSet;
    }

    @Override
    public PatternSet getDelegated() {
        return patternSet;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getConfig() {
        return config;
    }

    public void setDestinationFolder(Closure<File> destinationFolder) {
        this.destinationFolder = destinationFolder;
    }

    public File getDestinationFolder() {
        return destinationFolder.call();
    }

    public boolean includeEmptyFolders() {
        return includeEmptyFolders;
    }

    public void includeEmptyFolders(boolean includeEmptyFolders) {
        this.includeEmptyFolders = includeEmptyFolders;
    }

    public boolean shouldClean() {
        return shouldClean;
    }

    public void setClean(boolean shouldClean) {
        this.shouldClean = shouldClean;
    }

}
