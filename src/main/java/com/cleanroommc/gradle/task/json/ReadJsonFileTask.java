package com.cleanroommc.gradle.task.json;

import com.cleanroommc.gradle.CleanroomMeta;
import com.google.gson.Gson;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.FileReader;
import java.io.IOException;

public abstract class ReadJsonFileTask extends DefaultTask {

    @Input
    public abstract Property<Gson> getGson();

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @Input
    public abstract Property<Class> getType();

    public Object output;

    public ReadJsonFileTask() {
        getInputFile().convention(getProject().getObjects().fileProperty());
        getGson().convention(CleanroomMeta.GSON);
    }

    @TaskAction
    public void readJsonFile() throws IOException {
        try (FileReader reader = new FileReader(getInputFile().get().getAsFile())) {
            this.output = getGson().get().fromJson(reader, getType().get());
        }
    }

    public <T> T getTypedOutput(Class<T> type) {
        return (T) output;
    }

}
