package com.cleanroommc.gradle.api.named.extension;

import org.gradle.api.plugins.ExtensionAware;

public final class Extensions {

    public static <T> T create(ExtensionAware extensionAware, String name, Class<T> clazz, Object... ctorOverloads) {
        return extensionAware.getExtensions().create(name, clazz, ctorOverloads);
    }

    private Extensions() { }

}
