package com.cleanroommc.gradle.api.schema;

import com.cleanroommc.gradle.api.util.IO;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Metadata shipped inside the userdev artifact, describing how a mod developer's environment has to be
 * constructed. The loader project writes it with {@code writeUserdevConfig} and a consuming project reads
 * it back to verify that its pipeline lines up with the one the artifact was built by.
 *
 * @param spec              format version of this file, {@link #SPEC}
 * @param minecraftVersion  the Minecraft version the artifact targets
 * @param cleanroomVersion  version of the loader the artifact was cut from
 * @param forgeVersion      the Forge version the loader reports at runtime
 * @param mcpConfig         dependency notation of the MCP config the SRG names come from. A consumer
 *                          <em>must</em> use this exact one: the binpatches and the loader classes are keyed
 *                          against its SRG names
 * @param binpatches        path of the binpatch archive inside the artifact
 * @param clientBinpatches  prefix of the client patch entries inside {@code binpatches}
 * @param serverBinpatches  prefix of the server patch entries inside {@code binpatches}
 * @param srg2mcp           path of the SRG to MCP mapping inside the artifact
 * @param mcp2srg           path of the MCP to SRG mapping inside the artifact
 * @param accessTransformers paths of the loader's access transformers inside the artifact
 * @param libraries         dependency notations the loader needs on a runtime classpath
 * @param group             the loader's maven group, reported to the runtime as {@code FORGE_GROUP}
 * @param runs              how the loader is launched on either side
 */
public record UserdevConfig(
        int spec,
        String minecraftVersion,
        String cleanroomVersion,
        String forgeVersion,
        String mcpConfig,
        String binpatches,
        String clientBinpatches,
        String serverBinpatches,
        String srg2mcp,
        String mcp2srg,
        List<String> accessTransformers,
        List<String> libraries,
        String group,
        Runs runs) {

    public static final int SPEC = 1;

    /**
     * Directory every file below lives under inside the userdev artifact.
     */
    public static final String META = "userdev";
    public static final String FILE_NAME = "config.json";
    public static final String BINPATCHES = "binpatches.zip";
    public static final String CLIENT_BINPATCHES = "binpatch/client/";
    public static final String SERVER_BINPATCHES = "binpatch/server/";
    public static final String SRG2MCP = "srg2mcp.tsrg";
    public static final String MCP2SRG = "mcp2srg.tsrg";
    public static final String DEOBF_LIBRARY = "deobf-library.jar";
    public static final String ATS = "ats";

    /**
     * Path of {@code name} inside the userdev artifact.
     */
    public static String meta(String name) {
        return META + "/" + name;
    }

    public record Runs(Run client, Run server) { }

    /**
     * @param mainClass  the entry point the run task executes
     * @param launchClass the class {@code mainClass} hands off to, passed as the {@code mainClass} variable
     * @param tweakClass  the LaunchWrapper tweaker
     * @param target      the launch target, e.g. {@code fmldevclient}
     */
    public record Run(String mainClass, String launchClass, String tweakClass, String target) { }

    /**
     * Reads the config out of an extracted userdev directory.
     */
    public static UserdevConfig read(File file) {
        return IO.readJson(file, UserdevConfig.class);
    }

    /**
     * Reads the config straight out of a userdev jar.
     */
    public static UserdevConfig readFromJar(File jar) {
        try (var zip = new ZipFile(jar)) {
            var entry = zip.getEntry(meta(FILE_NAME));
            if (entry == null) {
                throw new IllegalStateException(jar + " is not a userdev artifact: it has no " + meta(FILE_NAME) + ".");
            }
            try (var input = zip.getInputStream(entry)) {
                return IO.readJson(input, UserdevConfig.class);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + meta(FILE_NAME) + " from " + jar, e);
        }
    }

}
