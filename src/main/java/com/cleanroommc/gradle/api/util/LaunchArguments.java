package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.schema.VersionMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the Minecraft launch arguments for a {@link VersionMeta}, independent of Gradle so it can be
 * unit tested. Handles both the modern (1.13+) {@code arguments {game, jvm}} block and the legacy
 * ({@code minecraftArguments}) template string, evaluating os/feature rules and substituting the standard
 * {@code ${placeholder}} tokens.
 *
 * <p>Rendering rules:</p>
 * <ul>
 *     <li>Plain (rule-less) arguments are always kept.</li>
 *     <li>Rule-gated arguments are kept only when the rules resolve to {@code allow} for the current
 *     {@link Platform}; os rules match by name, and — since this launcher enables no launcher features —
 *     any {@code features} rule is unmet, so feature-gated arguments drop.</li>
 *     <li>JVM arguments additionally skip the classpath pair ({@code -cp}/{@code ${classpath}}, supplied by
 *     JavaExec) and any {@code -Djava.library.path=} entry (the run task sets that system property itself).
 *     The {@code -Dminecraft.launcher.brand/version} entries are kept and substituted.</li>
 *     <li>An unknown placeholder in a kept argument becomes an empty string and warns once.</li>
 * </ul>
 */
public final class LaunchArguments {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(?<name>[^}]+)}");

    private final VersionMeta meta;
    private final Map<String, String> substitutions;
    private final Platform platform;
    private final Consumer<String> warn;
    private final Set<String> warnedPlaceholders = new HashSet<>();

    public LaunchArguments(VersionMeta meta, Map<String, String> substitutions, Platform platform, Consumer<String> warn) {
        this.meta = meta;
        this.substitutions = substitutions;
        this.platform = platform;
        this.warn = warn;
    }

    /** Whether the meta carries game arguments in either the modern or legacy form. */
    public boolean hasGameArguments() {
        return hasModernArguments() || meta.minecraftArguments() != null;
    }

    private boolean hasModernArguments() {
        return meta.arguments() != null && meta.arguments().game() != null;
    }

    /** The rendered game (program) arguments; empty when the meta declares none. */
    public List<String> gameArguments() {
        if (hasModernArguments()) {
            return render(meta.arguments().game(), false);
        }
        String template = meta.minecraftArguments();
        if (template == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : template.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(substitute(token));
            }
        }
        return result;
    }

    /** The rendered extra JVM arguments; empty for legacy metas (they carry no {@code jvm} block). */
    public List<String> jvmArguments() {
        if (meta.arguments() == null || meta.arguments().jvm() == null) {
            return List.of();
        }
        return render(meta.arguments().jvm(), true);
    }

    private List<String> render(List<VersionMeta.Argument> arguments, boolean jvm) {
        List<String> result = new ArrayList<>();
        for (VersionMeta.Argument argument : arguments) {
            if (!isAllowed(argument)) {
                continue;
            }
            for (String raw : argument.values()) {
                if (jvm && shouldSkipJvmArgument(raw)) {
                    continue;
                }
                result.add(substitute(raw));
            }
        }
        return result;
    }

    private boolean isAllowed(VersionMeta.Argument argument) {
        List<VersionMeta.ArgRule> rules = argument.rules();
        if (rules == null) {
            return true;
        }
        boolean allowed = false;
        for (VersionMeta.ArgRule rule : rules) {
            if (rule.matches(platform)) {
                allowed = rule.isAllowed();
            }
        }
        return allowed;
    }

    private static boolean shouldSkipJvmArgument(String raw) {
        return raw.equals("-cp") || raw.equals("-classpath") || raw.equals("--class-path")
                || raw.contains("${classpath}")
                || raw.startsWith("-Djava.library.path=");
    }

    private String substitute(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group("name");
            String replacement = substitutions.get(name);
            if (replacement == null) {
                if (warnedPlaceholders.add(name)) {
                    warn.accept("No substitution for launch argument placeholder ${" + name + "}; using an empty string.");
                }
                replacement = "";
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

}
