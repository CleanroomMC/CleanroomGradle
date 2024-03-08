package com.cleanroommc.gradle.env.loaderdev;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class LoaderDevExtension {

    public static final String EXT_NAME = "loaderDev";
    public static final String PROPERTY_NAME = "com.cleanroommc.loader-dev.extension";

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Nested
    public abstract Property<LoaderDevTasks> getTasks();

    public LoaderDevExtension() {
        getTasks().convention(getObjectFactory().newInstance(LoaderDevTasks.class));
    }

}
