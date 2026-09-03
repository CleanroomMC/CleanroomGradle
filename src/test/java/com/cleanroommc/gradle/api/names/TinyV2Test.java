package com.cleanroommc.gradle.api.names;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TinyV2Test {

    @TempDir
    Path directory;

    @Test
    void writesMethodsFieldsParamsAndDocs() {
        var structure = new JarStructure(Map.of(
                "net/minecraft/Block", new JarStructure.ClassEntry("net/minecraft/Block",
                        List.of(new JarStructure.Member("func_123_a", "()V")),
                        List.of(new JarStructure.Member("field_456_b", "I")))));
        var names = new CsvNames(
                Map.of("field_456_b", "readableField"),
                Map.of("func_123_a", "readableName"),
                Map.of("p_123_0_", "firstArg"),
                Map.of("func_123_a", "Does things.", "field_456_b", "A field."));

        var text = TinyV2.write(structure, names, Map.of());

        assertTrue(text.startsWith(TinyV2.HEADER + "\n"), text);
        assertTrue(text.contains("c\tnet/minecraft/Block\tnet/minecraft/Block\n"), text);
        assertTrue(text.contains("\tm\t()V\tfunc_123_a\treadableName\n"), text);
        assertTrue(text.contains("\t\tp\t0\tp_123_0_\tfirstArg\n"), text);
        assertTrue(text.contains("\t\tc\tDoes things.\n"), text);
        assertTrue(text.contains("\tf\tI\tfield_456_b\treadableField\n"), text);
    }

    @Test
    void skipsUnmappedMembersButKeepsMethodsWithParams() {
        var structure = new JarStructure(Map.of(
                "net/minecraft/Block", new JarStructure.ClassEntry("net/minecraft/Block",
                        List.of(
                                new JarStructure.Member("func_1_a", "()V"),
                                new JarStructure.Member("func_2_b", "()V")),
                        List.of(new JarStructure.Member("field_9_z", "I")))));
        var names = new CsvNames(Map.of(), Map.of(), Map.of("p_1_0_", "only"), Map.of());

        var text = TinyV2.write(structure, names, Map.of());

        assertTrue(text.contains("func_1_a"), text);
        assertTrue(!text.contains("func_2_b"), text);
        assertTrue(!text.contains("field_9_z"), text);
    }

    @Test
    void writesConstructorsOnlyWhenTheyHaveParams() {
        var structure = new JarStructure(Map.of(
                "net/minecraft/Block", new JarStructure.ClassEntry("net/minecraft/Block", List.of(), List.of())));
        var names = new CsvNames(Map.of(), Map.of(), Map.of("p_5_1_", "value"), Map.of());

        var text = TinyV2.write(structure, names,
                Map.of("net/minecraft/Block", List.of(new TinyV2.Constructor("5", "(I)V"))));
        assertTrue(text.contains("\tm\t(I)V\t<init>\t<init>\n"), text);
        assertTrue(text.contains("\t\tp\t1\tp_5_1_\tvalue\n"), text);

        var empty = TinyV2.write(structure, names, Map.of());
        assertEquals(TinyV2.HEADER + "\n", empty);
    }

    @Test
    void escapesAndUnescapesDocs() throws IOException {
        var structure = new JarStructure(Map.of(
                "a/B", new JarStructure.ClassEntry("a/B",
                        List.of(new JarStructure.Member("func_1_a", "()V")), List.of())));
        var doc = "back\\slash and\ttab and\nnewline";
        var names = new CsvNames(Map.of(), Map.of("func_1_a", "renamed"), Map.of(), Map.of("func_1_a", doc));

        var flat = read(TinyV2.write(structure, names, Map.of()));
        assertEquals("renamed", flat.methods().get("func_1_a"));
        assertEquals(doc, flat.docs().get("func_1_a"));
    }

    @Test
    void readsCommentsSkipsHeadersAndIgnoresBadRows() throws IOException {
        var file = this.directory.resolve("mappings.tiny2");
        Files.writeString(file, TinyV2.HEADER + "\n"
                + "# a comment\n"
                + "\n"
                + "c\ta/B\ta/B\n"
                + "\tm\t()V\tfunc_1_a\trenamed\n"
                + "\t\tc\tA doc\n"
                + "\t\tp\t0\tp_1_0_\targ\n"
                + "\tf\tI\tfield_1_b\trenamedField\n"
                + "\tm\t()V\t\t\n", StandardCharsets.UTF_8);

        var flat = TinyV2.read(file);
        assertEquals("renamed", flat.methods().get("func_1_a"));
        assertEquals("A doc", flat.docs().get("func_1_a"));
        assertEquals("arg", flat.params().get("p_1_0_"));
        assertEquals("renamedField", flat.fields().get("field_1_b"));
    }

    private TinyV2.FlatNames read(String text) throws IOException {
        var file = this.directory.resolve("roundtrip-" + System.nanoTime() + ".tiny2");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return TinyV2.read(file);
    }

}
