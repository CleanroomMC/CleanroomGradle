package com.cleanroommc.gradle.api.deobf;

import org.gradle.api.provider.Property;

/**
 * Per-call options for {@code deobf(...)}.
 */
public interface DeobfSpec {

    Property<Boolean> getSources();

}
