package com.cleanroommc.gradle.api.names;

import com.cleanroommc.gradle.api.util.IO;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;

/**
 * The SRG -> name lookups from a legacy MCP names zip ({@code fields.csv}, {@code methods.csv}, {@code params.csv}).
 * Columns are {@code searge, name, side, desc}. The {@code desc} of methods/fields becomes the member's javadoc.
 */
public record CsvNames(Map<String, String> fields, Map<String, String> methods, Map<String, String> params, Map<String, String> docs) {

    /** Reads the three CSVs out of an MCP names zip. */
    public static CsvNames fromZip(File zipFile) {
        var fields = new HashMap<String, String>();
        var methods = new HashMap<String, String>();
        var params = new HashMap<String, String>();
        var docs = new HashMap<String, String>();
        try (var zip = IO.zipIn(zipFile)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith("fields.csv")) {
                    readRecords(zip, fields, docs);
                } else if (name.endsWith("methods.csv")) {
                    readRecords(zip, methods, docs);
                } else if (name.endsWith("params.csv")) {
                    readRecords(zip, params, docs);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read MCP names zip " + zipFile, e);
        }
        return new CsvNames(fields, methods, params, docs);
    }

    /**
     * Reads {@code searge, name, side, desc} records into {@code names} (searge -> name) and {@code docs}
     * (searge -> desc). The first three columns are comma-free identifiers,
     * the {@code desc} is the rest and may be CSV-quoted with embedded commas.
     */
    private static void readRecords(InputStream in, Map<String, String> names, Map<String, String> docs) throws IOException {
        var reader = IO.reader(in, StandardCharsets.UTF_8);
        var line = reader.readLine(); // header
        boolean hasDesc = line != null && line.contains("desc");
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            var parts = line.split(",", 4);
            if (parts.length < 2) {
                continue;
            }
            var searge = parts[0].trim();
            if (searge.isEmpty()) {
                continue;
            }
            var mapped = parts[1].trim();
            if (mapped.isEmpty()) {
                continue;
            }
            names.put(searge, mapped);
            if (hasDesc && parts.length >= 4) {
                var desc = unquote(parts[3]);
                if (!desc.isEmpty()) {
                    docs.put(searge, desc);
                }
            }
        }
    }

    /** Strips surrounding quotes and unescapes doubled quotes from a CSV field. */
    private static String unquote(String field) {
        var value = field.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }

}
