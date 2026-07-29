package com.cleanroommc.gradle.api.source;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/**
 * Reads {@link UserdevConfig} out of a userdev artifact.
 */
public abstract class UserdevConfigValueSource implements ValueSource<UserdevConfig, UserdevConfigValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        RegularFileProperty getUserdevJar();

    }

    @Override
    public UserdevConfig obtain() {
        return UserdevConfig.readFromJar(getParameters().getUserdevJar().getAsFile().get());
    }

}
