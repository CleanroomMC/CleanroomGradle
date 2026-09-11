/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.ext;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class RunConfiguration implements Named {

    private final String name;
    private final List<Action<? super Task>> configurationActions = new ArrayList<>();

    @Inject
    public RunConfiguration(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public abstract Property<String> getInherit();

    public void configure(Action<? super Task> action) {
        this.configurationActions.add(action);
    }

    public <T extends Task> void configure(Class<T> type, Action<? super T> action) {
        configure(task -> action.execute(type.cast(task)));
    }

    public List<Action<? super Task>> getConfigurationActions() {
        return List.copyOf(this.configurationActions);
    }

}
