package com.cleanroommc.gradle.dependency;

public enum Side {
    CLIENT_ONLY("client"),
    SERVER_ONLY("server"),
    JOINED("joined");

    private final String value;
    Side(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
