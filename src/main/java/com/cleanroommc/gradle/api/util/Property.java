package com.cleanroommc.gradle.api.util;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

public enum Property {

    ENABLE_EXCLUSIVE_LOCAL_MAVENS("cg.repos.enableLocal"),
    NSIGHT_ACTIVITY("cg.run.nsight_activity"),
    NSIGHT_NGFX_PATH("cg.run.nsight_ngfx_path");

    private final String property;

    Property(String property) {
        this.property = property;
    }

    public Provider<String> value(ProviderFactory providerFactory) {
        return providerFactory.gradleProperty(property);
    }

    public Provider<Boolean> bool(ProviderFactory providerFactory) {
        return value(providerFactory).map(Boolean::parseBoolean);
    }

}
