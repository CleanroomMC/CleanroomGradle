package com.cleanroommc.gradle.api;

import groovy.lang.Closure;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;

import java.util.Set;

public interface DelegatedPatternFilterable extends PatternFilterable {

    PatternFilterable getDelegated();

    @Override
    default Set<String> getIncludes() {
        return getDelegated().getIncludes();
    }

    @Override
    default Set<String> getExcludes() {
        return getDelegated().getExcludes();
    }

    @Override
    default PatternFilterable setIncludes(Iterable<String> includes) {
        return getDelegated().setIncludes(includes);
    }

    @Override
    default PatternFilterable setExcludes(Iterable<String> excludes) {
        return getDelegated().setExcludes(excludes);
    }

    @Override
    default PatternFilterable include(String... includes) {
        return getDelegated().include(includes);
    }

    @Override
    default PatternFilterable include(Iterable<String> includes) {
        return getDelegated().include(includes);
    }

    @Override
    default PatternFilterable include(Spec<FileTreeElement> includeSpec) {
        return getDelegated().include(includeSpec);
    }

    @Override
    default PatternFilterable include(Closure includeSpec) {
        return getDelegated().include(includeSpec);
    }

    @Override
    default PatternFilterable exclude(String... excludes) {
        return getDelegated().exclude(excludes);
    }

    @Override
    default PatternFilterable exclude(Iterable<String> excludes) {
        return getDelegated().exclude(excludes);
    }

    @Override
    default PatternFilterable exclude(Spec<FileTreeElement> excludeSpec) {
        return getDelegated().exclude(excludeSpec);
    }

    @Override
    default PatternFilterable exclude(Closure excludeSpec) {
        return getDelegated().exclude(excludeSpec);
    }

}
