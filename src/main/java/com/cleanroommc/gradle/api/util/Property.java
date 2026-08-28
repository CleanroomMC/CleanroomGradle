package com.cleanroommc.gradle.api.util;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public enum Property {

    ENABLE_EXCLUSIVE_LOCAL_MAVENS("cg.repos.enableLocal");

    private final String property;

    Property(String property) {
        this.property = property;
    }

    public Provider<String> value(ProviderFactory providerFactory) {
        return providerFactory.gradleProperty(property);
    }

    public boolean bool(ProviderFactory providerFactory) {
        return Boolean.parseBoolean(value(providerFactory).getOrElse("false"));
    }

}
