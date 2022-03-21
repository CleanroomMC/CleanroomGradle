package com.cleanroommc.gradle.util.json.deserialization.mcversion;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import com.google.common.base.Strings;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.cleanroommc.gradle.Constants.*;

public class Version {

    private static Version cachedVersion;

    public static Version getCurrentVersion() {
        return cachedVersion;
    }

    public static Version parseVersionAndStoreDeps(Project project, File json, boolean override, File... inheritanceDirs) {
        if (!json.exists()) {
            return null;
        }
        if (!override && cachedVersion != null) {
            return cachedVersion;
        }
        try {
            cachedVersion = load(json, MinecraftExtension.get(project).getVersion(), inheritanceDirs);
        } catch (IOException e) {
            CleanroomLogger.error("{} could not be parsed", json);
            throw new RuntimeException(e);
        }
        // Apply dependencies
        DependencyHandler handler = project.getDependencies();
        // Actual dependencies
        if (project.getConfigurations().getByName(CONFIG_MC_DEPS).getState() == State.UNRESOLVED) {
            for (Library lib : cachedVersion.getLibraries()) {
                if (lib.natives == null) {
                    String configName = CONFIG_MC_DEPS;
                    if (lib.name.contains("java3d")
                            || lib.name.contains("paulscode")
                            || lib.name.contains("lwjgl")
                            || lib.name.contains("twitch")
                            || lib.name.contains("jinput")) {
                        configName = CONFIG_MC_DEPS_CLIENT;
                    }
                    handler.add(configName, lib.getArtifactName());
                }
            }
        } else {
            CleanroomLogger.debug("Resolved: {}", CONFIG_MC_DEPS);
        }
        // Natives
        if (project.getConfigurations().getByName(CONFIG_NATIVES).getState() == State.UNRESOLVED) {
            for (Library lib : cachedVersion.getLibraries()) {
                if (lib.natives != null) {
                    if (lib.getArtifactName().contains("java-objc-bridge") && lib.getArtifactName().contains("natives-osx")) {
                        // Normal repo bundles this in the main jar, use it
                        handler.add(CONFIG_NATIVES, lib.getArtifactNameSkipNatives());
                    } else {
                        handler.add(CONFIG_NATIVES, lib.getArtifactName());
                    }
                }
            }
        } else {
            CleanroomLogger.debug("Resolved: {}", CONFIG_NATIVES);
        }
        return cachedVersion;
    }

    private static Version load(File json, String mcVersion, File... inheritanceDirs) throws JsonSyntaxException, JsonIOException, IOException {
        FileReader reader = new FileReader(json);
        Version v = Utils.GSON.fromJson(reader, Version.class);
        reader.close();
        if (!Strings.isNullOrEmpty(v.inheritsFrom)) {
            boolean found = false;
            for (File inheritDir : inheritanceDirs) {
                File parentFile = new File(inheritDir, v.inheritsFrom + ".json");
                if (parentFile.exists()) {
                    List<File> dirs = new ArrayList<>(inheritanceDirs.length - 1);
                    for (File toAdd : inheritanceDirs) {
                        if (toAdd != inheritDir) {
                            dirs.add(toAdd);
                        }
                    }
                    Version parent = load(new File(inheritDir, v.inheritsFrom + ".json"), mcVersion, dirs.toArray(new File[0]));
                    v.extendFrom(parent);
                    found = true;
                    break;
                }
            }
            // Still hasn't found the inherited
            if (!found) {
                throw new FileNotFoundException("Inherited json file (" + v.inheritsFrom + ") not found! Maybe you are running in offline mode?");
            }
        } else if (v.assetIndex == null) { // Inherit if the assetIndex is missing
            boolean found = false;
            for (File inheritDir : inheritanceDirs) {
                File parentFile = new File(inheritDir, mcVersion + ".json");
                if (parentFile.exists()) {
                    List<File> dirs = new ArrayList<>(inheritanceDirs.length-1);
                    for (File toAdd : inheritanceDirs) {
                        if (toAdd != inheritDir) {
                            dirs.add(toAdd);
                        }
                    }
                    Version parent = load(new File(inheritDir, mcVersion + ".json"), mcVersion, dirs.toArray(new File[0]));
                    v.extendFrom(parent);
                    found = true;
                    break;
                }
            }
            // Still hasn't found the inherited
            if (!found) {
                throw new FileNotFoundException("Inherited json file (" + v.inheritsFrom + ") not found! Maybe you are running in offline mode?");
            }
        }
        return v;
    }

    public String id;
    public Date time;
    public Date releaseTime;
    public String type;
    public String minecraftArguments;
    public String inheritsFrom;
    private List<Library> libraries;
    public String mainClass;
    public int minimumLauncherVersion;
    public String incompatibilityReason;
    public AssetIndexRef assetIndex;
    private Map<String, Download> downloads;
    public List<OSRule> rules;

    private List<Library> _libraries;

    public List<Library> getLibraries() {
        if (_libraries == null) {
            _libraries = new ArrayList<>();
            Library jsr305 = new Library();
            jsr305.name = "com.google.code.findbugs:jsr305:3.0.1";
            _libraries.add(jsr305);
            if (libraries == null) {
                return _libraries;
            }
            for (Library lib : libraries) {
                if (lib.applies()) {
                    _libraries.add(lib);
                }
            }
        }
        return _libraries;
    }

    public Download getClientInfo() {
        return downloads.get("client");
    }

    public Download getServerInfo() {
        return downloads.get("server");
    }

    /**
     * Populates this instance with information from another version json.
     * @param version Version json to extend from
     */
    public void extendFrom(Version version) {
        // Strings. Replace if null.
        if (minecraftArguments == null) {
            minecraftArguments = version.minecraftArguments;
        }
        if (mainClass == null) {
            mainClass = version.mainClass;
        }
        if (incompatibilityReason == null) {
            incompatibilityReason = version.incompatibilityReason;
        }
        if (assetIndex == null) {
            assetIndex = version.assetIndex;
        }
        if (downloads == null) {
            downloads = version.downloads;
        }
        // Lists. Replace if null, add if not.
        if (libraries == null) {
            libraries = version.libraries;
        } else if (version.libraries != null) {
            libraries.addAll(0, version.libraries);
        }
        if (rules == null) {
            rules = version.rules;
        } else if (version.rules != null) {
            rules.addAll(0, version.rules);
        }
    }

}
