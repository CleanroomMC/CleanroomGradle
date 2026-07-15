package com.cleanroommc.gradle.api.util.lazy;

import com.cleanroommc.gradle.api.util.Objects;
import kotlin.jvm.functions.Function0;
import org.gradle.api.provider.Provider;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class LazyStringable {

    public static LazyStringable of(Provider<?> property) {
        return new LazyStringable(property);
    }

    public static LazyStringable of(Callable<?> callable) {
        return new LazyStringable(callable);
    }

    public static LazyStringable of(Supplier<?> supplier) {
        return new LazyStringable(supplier);
    }

    public static LazyStringable of(Function0<?> function) {
        return new LazyStringable(function);
    }

    private final Object object;

    private LazyStringable(Object object) {
        this.object = object;
    }

    @Override
    public String toString() {
        return Objects.resolveString(this.object);
    }

}
