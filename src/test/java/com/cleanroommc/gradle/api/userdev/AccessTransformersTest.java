/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.userdev;

import net.minecraftforge.srgutils.IMappingFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTransformersTest {

    private static final String SRG_TO_MCP = """
        net/minecraft/item/ItemStack net/minecraft/item/ItemStack
        \tfield_77994_a stackSize
        \tfunc_77946_l ()Lnet/minecraft/item/ItemStack; copy
        """;

    @TempDir Path directory;

    @Test
    void remapsMembersBothWays() throws IOException {
        var srgToMcp = IMappingFile.load(new ByteArrayInputStream(SRG_TO_MCP.getBytes(StandardCharsets.UTF_8)));
        var srg = """
            # header
            public net.minecraft.item.ItemStack field_77994_a # count
            public-f net.minecraft.item.ItemStack func_77946_l()Lnet/minecraft/item/ItemStack;
            public net.minecraft.item.ItemStack *()
            public net.minecraft.item.ItemStack <init>(I)V
            public net.minecraft.item.ItemStack
            public net.minecraft.Unknown field_1_a
            """;
        var mcp = """
            # header
            public net.minecraft.item.ItemStack stackSize # count
            public-f net.minecraft.item.ItemStack copy()Lnet/minecraft/item/ItemStack;
            public net.minecraft.item.ItemStack *()
            public net.minecraft.item.ItemStack <init>(I)V
            public net.minecraft.item.ItemStack
            public net.minecraft.Unknown field_1_a
            """;

        assertThat(AccessTransformers.remap(srg, srgToMcp)).isEqualTo(mcp);
        assertThat(AccessTransformers.remap(mcp, srgToMcp.reverse())).isEqualTo(srg);
    }

    @Test
    void widensFieldsAndKeepsLines() throws IOException {
        var file = apply(
            "a/Block.java",
            """
            package a;

            public class Block {
                private int x;
                @Deprecated
                private static final int Y = 0;
                int z;
                protected final int w = 0;
            }
            """,
            """
            public a.Block x
            public-f a.Block Y
            protected a.Block z
            protected a.Block w
            """
        );

        assertThat(file).isEqualTo("""
            package a;

            public class Block {
                public int x;
                @Deprecated
                public static int Y = 0;
                protected int z;
                protected final int w = 0;
            }
            """);
    }

    @Test
    void neverNarrowsAndDropsPrivateForDefault() throws IOException {
        var file = apply(
            "a/Block.java",
            """
            package a;

            class Block {
                public int x;
                private int y;
                int z;
            }
            """,
            """
            private a.Block x
            default a.Block y
            default+f a.Block z
            """
        );

        assertThat(file).isEqualTo("""
            package a;

            class Block {
                public int x;
                int y;
                final int z;
            }
            """);
    }

    @Test
    void matchesMethodsByDescriptor() throws IOException {
        var file = apply(
            "a/Block.java",
            """
            package a;

            import java.util.List;

            public class Block<T> {
                private void use(int a) { }
                private void use(String a, List<String>[] b) { }
                private <E> void use(E a, T b) { }
                private Block() { }
                void other() { }
            }
            """,
            """
            public a.Block use(Ljava/lang/String;[Ljava/util/List;)V
            public a.Block use(Ljava/lang/Object;Ljava/lang/Object;)V
            protected a.Block <init>()V
            """
        );

        assertThat(file).isEqualTo("""
            package a;

            import java.util.List;

            public class Block<T> {
                private void use(int a) { }
                public void use(String a, List<String>[] b) { }
                public <E> void use(E a, T b) { }
                protected Block() { }
                void other() { }
            }
            """);
    }

    @Test
    void reachesNestedClassesAndSkipsEnumConstants() throws IOException {
        var file = apply(
            "a/Block.java",
            """
            package a;

            public class Block {
                private class Inner {
                    private Inner(int a) { }
                }
                private enum Kind {
                    A, B;
                    private int id;
                    private Kind() { }
                }
            }
            """,
            """
            public a.Block$Inner
            public a.Block$Inner <init>(La/Block;I)V
            public a.Block$Kind *
            public a.Block$Kind <init>(Ljava/lang/String;I)V
            """
        );

        assertThat(file).isEqualTo("""
            package a;

            public class Block {
                public class Inner {
                    public Inner(int a) { }
                }
                private enum Kind {
                    A, B;
                    public int id;
                    public Kind() { }
                }
            }
            """);
    }

    private String apply(String path, String source, String at) throws IOException {
        var file = this.directory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        AccessTransformers.applyToSources(this.directory, at);
        return Files.readString(file);
    }

}
