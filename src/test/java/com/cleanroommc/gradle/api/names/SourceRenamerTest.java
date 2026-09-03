package com.cleanroommc.gradle.api.names;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRenamerTest {

    @Test
    void renamesMethodFieldAndParamTokens() {
        var names = Map.of(
                "func_123_a", "readableName",
                "field_456_b", "readableField",
                "p_123_0_", "firstArg");
        var out = SourceRenamer.rename(
                List.of("void func_123_a(int p_123_0_) { field_456_b = 1; }"),
                names, Map.of());
        assertEquals("void readableName(int firstArg) { readableField = 1; }", out.getFirst());
    }

    @Test
    void unmappedTokensPassThrough() {
        var out = SourceRenamer.rename(List.of("void func_999_z() { }"), Map.of(), Map.of());
        assertEquals("void func_999_z() { }", out.getFirst());
    }

    @Test
    void capitalizedMixinAccessorFormIsHonored() {
        var out = SourceRenamer.rename(
                List.of("void Func_123_a() { }", "int Field_456_b;"),
                Map.of("func_123_a", "readableName", "field_456_b", "readableField"),
                Map.of());
        assertEquals("void ReadableName() { }", out.get(0));
        assertEquals("int ReadableField;", out.get(1));
    }

    @Test
    void tsrg2ShortFormsAreRenamed() {
        var out = SourceRenamer.rename(
                List.of("void m_123_() { f_456_ = p_1_; }"),
                Map.of("m_123_", "shortMethod", "f_456_", "shortField", "p_1_", "arg"),
                Map.of());
        assertEquals("void shortMethod() { shortField = arg; }", out.getFirst());
    }

    @Test
    void injectsSingleLineJavadocAboveMethod() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "readableName"),
                Map.of("func_123_a", "Does things."));
        assertTrue(out.contains("    /** Does things. */"), out.toString());
        assertTrue(out.contains("    public void readableName() {"), out.toString());
    }

    @Test
    void injectsMultilineJavadocWhenDescCarriesNewlineMarkers() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "readableName"),
                Map.of("func_123_a", "First line\\nSecond line"));
        var joined = String.join("\n", out);
        assertTrue(joined.contains("    /**"), joined);
        assertTrue(joined.contains(" * First line"), joined);
        assertTrue(joined.contains(" * Second line"), joined);
    }

    @Test
    void javadocIsInsertedAboveAnnotations() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    @Override", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "readableName"),
                Map.of("func_123_a", "Docs."));
        var docAt = out.indexOf("    /** Docs. */");
        var annotationAt = out.indexOf("    @Override");
        var methodAt = -1;
        for (var i = 0; i < out.size(); i++) {
            if (out.get(i).contains("readableName")) {
                methodAt = i;
            }
        }
        assertTrue(docAt >= 0 && annotationAt >= 0 && methodAt >= 0, out.toString());
        assertTrue(docAt < annotationAt && annotationAt < methodAt, out.toString());
    }

    @Test
    void injectsFieldAndClassJavadoc() {
        var out = SourceRenamer.rename(
                List.of("package net.example;", "public class Block {", "    public int field_456_b;", "}"),
                Map.of("field_456_b", "readableField"),
                Map.of("field_456_b", "A field.", "net.example.Block", "A block."));
        var joined = String.join("\n", out);
        assertTrue(joined.contains("/** A field. */"), joined);
        assertTrue(joined.contains("/** A block. */"), joined);
    }

}
