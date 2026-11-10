/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * The packages vanilla Minecraft defines its classes in, read from obf to SRG mappings.
 */
public final class MinecraftPackages {

    private MinecraftPackages() { }

    /**
     * Reads the package paths, each ending in {@code /}, such as {@code net/minecraft/item/}.
     *
     * @param obfToSrg the obf to SRG mappings in TSRG format
     */
    public static Set<String> read(Path obfToSrg) throws IOException {
        var packages = new TreeSet<String>();
        for (String line : Files.readAllLines(obfToSrg, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            String mapped = line.split(" ", 3)[1];
            packages.add(mapped.substring(0, mapped.lastIndexOf('/') + 1));
        }
        return packages;
    }

    /**
     * Whether an entry's own package is one of them. A subpackage only matches when it is listed itself.
     *
     * @param packages what {@link #read} returned
     * @param path     an entry path, such as {@code net/minecraft/item/Item.class}
     */
    public static boolean contains(Set<String> packages, String path) {
        return packages.contains(path.substring(0, path.lastIndexOf('/') + 1));
    }

    /**
     * Matches the files in vanilla packages.
     * Directories never match, a custom package nested in a vanilla one is still reached.
     *
     * @param obfToSrg the obf to SRG mappings in TSRG format, read once on first use
     */
    public static Spec<FileTreeElement> files(Provider<RegularFile> obfToSrg) {
        return new VanillaFiles(obfToSrg);
    }

    private static final class VanillaFiles implements Spec<FileTreeElement> {

        private final Provider<RegularFile> obfToSrg;

        private transient Set<String> packages;

        private VanillaFiles(Provider<RegularFile> obfToSrg) {
            this.obfToSrg = obfToSrg;
        }

        @Override
        public boolean isSatisfiedBy(FileTreeElement element) {
            if (element.isDirectory()) {
                return false;
            }
            if (this.packages == null) {
                try {
                    this.packages = read(this.obfToSrg.get().getAsFile().toPath());
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read " + this.obfToSrg.get(), e);
                }
            }
            return contains(this.packages, element.getPath());
        }

    }

}
