package com.cleanroommc.gradle.api.userdev;

import com.cleanroommc.gradle.api.util.IO;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class UserdevArchive {

    static void select(File input, File output, Predicate<String> include, String stripPrefix) {
        try (var zip = new ZipFile(input); var out = IO.zipOut(output)) {
            var entries = new TreeMap<String, ZipEntry>();
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                var entry = enumeration.nextElement();
                if (!entry.isDirectory() && include.test(entry.getName())) {
                    entries.put(entry.getName(), entry);
                }
            }
            for (var item : entries.entrySet()) {
                var name = item.getKey().substring(stripPrefix.length());
                if (!name.isEmpty()) {
                    IO.writeEntry(out, name, IO.readEntry(zip, item.getValue()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize " + input, e);
        }
    }

    private UserdevArchive() { }

}
