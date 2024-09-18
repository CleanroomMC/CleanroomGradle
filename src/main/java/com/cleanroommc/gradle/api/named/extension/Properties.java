package com.cleanroommc.gradle.api.named.extension;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import java.util.function.Supplier;

public final class Properties {

    public static boolean has(ExtensionAware extensionAware, String name) {
        return extraProps(extensionAware).has(name);
    }

    public static <T> T get(ExtensionAware extensionAware, String name) {
        var extraProps = extraProps(extensionAware);
        if (extraProps.has(name)) {
            return (T) extraProps.get(name);
        }
        return null;
    }

    public static boolean getBoolean(ExtensionAware extensionAware, String name) {
        var extraProps = extraProps(extensionAware);
        if (extraProps.has(name)) {
            var value = extraProps.get(name);
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
            throw new RuntimeException("Property " + name + " should have a boolean value. Right now it has a " + value.getClass() + " value.");
        }
        return false;
    }

    public static <T> T getOrSet(ExtensionAware extensionAware, String name, T object) {
        var extraProps = extraProps(extensionAware);
        if (extraProps.has(name)) {
            return (T) extraProps.get(name);
        }
        extraProps.set(name, object);
        return object;
    }

    public static <T> T getOrSet(ExtensionAware extensionAware, String name, Supplier<T> supplier) {
        var extraProps = extraProps(extensionAware);
        if (extraProps.has(name)) {
            return (T) extraProps.get(name);
        }
        T object = supplier.get();
        extraProps.set(name, object);
        return object;
    }

    public static boolean getOrSetBoolean(ExtensionAware extensionAware, String name, boolean bool) {
        var extraProps = extraProps(extensionAware);
        if (extraProps.has(name)) {
            var value = extraProps.get(name);
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
            throw new RuntimeException("Property " + name + " should have a boolean value.");
        }
        extraProps.set(name, bool);
        return bool;
    }

    private static ExtraPropertiesExtension extraProps(ExtensionAware extensionAware) {
        return extensionAware.getExtensions().getExtraProperties();
    }

    private Properties() { }

}
