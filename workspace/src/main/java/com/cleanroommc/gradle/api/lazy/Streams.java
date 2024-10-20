package com.cleanroommc.gradle.api.lazy;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Streams {

    public static <T> Stream<T> of(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static <K, V, K2, V2> Map<K2, V2> convert(Map<K, V> initial, Function<K, K2> keyConverter, Function<V, V2> valueConverter) {
        return initial.entrySet().stream().collect(Collectors.toMap(e -> keyConverter.apply(e.getKey()), e -> valueConverter.apply(e.getValue())));
    }

    public static <K, V, K2> Map<K2, V> convertKeys(Map<K, V> initial, Function<K, K2> keyConverter) {
        return initial.entrySet().stream().collect(Collectors.toMap(e -> keyConverter.apply(e.getKey()), Map.Entry::getValue));
    }

    public static <K, V, V2> Map<K, V2> convertValues(Map<K, V> initial, Function<V, V2> valueConverter) {
        return initial.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> valueConverter.apply(e.getValue())));
    }

    private Streams() { }

}
