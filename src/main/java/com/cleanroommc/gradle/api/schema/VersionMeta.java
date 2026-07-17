package com.cleanroommc.gradle.api.schema;

import com.cleanroommc.gradle.api.util.Platform;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record VersionMeta(Arguments arguments,
                          AssetIndex assetIndex,
                          String assets,
                          int complianceLevel,
                          Map<String, Download> downloads,
                          String id,
                          JavaVersion javaVersion,
                          List<Library> libraries,
                          Object logging,
                          String mainClass,
                          String minecraftArguments,
                          int minimumLauncherVersion,
                          String releaseTime,
                          String time,
                          String type) {

    private static final Map<Platform.OperatingSystem, String> OS_NAMES = Map.of(
            Platform.OperatingSystem.WINDOWS, "windows",
            Platform.OperatingSystem.MAC_OS, "osx",
            Platform.OperatingSystem.LINUX, "linux"
    );

    public String clientUrl() {
        return downloads().get("client").url();
    }

    public String clientSha1() {
        return downloads().get("client").sha1();
    }

    public String serverUrl() {
        return downloads().get("server").url();
    }

    public String serverSha1() {
        return downloads().get("server").sha1();
    }

    public String assetIndexId() {
        return assetIndex().id();
    }

    public String assetIndexUrl() {
        return assetIndex().url();
    }

    public Download download(String key) {
        return downloads().get(key);
    }

    public boolean isVersionOrNewer(String releaseTime) {
        return releaseTime().compareTo(releaseTime) >= 0;
    }

    public boolean hasNativesToExtract() {
        return libraries().stream().anyMatch(Library::hasNatives);
    }

    // TODO: older Minecraft versions can target Java 6
    public int javaMajor() {
        return javaVersion() == null ? 8 : javaVersion().majorVersion();
    }

    public record JavaVersion(String component, int majorVersion) { }

    public record AssetIndex(String id, long totalSize, String path, String sha1, long size, String url) { }

    public record Library(Downloads downloads, String name, Map<String, String> natives, List<Rule> rules, Object extract) {

        public boolean isValidForOS(Platform platform) {
            List<Rule> rules = rules();
            if (rules == null) {
                // No rules allow everything.
                return true;
            }
            boolean valid = false;
            for (Rule rule : rules) {
                if (rule.appliesToOS(platform)) {
                    valid = rule.isAllowed();
                }
            }
            return valid;
        }

        public boolean hasNatives() {
            return natives() != null;
        }

        public boolean hasNativesForOS(Platform platform) {
            if (!hasNatives()) {
                return false;
            }
            if (classifierForOS(platform) == null) {
                return false;
            }
            return isValidForOS(platform);
        }

        public Download classifierForOS(Platform platform) {
            String classifier = natives().get(OS_NAMES.get(platform.getOperatingSystem()));
            if (classifier == null) {
                return null;
            }
            if (platform.getArchitecture().isArm() && platform.getArchitecture().is64Bit()) {
                // Default to the arm64 natives, if not found fallback.
                final Download armNative = downloads().classifier(classifier + "-arm64");
                if (armNative != null) {
                    return armNative;
                }
            }
            // Used in the twitch library in 1.7.10
            classifier = classifier.replace("${arch}", platform.getArchitecture().is64Bit() ? "64" : "32");
            return downloads().classifier(classifier);
        }

        public Download artifact() {
            Downloads downloads = downloads();
            if (downloads == null) {
                return null;
            }
            return downloads.artifact();
        }

    }

    public record Downloads(Download artifact, Map<String, Download> classifiers) {

        public Download classifier(String os) {
            return classifiers().get(os);
        }

    }

    public record Rule(String action, OS os) {

        public boolean appliesToOS(Platform platform) {
            return os() == null || os().isValidForOS(platform);
        }

        public boolean isAllowed() {
            return action().equals("allow");
        }

    }

    public record OS(String name) {

        public boolean isValidForOS(Platform platform) {
            String name = name();
            return name == null || name.equalsIgnoreCase(OS_NAMES.get(platform.getOperatingSystem()));
        }

    }

    public record Download(String path, String sha1, long size, String url) { }

    // 1.13+ arguments block
    public record Arguments(List<Argument> game, List<Argument> jvm) { }

    public record Argument(List<ArgRule> rules, List<String> values) { }

    public record ArgRule(String action, OS os, Map<String, Boolean> features) {

        public boolean isAllowed() {
            return "allow".equals(action());
        }

        public boolean matches(Platform platform) {
            if (features() != null) {
                for (Boolean required : features().values()) {
                    if (!Boolean.FALSE.equals(required)) {
                        return false;
                    }
                }
            }
            return os() == null || os().isValidForOS(platform);
        }

    }

    public static final class ArgumentDeserializer implements JsonDeserializer<Argument> {

        private static final Type RULES_TYPE = TypeToken.getParameterized(List.class, ArgRule.class).getType();

        @Override
        public Argument deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            if (json.isJsonPrimitive()) {
                return new Argument(null, List.of(json.getAsString()));
            }
            var object = json.getAsJsonObject();
            List<ArgRule> rules = context.deserialize(object.get("rules"), RULES_TYPE);
            var value = object.get("value");
            var values = new ArrayList<String>();
            if (value.isJsonArray()) {
                for (var element : value.getAsJsonArray()) {
                    values.add(element.getAsString());
                }
            } else {
                values.add(value.getAsString());
            }
            return new Argument(rules, values);
        }

    }

}
