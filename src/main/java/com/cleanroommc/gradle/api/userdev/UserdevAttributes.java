package com.cleanroommc.gradle.api.userdev;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

public final class UserdevAttributes {

    public static final Attribute<String> STAGE = Attribute.of("com.cleanroommc.userdev.stage", String.class);
    public static final Attribute<String> ROLE = Attribute.of("com.cleanroommc.userdev.role", String.class);

    public static final String RAW = "raw";
    public static final String MATERIALIZED = "materialized";
    public static final String CLASSES = "classes";
    public static final String SOURCES = "sources";
    public static final String CLIENT_EXTRA = "client-extra";
    public static final String SERVER_EXTRA = "server-extra";
    /** Carries no artifact of its own, only the platform's native library dependencies. */
    public static final String NATIVES = "natives";

    private UserdevAttributes() { }

    public abstract static class PreferClasses implements AttributeDisambiguationRule<String> {
        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            if (details.getCandidateValues().contains(CLASSES)) {
                details.closestMatch(CLASSES);
            }
        }
    }

}
