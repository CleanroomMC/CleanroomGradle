package com.cleanroommc.gradle.api.named.dependency;

import org.gradle.util.internal.VersionNumber;

public class Versioning {

    public static boolean lowerThan(String checkFor, String checkAgainst) {
        return VersionNumber.parse(checkFor).compareTo(VersionNumber.parse(checkAgainst)) < 0;
    }

    public static boolean higherThan(String checkFor, String checkAgainst) {
        return VersionNumber.parse(checkFor).compareTo(VersionNumber.parse(checkAgainst)) > 0;
    }

    public static boolean sameAs(String checkFor, String checkAgainst) {
        return VersionNumber.parse(checkFor).equals(VersionNumber.parse(checkAgainst));
    }

}
