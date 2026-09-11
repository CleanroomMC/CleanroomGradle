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
    void writesMethodsFieldsParamsAndDocs() {
        var structure = new JarStructure(
                Map.of(
                        "net/minecraft/Block",
                        new JarStructure.ClassEntry(
                                "net/minecraft/Block",
                                List.of(new JarStructure.Member("func_123_a", "()V")),
                                List.of(new JarStructure.Member("field_456_b", "I"))
                        )
                )
        );
        var names = new CsvNames(
                Map.of("field_456_b", "readableField"),
                Map.of("func_123_a", "readableName"),
                Map.of("p_123_0_", "firstArg"),
                Map.of("func_123_a", "Does things.", "field_456_b", "A field.")
        );

        var text = TinyV2.write(structure, names, Map.of());

        assertThat(text).as(text).startsWith(TinyV2.HEADER + "\n");
        assertThat(text).as(text).contains("c\tnet/minecraft/Block\tnet/minecraft/Block\n");
        assertThat(text).as(text).contains("\tm\t()V\tfunc_123_a\treadableName\n");
        assertThat(text).as(text).contains("\t\tp\t0\tp_123_0_\tfirstArg\n");
        assertThat(text).as(text).contains("\t\tc\tDoes things.\n");
        assertThat(text).as(text).contains("\tf\tI\tfield_456_b\treadableField\n");
    }

    @Test
    void skipsUnmappedMembersButKeepsMethodsWithParams() {
        var structure = new JarStructure(
                Map.of(
                        "net/minecraft/Block",
                        new JarStructure.ClassEntry(
                                "net/minecraft/Block",
                                List.of(new JarStructure.Member("func_1_a", "()V"), new JarStructure.Member("func_2_b", "()V")),
                                List.of(new JarStructure.Member("field_9_z", "I"))
                        )
                )
        );
        var names = new CsvNames(Map.of(), Map.of(), Map.of("p_1_0_", "only"), Map.of());

        var text = TinyV2.write(structure, names, Map.of());

        assertThat(text).as(text).contains("func_1_a");
        assertThat(text).as(text).doesNotContain("func_2_b");
        assertThat(text).as(text).doesNotContain("field_9_z");
    }

    @Test
    void writesConstructorsOnlyWhenTheyHaveParams() {
        var structure = new JarStructure(Map.of("net/minecraft/Block", new JarStructure.ClassEntry("net/minecraft/Block", List.of(), List.of())));
        var names = new CsvNames(Map.of(), Map.of(), Map.of("p_5_1_", "value"), Map.of());

        var text = TinyV2.write(structure, names, Map.of("net/minecraft/Block", List.of(new TinyV2.Constructor("5", "(I)V"))));
        assertThat(text).as(text).contains("\tm\t(I)V\t<init>\t<init>\n");
        assertThat(text).as(text).contains("\t\tp\t1\tp_5_1_\tvalue\n");

        var empty = TinyV2.write(structure, names, Map.of());
        assertThat(empty).isEqualTo(TinyV2.HEADER + "\n");
    }

    @Test
    void escapesAndUnescapesDocs() throws IOException {
        var structure = new JarStructure(Map.of("a/B", new JarStructure.ClassEntry("a/B", List.of(new JarStructure.Member("func_1_a", "()V")), List.of())));
        var doc = "back\\slash and\ttab and\nnewline";
        var names = new CsvNames(Map.of(), Map.of("func_1_a", "renamed"), Map.of(), Map.of("func_1_a", doc));

        var flat = read(TinyV2.write(structure, names, Map.of()));
        assertThat(flat.methods().get("func_1_a")).isEqualTo("renamed");
        assertThat(flat.docs().get("func_1_a")).isEqualTo(doc);
    }

    @Test
    void readsCommentsSkipsHeadersAndIgnoresBadRows() throws IOException {
        var file = this.directory.resolve("mappings.tiny2");
        Files.writeString(
                file,
                TinyV2.HEADER + "\n" + "# a comment\n" + "\n" + "c\ta/B\ta/B\n" + "\tm\t()V\tfunc_1_a\trenamed\n" + "\t\tc\tA doc\n" + "\t\tp\t0\tp_1_0_\targ\n" +
                        "\tf\tI\tfield_1_b\trenamedField\n" + "\tm\t()V\t\t\n",
                StandardCharsets.UTF_8
        );

        var flat = TinyV2.read(file);
        assertThat(flat.methods().get("func_1_a")).isEqualTo("renamed");
        assertThat(flat.docs().get("func_1_a")).isEqualTo("A doc");
        assertThat(flat.params().get("p_1_0_")).isEqualTo("arg");
        assertThat(flat.fields().get("field_1_b")).isEqualTo("renamedField");
    }

    private TinyV2.FlatNames read(String text) throws IOException {
        var file = this.directory.resolve("roundtrip-" + System.nanoTime() + ".tiny2");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return TinyV2.read(file);
    }

}
