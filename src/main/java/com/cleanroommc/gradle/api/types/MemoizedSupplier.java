package com.cleanroommc.gradle.api.types;

import java.util.function.Supplier;

public final class MemoizedSupplier<T> implements Supplier<T> {

    private Supplier<T> supplier;
    private T supplied;

    MemoizedSupplier(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        if (this.supplied == null) {
            this.supplied = supplier.get();
            this.supplier = null;
        }
        return this.supplied;
    }

}
