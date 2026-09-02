package com.cleanroommc.gradle.api.schema;

import com.cleanroommc.gradle.api.util.IO;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Cleanroom userdev specification 1. Dependencies belong to Gradle module metadata. This document owns only
 * the reproducible Minecraft materialization pipeline and launch contract.
 */
public record UserdevConfig(int spec, Minecraft minecraft, Loader loader, Inputs inputs, Layout layout, Runs runs) {

    public static final int SPEC = 1;
    public static final String META = "userdev";
    public static final String FILE_NAME = "config.json";
    public static final String BINPATCHES = "binpatches.zip";
    public static final String CLIENT_BINPATCHES = "binpatch/client/";
    public static final String SERVER_BINPATCHES = "binpatch/server/";
    public static final String OBF2SRG = "obf2srg.tsrg";
    public static final String SRG2MCP = "srg2mcp.tsrg";
    public static final String MCP2SRG = "mcp2srg.tsrg";
    public static final String DEOBF_LIBRARY = "deobf-library.jar";
    public static final String SOURCE_INPUT = "source-input.jar";
    public static final String ACCESS = "access.txt";
    public static final String CONSTRUCTORS = "constructors.txt";
    public static final String EXCEPTIONS = "exceptions.txt";
    public static final String METHODS = "methods.csv";
    public static final String FIELDS = "fields.csv";
    public static final String PARAMS = "params.csv";
    public static final String INITIAL_PATCHES = "initial-patches";
    public static final String ATS = "ats";
    public static final String SAS = "cleanroom.sas";
    public static final String PATCHES = "patches";
    public static final String LOADER_SOURCES = "loader-sources";

    public static String meta(String name) {
        return META + "/" + name;
    }

    public String minecraftVersion() {
        return minecraft.version();
    }

    public String loaderVersion() {
        return loader.version();
    }

    public void validate() {
        if (spec != SPEC) {
            throw new IllegalStateException("Unsupported Cleanroom userdev spec " + spec + "; expected spec " + SPEC + ".");
        }
        if (minecraft == null || loader == null || inputs == null || layout == null || runs == null) {
            throw new IllegalStateException("Invalid Cleanroom userdev spec 1: minecraft, loader, inputs, layout and runs are required.");
        }
        require("minecraft.version", minecraft.version());
        requireDownload("minecraft.client", minecraft.client());
        requireDownload("minecraft.server", minecraft.server());
        require("loader.version", loader.version());
        require("loader.forgeVersion", loader.forgeVersion());
        require("loader.group", loader.group());
        require("inputs.mcpConfig", inputs.mcpConfig());
        require("inputs.mappings", inputs.mappings());
        require("inputs.initialPatches", inputs.initialPatches());
        if (inputs.tools() == null) {
            throw invalid("inputs.tools");
        }
        for (var tool : List.of("accesstransformer", "decompiler", "mergetool")) {
            require("inputs.tools." + tool, inputs.tools().get(tool));
        }
        require("layout.binpatches", layout.binpatches());
        require("layout.clientBinpatches", layout.clientBinpatches());
        require("layout.serverBinpatches", layout.serverBinpatches());
        require("layout.obfToSrg", layout.obfToSrg());
        require("layout.srgToMcp", layout.srgToMcp());
        require("layout.mcpToSrg", layout.mcpToSrg());
        require("layout.access", layout.access());
        require("layout.constructors", layout.constructors());
        require("layout.exceptions", layout.exceptions());
        require("layout.methods", layout.methods());
        require("layout.fields", layout.fields());
        require("layout.params", layout.params());
        require("layout.deobfLibrary", layout.deobfLibrary());
        require("layout.sourceInput", layout.sourceInput());
        require("layout.clientExtra", layout.clientExtra());
        require("layout.serverExtra", layout.serverExtra());
        require("layout.initialPatches", layout.initialPatches());
        require("layout.sideAnnotationStrippers", layout.sideAnnotationStrippers());
        require("layout.patches", layout.patches());
        require("layout.loaderSources", layout.loaderSources());
        if (layout.accessTransformers() == null) {
            throw invalid("layout.accessTransformers");
        }
        requireRun("runs.client", runs.client());
        requireRun("runs.server", runs.server());
    }

    private static void requireDownload(String name, Download download) {
        if (download == null) {
            throw invalid(name);
        }
        require(name + ".url", download.url());
        require(name + ".sha1", download.sha1());
    }

    private static void requireRun(String name, Run run) {
        if (run == null) {
            throw invalid(name);
        }
        require(name + ".mainClass", run.mainClass());
        require(name + ".launchClass", run.launchClass());
        require(name + ".tweakClass", run.tweakClass());
        require(name + ".target", run.target());
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(name);
        }
    }

    private static IllegalStateException invalid(String field) {
        return new IllegalStateException("Invalid Cleanroom userdev spec 1: " + field + " is required.");
    }

    public record Minecraft(String version, Download client, Download server) { }

    public record Download(String url, String sha1) { }

    public record Loader(String version, String forgeVersion, String group) { }

    public record Inputs(String mcpConfig, String mappings, String initialPatches, Map<String, String> tools) { }

    public record Layout(String binpatches, String clientBinpatches, String serverBinpatches,
                         String obfToSrg, String srgToMcp, String mcpToSrg,
                         String access, String constructors, String exceptions,
                         String methods, String fields, String params,
                         String deobfLibrary, String sourceInput, String clientExtra, String serverExtra,
                         String initialPatches, List<String> accessTransformers,
                         String sideAnnotationStrippers, String patches, String loaderSources) { }

    public record Runs(Run client, Run server) { }

    public record Run(String mainClass, String launchClass, String tweakClass, String target) { }

    public static UserdevConfig read(File file) {
        try {
            return parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    public static UserdevConfig readFromJar(File jar) {
        try (var zip = new ZipFile(jar)) {
            var entry = zip.getEntry(meta(FILE_NAME));
            if (entry == null) {
                throw new IllegalStateException(jar + " is not a Cleanroom userdev spec 1 artifact: missing " + meta(FILE_NAME) + ".");
            }
            try (var input = zip.getInputStream(entry)) {
                return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + meta(FILE_NAME) + " from " + jar, e);
        }
    }

    private static UserdevConfig parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("mcpConfig") && !root.has("layout")) {
            throw new IllegalStateException("This userdev artifact was produced by CleanroomGradle older than 0.15.0. "
                    + "Rebuild it with 0.15.0 or newer, or use the plugin version that produced it.");
        }
        var config = IO.readJson(json, UserdevConfig.class);
        config.validate();
        return config;
    }

}
