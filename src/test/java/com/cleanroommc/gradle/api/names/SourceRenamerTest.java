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
    void renamesEverySrgTokenForm() {
        var names = Map.of("func_123_a", "method", "field_456_b", "field", "p_123_0_", "arg", "m_7_", "shortMethod", "f_8_", "shortField");
        var out = SourceRenamer.rename(
                List.of("void func_123_a(int p_123_0_) { field_456_b = 1; }", "void Func_123_a() { m_7_(f_8_); func_999_z(); }"),
                names,
                Map.of()
        );

        assertThat(out).containsExactly("void method(int arg) { field = 1; }", "void Method() { shortMethod(shortField); func_999_z(); }");
    }

    @Test
    void placesJavadocAboveAnnotations() {
        var out = SourceRenamer.rename(
                List.of("public class Block {", "    @Override", "    public void func_123_a() {", "    }", "}"),
                Map.of("func_123_a", "method"),
                Map.of("func_123_a", "Docs.")
        );

        assertThat(out).containsSubsequence("    /** Docs. */", "    @Override", "    public void method() {");
    }

}
