package com.cleanroommc.gradle.newapi.util.lazy;

import com.cleanroommc.gradle.newapi.util.Objects;
import org.gradle.api.internal.provider.AbstractProviderWithValue;
import org.jetbrains.annotations.Nullable;

public class StringableProvider extends AbstractProviderWithValue<String> {

    private final Object object;

    private String cachedString;

    StringableProvider(Object object) {
        this.object = object;
    }

    @Override
    protected Value<? extends String> calculateOwnValue(ValueConsumer consumer) {
        return Value.of(this.toString());
    }

    @Nullable
    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    protected String toStringNoReentrance() {
        if (this.cachedString == null) {
            this.cachedString = Objects.resolveString(object);
        }
        return this.cachedString;
    }

}
