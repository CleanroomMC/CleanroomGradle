package com.cleanroommc.gradle.api.names;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renames SRG identifiers in decompiled source to MCP names and injects member javadocs.
 * The source-level half of ForgeGradle's {@code McpNames}. Pure (no I/O) so it can be unit-tested.
 *
 * <p>Mirrors the inline logic in {@link com.cleanroommc.gradle.api.task.mcp.RemapSrg2Mcp}.</p>
 */
public final class SourceRenamer {

    // Token matcher: capitalized Func_/Field_ (Mixin accessors), TSRG2 m_/f_, p_i constructor params
    private static final Pattern SRG_TOKEN = Pattern.compile(
            "[fF]unc_\\d+_[a-zA-Z_]+|m_\\d+_|[fF]ield_\\d+_[a-zA-Z_]+|f_\\d+_|p_\\w+_\\d+_|p_\\d+_");
    private static final Pattern CONSTRUCTOR = Pattern.compile(
            "^(?<indent> +|\\t+)(public |private|protected |)(?<generic><[\\w\\W]*>\\s+)?(?<name>[\\w.]+)\\((?<parameters>.*)\\)\\s+(?:throws[\\w.,\\s]+)?\\{");
    private static final Pattern METHOD = Pattern.compile(
            "^(?<indent> +|\\t+)(?!return)(?:\\w+\\s+)*(?<generic><[\\w\\W]*>\\s+)?(?<return>\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>(?:func_|m_)[0-9]+_[a-zA-Z_]*)\\(");
    private static final Pattern FIELD = Pattern.compile(
            "^(?<indent> +|\\t+)(?!return)(?:\\w+\\s+)*\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*\\s+(?<name>(?:field_|f_)[0-9]+_[a-zA-Z_]*) *[=;]");
    private static final Pattern CLASS = Pattern.compile(
            "^(?<indent> *|\\t*)([\\w|@]*\\s)*(class|interface|@interface|enum) (?<name>[\\w]+)");
    private static final Pattern CLOSING_BRACE = Pattern.compile("^(?<indent> *|\\t*)}");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s*(?<name>[\\w.]+);$");

    private SourceRenamer() { }

    /** Renames SRG ids in {@code lines} and inserts javadocs from {@code docs} (keyed by SRG id). */
    public static List<String> rename(List<String> lines, Map<String, String> names, Map<String, String> docs) {
        var out = new ArrayList<String>(lines.size() + 64);
        var innerClasses = new ArrayDeque<Map.Entry<String, Integer>>();
        var pkg = "";
        for (String line : lines) {
            Matcher pm = PACKAGE.matcher(line);
            if (pm.find()) {
                pkg = pm.group("name") + ".";
            }
            injectJavadoc(docs, out, line, pkg, innerClasses);
            out.add(replaceLine(names, line));
        }
        return out;
    }

    private static String replaceLine(Map<String, String> names, String line) {
        var matcher = SRG_TOKEN.matcher(line);
        var result = new StringBuilder(line.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(getMapped(names, matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Looks up an SRG id, honouring the capitalized {@code Func_}/{@code Field_} Mixin-accessor form. */
    private static String getMapped(Map<String, String> names, String srg) {
        boolean capitalized = srg.charAt(0) == 'F';
        var key = capitalized ? 'f' + srg.substring(1) : srg;
        var mapped = names.getOrDefault(key, key);
        if (capitalized && !mapped.isEmpty()) {
            mapped = Character.toUpperCase(mapped.charAt(0)) + mapped.substring(1);
        }
        return mapped;
    }

    private static void injectJavadoc(Map<String, String> docs, List<String> out, String line, String pkg,
                                      Deque<Map.Entry<String, Integer>> innerClasses) {
        var matcher = CONSTRUCTOR.matcher(line);
        boolean isConstructor = matcher.find() && !innerClasses.isEmpty()
                && innerClasses.peek().getKey().contains(matcher.group("name"));
        if (!isConstructor) {
            matcher = METHOD.matcher(line);
        }
        if (isConstructor || matcher.find()) {
            var name = isConstructor ? "<init>" : matcher.group("name");
            var javadoc = docs.get(name);
            if (javadoc == null && !innerClasses.isEmpty() && !name.startsWith("func_")) {
                javadoc = docs.get(innerClasses.peek().getKey() + '#' + name);
            }
            if (javadoc != null) {
                insertAboveAnnotations(out, buildJavadoc(matcher.group("indent"), javadoc));
            }
            return;
        }

        matcher = FIELD.matcher(line);
        if (matcher.find()) {
            var name = matcher.group("name");
            var javadoc = docs.get(name);
            if (javadoc == null && !innerClasses.isEmpty() && !name.startsWith("field_")) {
                javadoc = docs.get(innerClasses.peek().getKey() + '#' + name);
            }
            if (javadoc != null) {
                insertAboveAnnotations(out, buildJavadoc(matcher.group("indent"), javadoc));
            }
            return;
        }

        matcher = CLASS.matcher(line);
        if (matcher.find()) {
            var current = (innerClasses.isEmpty() ? pkg : innerClasses.peek().getKey() + "$") + matcher.group("name");
            innerClasses.push(Map.entry(current, matcher.group("indent").length()));
            var javadoc = docs.get(current);
            if (javadoc != null) {
                insertAboveAnnotations(out, buildJavadoc(matcher.group("indent"), javadoc));
            }
            return;
        }

        matcher = CLOSING_BRACE.matcher(line);
        if (matcher.find() && !innerClasses.isEmpty() && matcher.group("indent").length() == innerClasses.peek().getValue()) {
            innerClasses.pop();
        }
    }

    /** Builds a one-line javadoc, or a multi-line block when the desc carries {@code \\n} markers. */
    private static String buildJavadoc(String indent, String doc) {
        if (!doc.contains("\\n")) {
            return indent + "/** " + doc + " */";
        }
        var builder = new StringBuilder(indent).append("/**");
        for (String part : doc.split("\\\\n")) {
            builder.append('\n').append(indent).append(" * ").append(part);
        }
        return builder.append('\n').append(indent).append(" */").toString();
    }

    private static void insertAboveAnnotations(List<String> out, String doc) {
        int back = 0;
        while (back < out.size() && out.get(out.size() - 1 - back).trim().startsWith("@")) {
            back++;
        }
        out.add(out.size() - back, doc);
    }

}
