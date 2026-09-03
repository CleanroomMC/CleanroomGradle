package com.cleanroommc.gradle.api.schema;

import com.cleanroommc.gradle.api.userdev.ExtractUserdevExtra;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevClasses;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevSources;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserdevConfigTest {

    @TempDir
    Path directory;

    @Test
    void specOneUsesTheNewNestedArtifactContract() throws IOException {
        var artifact = this.directory.resolve("userdev.jar");
        writeConfig(artifact, """
                {
                  "spec": 1,
                  "minecraft": {
                    "version": "1.12.2",
                    "client": {"url": "https://example.invalid/client.jar", "sha1": "client"},
                    "server": {"url": "https://example.invalid/server.jar", "sha1": "server"}
                  },
                  "loader": {"version": "0.7.0", "forgeVersion": "14.23.5.2860", "group": "com.cleanroommc"},
                  "inputs": {
                    "mcpConfig": "mcp:config:1",
                    "mappings": "mcp:names:1",
                    "initialPatches": "patches:initial:1",
                    "tools": {"accesstransformer":"tools:at:1", "decompiler":"tools:decompiler:1", "mergetool":"tools:merge:1"}
                  },
                  "layout": {
                    "binpatches":"userdev/binpatches.zip","clientBinpatches":"binpatch/client/","serverBinpatches":"binpatch/server/",
                    "obfToSrg":"userdev/obf2srg.tsrg","srgToMcp":"userdev/srg2mcp.tsrg","mcpToSrg":"userdev/mcp2srg.tsrg",
                    "access":"userdev/access.txt","constructors":"userdev/constructors.txt","exceptions":"userdev/exceptions.txt",
                    "methods":"userdev/methods.csv","fields":"userdev/fields.csv","params":"userdev/params.csv",
                    "deobfLibrary":"userdev/deobf-library.jar","sourceInput":"userdev/source-input.jar",
                    "clientExtra":"userdev/client-extra","serverExtra":"userdev/server-extra",
                    "initialPatches":"userdev/initial-patches","accessTransformers":[],"sideAnnotationStrippers":"userdev/cleanroom.sas",
                    "patches":"userdev/patches","loaderSources":"userdev/loader-sources"
                  },
                  "runs": {
                    "client": {"mainClass": "Client", "launchClass": "Launch", "tweakClass": "ClientTweaker", "target": "client"},
                    "server": {"mainClass": "Server", "launchClass": "Launch", "tweakClass": "ServerTweaker", "target": "server"}
                  }
                }
                """);

        var config = UserdevConfig.readFromJar(artifact.toFile());
        assertEquals("1.12.2", config.minecraftVersion());
        assertEquals("0.7.0", config.loaderVersion());
        assertEquals("userdev/mcp2srg.tsrg", config.layout().mcpToSrg());
    }

    @Test
    void previousFlatSpecOneIsRejected() throws IOException {
        var artifact = this.directory.resolve("old-userdev.jar");
        writeConfig(artifact, """
                {"spec":1,"minecraftVersion":"1.12.2","cleanroomVersion":"0.7.0","libraries":[]}
                """);

        var failure = assertThrows(IllegalStateException.class,
                () -> UserdevConfig.readFromJar(artifact.toFile()));
        assertTrue(failure.getMessage().contains("minecraft, loader, inputs, layout and runs are required"));
    }

    @Test
    void legacyArtifactWithoutLayoutExplainsTheVersionSkew() throws IOException {
        var artifact = this.directory.resolve("legacy-userdev.jar");
        writeConfig(artifact, """
                {"spec":1,"mcpConfig":"mcp:config:1"}
                """);

        var failure = assertThrows(IllegalStateException.class,
                () -> UserdevConfig.readFromJar(artifact.toFile()));
        assertTrue(failure.getMessage().contains("older than 0.15.0"), failure.getMessage());
    }

    @Test
    void missingJarEntryExplainsTheContract() throws IOException {
        var artifact = this.directory.resolve("empty.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(artifact))) {
            output.putNextEntry(new ZipEntry("other.txt"));
            output.write("other".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        var failure = assertThrows(IllegalStateException.class,
                () -> UserdevConfig.readFromJar(artifact.toFile()));
        assertTrue(failure.getMessage().contains(UserdevConfig.meta(UserdevConfig.FILE_NAME)), failure.getMessage());
    }

    @Test
    void wrongSpecAndMissingFieldsAreRejected() {
        var config = valid();
        var wrongSpec = new UserdevConfig(2, config.minecraft(), config.loader(), config.inputs(),
                config.layout(), config.runs());
        assertTrue(assertThrows(IllegalStateException.class, wrongSpec::validate)
                .getMessage().contains("Unsupported Cleanroom userdev spec 2"));

        var missingRuns = new UserdevConfig(1, config.minecraft(), config.loader(), config.inputs(),
                config.layout(), null);
        assertTrue(assertThrows(IllegalStateException.class, missingRuns::validate)
                .getMessage().contains("minecraft, loader, inputs, layout and runs are required"));

        var missingVersion = new UserdevConfig(1,
                new UserdevConfig.Minecraft(" ", config.minecraft().client(), config.minecraft().server()),
                config.loader(), config.inputs(), config.layout(), config.runs());
        assertTrue(assertThrows(IllegalStateException.class, missingVersion::validate)
                .getMessage().contains("minecraft.version is required"));

        var missingTool = new UserdevConfig(1, config.minecraft(), config.loader(),
                new UserdevConfig.Inputs("mcp", "mappings", "patches", java.util.Map.of()),
                config.layout(), config.runs());
        assertTrue(assertThrows(IllegalStateException.class, missingTool::validate)
                .getMessage().contains("inputs.tools.accesstransformer is required"));
    }

    @Test
    void metaPrefixesEntries() {
        assertEquals("userdev/config.json", UserdevConfig.meta(UserdevConfig.FILE_NAME));
    }

    @Test
    void everyMaterializationOperationIsCacheable() {
        assertTrue(MaterializeUserdevClasses.class.isAnnotationPresent(CacheableTransform.class));
        assertTrue(MaterializeUserdevSources.class.isAnnotationPresent(CacheableTransform.class));
        assertTrue(ExtractUserdevExtra.class.isAnnotationPresent(CacheableTransform.class));
    }

    private static void writeConfig(Path artifact, String json) throws IOException {
        try (var output = new JarOutputStream(Files.newOutputStream(artifact))) {
            output.putNextEntry(new ZipEntry(UserdevConfig.meta(UserdevConfig.FILE_NAME)));
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static UserdevConfig valid() {
        var download = new UserdevConfig.Download("https://example.invalid/a.jar", "sha1");
        return new UserdevConfig(1,
                new UserdevConfig.Minecraft("1.12.2", download, download),
                new UserdevConfig.Loader("0.7.0", "14.23.5.2860", "com.cleanroommc"),
                new UserdevConfig.Inputs("mcp:config:1", "mcp:names:1", "patches:initial:1",
                        java.util.Map.of("accesstransformer", "t:at:1", "decompiler", "t:dec:1", "mergetool", "t:merge:1")),
                new UserdevConfig.Layout("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",
                        "m", "n", "o", "p", "q", java.util.List.of(), "r", "s", "t"),
                new UserdevConfig.Runs(
                        new UserdevConfig.Run("C", "L", "T", "client"),
                        new UserdevConfig.Run("C", "L", "T", "server")));
    }

}
