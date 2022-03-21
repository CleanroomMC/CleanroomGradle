package com.cleanroommc.gradle.util;

import com.cleanroommc.gradle.CleanroomLogger;
import groovy.lang.Closure;

import java.io.InputStream;
import java.util.function.Supplier;

public final class Utils {

    public static InputStream getResource(String path) {
        return Utils.class.getResourceAsStream("/" + path);
    }

    public static <T> Closure<T> supplyToClosure(Class<?> caller, Supplier<T> supplier) {
        return new Closure<T>(caller) {
            @Override
            public T call(Object... args) {
                return supplier.get();
            }
        };
    }

    public static <T> Closure<T> supplyToClosure(Supplier<T> supplier) {
        return new Closure<T>(Utils.class) {
            @Override
            public T call(Object... args) {
                return supplier.get();
            }
        };
    }

    public static void error(boolean throwError, String error) {
        if (throwError) {
            throw new RuntimeException(error);
        } else {
            CleanroomLogger.error(error);
        }
    }

    private Utils() { }

}
