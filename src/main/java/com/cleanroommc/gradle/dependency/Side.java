package com.cleanroommc.gradle.dependency;

public enum Side {
    CLIENT_ONLY("client"),
    SERVER_ONLY("server"),
    JOINED("joined");

    public static Side parse(String side) {
        try {
            return Side.valueOf(side.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(String.format("%s side not supported!", side));
        }
    }


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
