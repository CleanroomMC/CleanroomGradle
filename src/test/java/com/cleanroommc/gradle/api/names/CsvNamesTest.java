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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CsvNamesTest {

    @Test
    void readsQuotedDescsAndSkipsUnnamedRows(@TempDir Path directory) throws IOException {
        var zip = directory.resolve("names.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(
                    out,
                    "fields.csv",
                    "searge,name,side,desc\nfield_1_a,readableField,2,\"A, quoted \"\"field\"\" desc\"\n\n,ignored,2,desc\nfield_2_b,,2,desc\n"
            );
            entry(out, "methods.csv", "searge,name,side,desc\nfunc_1_a,readableMethod,2,Does things.\n");
            entry(out, "params.csv", "param,name,side\np_1_0_,firstArg,2\n");
        }

        var names = CsvNames.fromZip(zip.toFile());

        assertThat(names.fields()).containsOnlyKeys("field_1_a").containsEntry("field_1_a", "readableField");
        assertThat(names.methods()).containsEntry("func_1_a", "readableMethod");
        assertThat(names.params()).containsEntry("p_1_0_", "firstArg");
        assertThat(names.docs()).containsEntry("field_1_a", "A, quoted \"field\" desc").containsEntry("func_1_a", "Does things.");
    }

    private static void entry(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

}
