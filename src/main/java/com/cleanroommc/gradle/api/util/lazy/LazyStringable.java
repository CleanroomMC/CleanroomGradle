/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.lazy;

import com.cleanroommc.gradle.api.util.Objects;
import kotlin.jvm.functions.Function0;
import org.gradle.api.provider.Provider;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class LazyStringable {

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
