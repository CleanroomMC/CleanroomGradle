package com.cleanroommc.gradle.util;

import groovy.lang.Closure;

import java.util.function.Supplier;

public final class ClosureUtil {

    public static <T> Closure<T> of(Supplier<T> supplier) {
        return new Closure<T>(ClosureUtil.class) {
            @Override
            public T call(Object... args) {
                return supplier.get();
            }
        };
    }

    private ClosureUtil() { }

}
