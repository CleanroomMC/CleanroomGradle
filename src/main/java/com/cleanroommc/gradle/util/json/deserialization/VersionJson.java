package com.cleanroommc.gradle.util.json.deserialization;


import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.OS;
import com.google.gson.*;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class VersionJson {

    @Nullable public Arguments arguments;
    public AssetIndex assetIndex;
    public String assets;
    @Nullable public Map<String, Download> downloads;
    public Library[] libraries;

    private List<LibraryDownload> _natives = null;

    public List<LibraryDownload> getNatives() {
        if (_natives == null) {
            _natives = new ArrayList<>();
            OS os = OS.getCurrentPlatform();
            for (Library lib : libraries) {
                if (lib.natives != null && lib.downloads.classifiers != null && lib.natives.containsKey(os.toString())) {
                    LibraryDownload l = lib.downloads.classifiers.get(lib.natives.get(os.toString()));
                    if (l != null) {
                        _natives.add(l);
                    }
                }
            }
        }
        return _natives;
    }

    public Stream<String> getPlatformJvmArgs() {
        if (arguments == null || arguments.jvm == null) {
            return Stream.of();
        }
        return Stream.of(arguments.jvm)
                .filter(arg -> arg.rules != null && arg.isAllowed())
                .flatMap(arg -> arg.value.stream())
                .map(s -> s.indexOf(' ') != -1 ? "\"" + s + "\"" : s);
    }

    public static class Arguments {

        public Argument[] game;
        @Nullable public Argument[] jvm;

    }

    public static class Argument extends RuledObject {

        public List<String> value;

        public Argument(@Nullable Rule[] rules, List<String> value) {
            this.rules = rules;
            this.value = value;
        }

        public static class Deserializer implements JsonDeserializer<Argument> {

            @Override
            public Argument deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonPrimitive()) {
                    return new Argument(null, Collections.singletonList(json.getAsString()));
                }

                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("rules") || !obj.has("value"))
                    throw new JsonParseException("Error parsing arguments in version json. File is corrupt or its format has changed.");

                JsonElement val = obj.get("value");
                Rule[] rules = Utils.GSON.fromJson(obj.get("rules"), Rule[].class);
                @SuppressWarnings("unchecked")
                List<String> value = val.isJsonPrimitive() ? Collections.singletonList(val.getAsString()) : Utils.GSON.fromJson(val, List.class);

                return new Argument(rules, value);
            }
        }

    }

    public static class RuledObject {

        @Nullable
        public Rule[] rules;

        public boolean isAllowed() {
            if (rules != null) {
                for (Rule rule : rules) {
                    if (!rule.allowsAction()) {
                        return false;
                    }
                }
            }
            return true;
        }

    }

    public static class Rule {

        public String action;
        public OsCondition os;

        public boolean allowsAction() {
            return (os == null || os.platformMatches()) == action.equals("allow");
        }

    }

    public static class OsCondition {

        @Nullable public String name;
        @Nullable public String version;
        @Nullable public String arch;

        public boolean nameMatches() {
            return name == null || OS.getCurrentPlatform().toString().equals(name);
        }

        public boolean versionMatches() {
            return version == null || Pattern.compile(version).matcher(System.getProperty("os.version")).find();
        }

        public boolean archMatches() {
            return arch == null || Pattern.compile(arch).matcher(System.getProperty("os.arch")).find();
        }

        public boolean platformMatches() {
            return nameMatches() && versionMatches() && archMatches();
        }

    }

    public static class AssetIndex extends Download {

        public String id;
        public int totalSize;

    }

    public static class Download {

        public String sha1;
        public int size;
        public URL url;

    }

    public static class LibraryDownload extends Download {

        public String path;

    }

    public static class Downloads {

        @Nullable public Map<String, LibraryDownload> classifiers;
        @Nullable public LibraryDownload artifact;

    }

    public static class Library extends RuledObject {

        //Extract? rules?
        public String name;
        public Map<String, String> natives;
        public Downloads downloads;
        private Artifact _artifact;

        public Artifact getArtifact() {
            if (_artifact == null) {
                _artifact = Artifact.from(name);
            }
            return _artifact;
        }

    }

}

