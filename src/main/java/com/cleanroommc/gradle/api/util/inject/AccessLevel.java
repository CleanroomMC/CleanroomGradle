package com.cleanroommc.gradle.api.util.inject;

import org.objectweb.asm.Opcodes;

/**
 * The four Java visibility levels, as written in an MCP {@code access.txt}.
 */
public enum AccessLevel {

    PRIVATE,
    DEFAULT,
    PROTECTED,
    PUBLIC;

    private static final int MASK = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;

    public static AccessLevel of(int access) {
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return PRIVATE;
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            return PROTECTED;
        }
        return (access & Opcodes.ACC_PUBLIC) != 0 ? PUBLIC : DEFAULT;
    }

    public int apply(int access) {
        access &= ~MASK;
        return switch (this) {
            case PRIVATE -> access | Opcodes.ACC_PRIVATE;
            case PROTECTED -> access | Opcodes.ACC_PROTECTED;
            case PUBLIC -> access | Opcodes.ACC_PUBLIC;
            case DEFAULT -> access;
        };
    }

}
