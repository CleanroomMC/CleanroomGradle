/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserdevConfigTest {

    @TempDir
    Path directory;

    @Test
    void readsTheSpecOneDocument() throws IOException {
        var artifact = this.directory.resolve("userdev.jar");
        writeConfig(
                artifact,
                """
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
                """
        );

        var config = UserdevConfig.readFromJar(artifact.toFile());
        assertThat(config.minecraftVersion()).isEqualTo("1.12.2");
        assertThat(config.loaderVersion()).isEqualTo("0.7.0");
        assertThat(config.layout().mcpToSrg()).isEqualTo("userdev/mcp2srg.tsrg");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                    "{\"spec\":1,\"minecraftVersion\":\"1.12.2\"} | minecraft, loader, inputs, layout and runs are required",
                    "{\"spec\":1,\"mcpConfig\":\"mcp:config:1\"} | older than 0.15.0"
            }
    )
    void outdatedDocumentsAreRejected(String json, String message) throws IOException {
        var artifact = this.directory.resolve("outdated-userdev.jar");
        writeConfig(artifact, json);

        assertThatThrownBy(() -> UserdevConfig.readFromJar(artifact.toFile())).isInstanceOf(IllegalStateException.class).hasMessageContaining(message);
    }

    @Test
    void missingJarEntryExplainsTheContract() throws IOException {
        var artifact = this.directory.resolve("empty.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(artifact))) {
            output.putNextEntry(new ZipEntry("other.txt"));
            output.write("other".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertThatThrownBy(() -> UserdevConfig.readFromJar(artifact.toFile()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UserdevConfig.meta(UserdevConfig.FILE_NAME));
    }

    @Test
    void validateRejectsAnUnknownSpecAndMissingTools() {
        var config = valid();
        var wrongSpec = new UserdevConfig(2, config.minecraft(), config.loader(), config.inputs(), config.layout(), config.runs());
        assertThatThrownBy(wrongSpec::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unsupported Cleanroom userdev spec 2");

        var missingTool = new UserdevConfig(
                1,
                config.minecraft(),
                config.loader(),
                new UserdevConfig.Inputs("mcp", "mappings", "patches", Map.of()),
                config.layout(),
                config.runs()
        );
        assertThatThrownBy(missingTool::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("inputs.tools.accesstransformer is required");
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
        return new UserdevConfig(
                1,
                new UserdevConfig.Minecraft("1.12.2", download, download),
                new UserdevConfig.Loader("0.7.0", "14.23.5.2860", "com.cleanroommc"),
                new UserdevConfig.Inputs(
                        "mcp:config:1",
                        "mcp:names:1",
                        "patches:initial:1",
                        Map.of("accesstransformer", "t:at:1", "decompiler", "t:dec:1", "mergetool", "t:merge:1")
                ),
                new UserdevConfig.Layout(
                        "a",
                        "b",
                        "c",
                        "d",
                        "e",
                        "f",
                        "g",
                        "h",
                        "i",
                        "j",
                        "k",
                        "l",
                        "m",
                        "n",
                        "o",
                        "p",
                        "q",
                        List.of(),
                        "r",
                        "s",
                        "t"
                ),
                new UserdevConfig.Runs(new UserdevConfig.Run("C", "L", "T", "client"), new UserdevConfig.Run("C", "L", "T", "server"))
        );
    }

}
