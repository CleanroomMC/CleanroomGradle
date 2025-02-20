package com.cleanroommc.gradle.newapi.task;

import com.cleanroommc.gradle.newapi.util.IO;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;

public interface JarProcessingTypeTask {

    default void checkExistence(Task task, RegularFileProperty... files) {
        task.onlyIf("existenceCheck", $ -> {
            for (RegularFileProperty file : files) {
                if (!file.get().getAsFile().exists()) {
                    return true;
                }
            }
            return false;
        });
    }

    default void checkHash(Task task, RegularFileProperty file, String sha1) {
        task.onlyIf("sameHashCheck", $ -> !(IO.sha1Match(file.getAsFile().get(), sha1)));
    }

}
