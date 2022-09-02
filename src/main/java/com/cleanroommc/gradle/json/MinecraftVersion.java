package com.cleanroommc.gradle.json;

import com.cleanroommc.gradle.Constants;
import com.cleanroommc.gradle.util.OS;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MinecraftVersion {

    public String id;
    public String assets;
    public AssetIndex assetIndex;
    public RunnableDownloads downloads;
    public List<Library> libraries;
    public String mainClass;
    public String minecraftArguments;
    public String minimumLauncherVersion;
    public String releaseTime;
    public String release; // Enum

    public static class JavaVersion {

        public String component;
        public int majorVersion;

    }

    public static class AssetIndex extends Download {

        public String id;
        public long totalSize;

    }

    public static class RunnableDownloads {

        public Download client, server;

    }

    public static class Library {

        public String name;
        public Downloads downloads;
        @Nullable public Map<String, String> natives;
        @Nullable public List<Rule> rules;
        @Nullable public Extract extract;

        private transient RuleAction applicable;

        public boolean isApplicable() {
            if (applicable == null) {
                if (rules == null) {
                    applicable = RuleAction.ALLOW;
                } else {
                    applicable = RuleAction.DISALLOW;
                    for (Rule rule : rules) {
                        if (rule.isApplicable()) {
                            applicable = RuleAction.ALLOW;
                        }
                    }
                }
            }
            return applicable == RuleAction.ALLOW;
        }

    }

    public static class Artifact extends Download {

        public String path;

    }

    public static class Rule {

        public RuleAction action;
        @Nullable public OS os;

        public boolean isApplicable() {
            if (os == null) {
                return true;
            }
            if (os.name != null && os.name != Constants.OPERATING_SYSTEM) {
                return false;
            }
            if (os.version != null) {
                try {
                    if (!Pattern.compile(os.version).matcher(Constants.OPERATING_SYSTEM_VERSION).matches()) {
                        return false;
                    }
                } catch (Throwable ignored) { }
            }
            return true;
        }

    }

    public static class OS {

        private com.cleanroommc.gradle.util.OS name;
        private String version;

    }

    public enum RuleAction {

        ALLOW,
        DISALLOW;

    }

    public static class Extract {

        public List<String> exclude;

    }

    public static class Downloads {

        @Nullable public Artifact artifact;
        @Nullable public Map<String, Artifact> classifiers;

    }

    public static class Download {

        public String sha1;
        public long size;
        public URL url; // String

    }

}
