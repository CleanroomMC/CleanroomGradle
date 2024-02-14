package com.cleanroommc.gradle.api.named.task;

import org.gradle.api.Task;

public interface JarTransformer {

    default void setup(boolean deleteInput) {
        var $ = ((Task) this);
        if (deleteInput) {
            $.doLast(t -> {
                for (var input : t.getInputs().getFiles()) {
                    input.delete();
                }
            });
        }
        $.onlyIf(t -> {
            for (var output : t.getOutputs().getFiles()) {
                if (!output.exists()) {
                    return true;
                }
            }
            return false;
        });
    }

}
