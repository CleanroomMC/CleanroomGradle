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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SourceRenamerTest {

    @Test
    void renamesMethodFieldAndParamTokens() {
        var names = Map.of("func_123_a", "readableName", "field_456_b", "readableField", "p_123_0_", "firstArg");
        var out = SourceRenamer.rename(List.of("void func_123_a(int p_123_0_) { field_456_b = 1; }"), names, Map.of());
        assertThat(out.getFirst()).isEqualTo("void readableName(int firstArg) { readableField = 1; }");
    }

    @Test
    void unmappedTokensPassThrough() {
        var out = SourceRenamer.rename(List.of("void func_999_z() { }"), Map.of(), Map.of());
        assertThat(out.getFirst()).isEqualTo("void func_999_z() { }");
    }

    @Test
    void capitalizedMixinAccessorFormIsHonored() {
        var out = SourceRenamer.rename(
                List.of("void Func_123_a() { }", "int Field_456_b;"),
                Map.of("func_123_a", "readableName", "field_456_b", "readableField"),
                Map.of()
        );
        assertThat(out.get(0)).isEqualTo("void ReadableName() { }");
        assertThat(out.get(1)).isEqualTo("int ReadableField;");
    }

    @Test
    void tsrg2ShortFormsAreRenamed() {
        var out = SourceRenamer.rename(
                List.of("void m_123_() { f_456_ = p_1_; }"),
                Map.of("m_123_", "shortMethod", "f_456_", "shortField", "p_1_", "arg"),
                Map.of()
        );
        assertThat(out.getFirst()).isEqualTo("void shortMethod() { shortField = arg; }");
    }

    @Test
    void injectsSingleLineJavadocAboveMethod() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "readableName"),
                Map.of("func_123_a", "Does things.")
        );
        assertThat(out).as(out.toString()).contains("    /** Does things. */");
        assertThat(out).as(out.toString()).contains("    public void readableName() {");
    }

    @Test
    void injectsMultilineJavadocWhenDescCarriesNewlineMarkers() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "readableName"),
                Map.of("func_123_a", "First line\\nSecond line")
        );
        var joined = String.join("\n", out);
        assertThat(joined).as(joined).contains("    /**");
        assertThat(joined).as(joined).contains(" * First line");
        assertThat(joined).as(joined).contains(" * Second line");
    }

    @Test
    void javadocIsInsertedAboveAnnotations() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    @Override", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "readableName"),
                Map.of("func_123_a", "Docs.")
        );
        var docAt = out.indexOf("    /** Docs. */");
        var annotationAt = out.indexOf("    @Override");
        var methodAt = -1;
        for (var i = 0; i < out.size(); i++) {
            if (out.get(i).contains("readableName")) {
                methodAt = i;
            }
        }
        assertThat(docAt >= 0 && annotationAt >= 0 && methodAt >= 0).as(out.toString()).isTrue();
        assertThat(docAt < annotationAt && annotationAt < methodAt).as(out.toString()).isTrue();
    }

    @Test
    void injectsFieldAndClassJavadoc() {
        var out = SourceRenamer.rename(
                List.of("package net.example;", "public class Block {", "    public int field_456_b;", "}"),
                Map.of("field_456_b", "readableField"),
                Map.of("field_456_b", "A field.", "net.example.Block", "A block.")
        );
        var joined = String.join("\n", out);
        assertThat(joined).as(joined).contains("/** A field. */");
        assertThat(joined).as(joined).contains("/** A block. */");
    }

}
