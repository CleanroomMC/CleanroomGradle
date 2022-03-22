package com.cleanroommc.gradle.tasks;

import com.cleanroommc.gradle.api.DelegatedPatternFilterable;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.IOException;

public class ExtractConfigTask extends DefaultTask implements DelegatedPatternFilterable {

    @Input private PatternSet patternSet = new PatternSet();

    @TaskAction
    public void extract() throws IOException {
        
    }

    public void setPatternSet(PatternSet patternSet) {
        this.patternSet = patternSet;
    }

    @Override
    public PatternFilterable getDelegated() {
        return patternSet;
    }

}
