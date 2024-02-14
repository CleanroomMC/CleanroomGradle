package com.cleanroommc.gradle.api.lazy;

import com.cleanroommc.gradle.api.types.Types;
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
        return Value.of(toString());
    }

    @Nullable
    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String toString() {
        if (cachedString == null) {
            cachedString = Types.resolveString(object);
        }
        return cachedString;
    }

}
