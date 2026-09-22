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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TinyV2Test {

    @TempDir
    Path directory;

    @Test
    void roundTripsNamesAndEscapedDocs() throws IOException {
        var doc = "back\\slash and\ttab and\nnewline";
        var structure = structure(List.of(new JarStructure.Member("func_1_a", "(I)V")), List.of(new JarStructure.Member("field_1_b", "I")));
        var names = new CsvNames(Map.of("field_1_b", "field"), Map.of("func_1_a", "method"), Map.of("p_1_1_", "arg"), Map.of("func_1_a", doc));

        var flat = read(TinyV2.write(structure, names, Map.of()));

        assertThat(flat.methods()).containsEntry("func_1_a", "method");
        assertThat(flat.fields()).containsEntry("field_1_b", "field");
        assertThat(flat.params()).containsEntry("p_1_1_", "arg");
        assertThat(flat.docs()).containsEntry("func_1_a", doc);
    }

    @Test
    void writesOnlyMembersThatCarryAName() {
        var structure = structure(
                List.of(new JarStructure.Member("func_1_a", "()V"), new JarStructure.Member("func_2_b", "()V")),
                List.of(new JarStructure.Member("field_9_z", "I"))
        );
        var names = new CsvNames(Map.of(), Map.of(), Map.of("p_1_0_", "only", "p_5_1_", "value"), Map.of());

        var text = TinyV2.write(structure, names, Map.of("a/B", List.of(new TinyV2.Constructor("5", "(I)V"), new TinyV2.Constructor("6", "()V"))));

        assertThat(text).contains("func_1_a", "\tm\t(I)V\t<init>\t<init>\n").doesNotContain("func_2_b", "field_9_z", "()V\t<init>");
    }

    private static JarStructure structure(List<JarStructure.Member> methods, List<JarStructure.Member> fields) {
        return new JarStructure(Map.of("a/B", new JarStructure.ClassEntry("a/B", methods, fields)));
    }

    private TinyV2.FlatNames read(String text) throws IOException {
        var file = this.directory.resolve("mappings.tiny2");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return TinyV2.read(file);
    }

}
