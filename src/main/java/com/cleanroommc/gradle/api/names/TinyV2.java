package com.cleanroommc.gradle.api.names;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Tiny v2 reader/writer for an SRG -> named mapping (two namespaces: {@code srg}, {@code named}).
 *
 * <p>The writer joins a jar's structure (for descriptors) with the MCP CSV name lookups.
 * The reader collapses a Tiny2 file back into flat SRG -> name maps for source-level renaming.</p>
 */
public final class TinyV2 {

    public static final String HEADER = "tiny\t2\t0\tsrg\tnamed";

    private TinyV2() { }

    /** Flat SRG -> named lookups parsed from a Tiny2 file, plus member javadocs keyed by SRG id. */
    public record FlatNames(Map<String, String> methods, Map<String, String> fields, Map<String, String> params, Map<String, String> docs) { }

    /** A constructor from {@code constructors.txt}: its param-number key ({@code i<id>}) and SRG descriptor. */
    public record Constructor(String numberKey, String descriptor) { }

    /** Builds Tiny2 text mapping SRG ids to MCP names, using {@code structure} for descriptors. */
    public static String write(JarStructure structure, CsvNames names, Map<String, List<Constructor>> constructorsByClass) {
        var paramsByMethodNum = groupParams(names.params());
        var out = new StringBuilder(1 << 20);
        out.append(HEADER).append('\n');
        for (var cls : structure.classes().values()) {
            boolean wroteClass = false;
            for (var method : cls.methods()) {
                var named = names.methods().get(method.name());
                var params = paramsByMethodNum.get(methodNumber(method.name()));
                boolean hasParams = params != null && !params.isEmpty();
                if (named == null && !hasParams) {
                    continue;
                }
                wroteClass = ensureClass(out, cls, wroteClass);
                out.append('\t').append('m')
                        .append('\t').append(method.descriptor())
                        .append('\t').append(method.name())
                        .append('\t').append(named != null ? named : method.name())
                        .append('\n');
                appendComment(out, names.docs().get(method.name()));
                if (hasParams) {
                    for (var param : params) {
                        out.append('\t').append('\t')
                                .append('p').append('\t').append(param.lvIndex())
                                .append('\t').append(param.srg())
                                .append('\t').append(param.named())
                                .append('\n');
                    }
                }
            }
            for (var field : cls.fields()) {
                String named = names.fields().get(field.name());
                if (named == null) {
                    continue;
                }
                wroteClass = ensureClass(out, cls, wroteClass);
                out.append('\t').append('f').append('\t').append(field.descriptor())
                        .append('\t').append(field.name())
                        .append('\t').append(named)
                        .append('\n');
                appendComment(out, names.docs().get(field.name()));
            }
            // Constructors: emit <init> + their p_i parameters (they have no func_ parent)
            for (var ctor : constructorsByClass.getOrDefault(cls.internalName(), List.of())) {
                var params = paramsByMethodNum.get(ctor.numberKey());
                if (params == null || params.isEmpty()) {
                    continue;
                }
                wroteClass = ensureClass(out, cls, wroteClass);
                out.append('\t').append('m')
                        .append('\t').append(ctor.descriptor())
                        .append('\t').append("<init>")
                        .append('\t').append("<init>")
                        .append('\n');
                for (var p : params) {
                    out.append('\t').append('\t').append('p')
                            .append('\t').append(p.lvIndex())
                            .append('\t').append(p.srg())
                            .append('\t').append(p.named())
                            .append('\n');
                }
            }
        }
        return out.toString();
    }

    private static void appendComment(StringBuilder out, String doc) {
        if (doc != null && !doc.isEmpty()) {
            out.append('\t').append('\t').append('c').append('\t').append(escape(doc)).append('\n');
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescape(String s) {
        var builder = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                builder.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> n;
                });
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static boolean ensureClass(StringBuilder out, JarStructure.ClassEntry cls, boolean wroteClass) {
        if (!wroteClass) {
            out.append('c').append('\t').append(cls.internalName())
                    .append('\t').append(cls.internalName())
                    .append('\n');
        }
        return true;
    }

    private record ParamEntry(int lvIndex, String srg, String named) { }

    /** Groups {@code p_<methodNum>_<slot>_} params by their method number. */
    private static Map<String, List<ParamEntry>> groupParams(Map<String, String> params) {
        var byMethod = new HashMap<String, List<ParamEntry>>();
        for (var e : params.entrySet()) {
            var srg = e.getKey(); // p_<num>_<slot>_
            var parts = srg.split("_");
            if (parts.length < 3) {
                continue;
            }
            var num = parts[1];
            int slot;
            try {
                slot = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ex) {
                continue;
            }
            byMethod.computeIfAbsent(num, k -> new ArrayList<>()).add(new ParamEntry(slot, srg, e.getValue()));
        }
        return byMethod;
    }

    private static String methodNumber(String funcName) {
        // func_<num>_<suffix>
        String[] parts = funcName.split("_");
        return parts.length >= 2 ? parts[1] : "";
    }

    /** Parses a Tiny2 file into flat SRG -> named maps. */
    public static FlatNames read(Path file) {
        var methods = new HashMap<String, String>();
        var fields = new HashMap<String, String>();
        var params = new HashMap<String, String>();
        var docs = new HashMap<String, String>();
        try {
            String lastMemberSrg = null;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] t = line.split("\t", -1);
                // Leading empty tokens encode indentation, find the first non-empty token
                int i = 0;
                while (i < t.length && t[i].isEmpty()) {
                    i++;
                }
                if (i >= t.length) {
                    continue;
                }
                switch (t[i]) {
                    case "m" -> lastMemberSrg = put(methods, t, i, 2); // m <desc> <srg> <named>
                    case "f" -> lastMemberSrg = put(fields, t, i, 2);  // f <desc> <srg> <named>
                    case "p" -> put(params, t, i, 2);                  // p <lvIndex> <srg> <named>
                    case "c" -> {
                        if (i == 0) {
                            lastMemberSrg = null; // Class declaration line
                        } else if (lastMemberSrg != null && i + 1 < t.length) {
                            docs.put(lastMemberSrg, unescape(t[i + 1])); // Comment for the most recent member
                        }
                    }
                    default -> { /* header / class */ }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Tiny2 mappings " + file, e);
        }
        return new FlatNames(methods, fields, params, docs);
    }

    private static String put(Map<String, String> target, String[] tokens, int typeIndex, int srgOffset) {
        int srgIndex = typeIndex + srgOffset;
        if (srgIndex + 1 < tokens.length) {
            String srg = tokens[srgIndex];
            String named = tokens[srgIndex + 1];
            if (!srg.isEmpty() && !named.isEmpty()) {
                target.put(srg, named);
                return srg;
            }
        }
        return null;
    }

}
