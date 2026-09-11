/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.names;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CsvNamesTest {

    @TempDir
    Path directory;

    @Test
    void readsThreeCsvsSkipsBlanksAndUnquotesDescs() throws IOException {
        var zip = this.directory.resolve("names.zip");
        try (var out = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            entry(
                    out,
                    "fields.csv",
                    "searge,name,side,desc\n" + "field_1_a,readableField,2,\"A, quoted \"\"field\"\" desc\"\n" + "\n" + ",ignored,2,desc\n" +
                            "field_2_b,,2,desc\n"
            );
            entry(out, "methods.csv", "searge,name,side,desc\n" + "func_1_a,readableMethod,2,Does things.\n");
            entry(out, "params.csv", "param,name,side\n" + "p_1_0_,firstArg,2\n");
        }

        var names = CsvNames.fromZip(zip.toFile());
        assertThat(names.fields().get("field_1_a")).isEqualTo("readableField");
        assertThat(names.docs().get("field_1_a")).isEqualTo("A, quoted \"field\" desc");
        assertThat(names.methods().get("func_1_a")).isEqualTo("readableMethod");
        assertThat(names.docs().get("func_1_a")).isEqualTo("Does things.");
        assertThat(names.params().get("p_1_0_")).isEqualTo("firstArg");
        assertThat(names.fields()).doesNotContainKey("").doesNotContainKey("field_2_b");
    }

    @Test
    void mcpAndTiny2IdsAndFlatMergeOrder() throws IOException {
        assertThat(NamesSource.mcpId("mcp_stable", "39-1.12")).isEqualTo("mcp:stable_39-1.12");
        assertThat(NamesSource.mcpId("stable", "39-1.12")).isEqualTo("mcp:stable_39-1.12");

        var tiny = this.directory.resolve("mappings.tiny2");
        Files.writeString(tiny, TinyV2.HEADER + "\n", StandardCharsets.UTF_8);
        var id = NamesSource.tiny2Id(tiny.toFile());
        assertThat(id).as(id).startsWith("tiny2:");
        assertThat(id.length()).isEqualTo(6 + 12);

        var source = new NamesSource("mcp:stable_39-1.12", Map.of("func_1_a", "method"), Map.of("field_1_a", "field"), Map.of("p_1_0_", "arg"), Map.of());
        assertThat(source.flatNames()).isEqualTo(Map.of("func_1_a", "method", "field_1_a", "field", "p_1_0_", "arg"));
    }

    @Test
    void fromTiny2ResolvesFlatNames() throws IOException {
        var tiny = this.directory.resolve("names.tiny2");
        Files.writeString(
                tiny,
                TinyV2.HEADER + "\n" + "c\ta/B\ta/B\n" + "\tm\t()V\tfunc_1_a\trenamed\n" + "\tf\tI\tfield_1_a\trenamedField\n",
                StandardCharsets.UTF_8
        );
        var source = NamesSource.fromTiny2(tiny.toFile());
        assertThat(source.methods().get("func_1_a")).isEqualTo("renamed");
        assertThat(source.fields().get("field_1_a")).isEqualTo("renamedField");
        assertThat(source.id()).startsWith("tiny2:");
    }

    private static void entry(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

}
