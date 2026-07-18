package com.cleanroommc.gradle.api;

import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.util.Set;
import java.util.regex.Pattern;

public final class Versioning {

    private static final Set<String> VALID_STAGES = Set.of("alpha", "beta", "rc", "release");
    private static final Pattern GIT_DESCRIBE = Pattern.compile("^(.+)-(\\d+)-g([0-9a-f]+)$");

    public static void apply(Project project) {
        var providers = project.getProviders();
        var stage = providers.gradleProperty("version_stage").get();
        if (!VALID_STAGES.contains(stage)) {
            throw new GradleException("version_stage must be one of: " + VALID_STAGES + " (got '" + stage + "')");
        }
        var numericVersion = project.getVersion().toString();
        var baseVersion = "release".equals(stage) ? numericVersion : numericVersion + "-" + stage;
        var gitInfo = providers.exec(spec -> spec.commandLine("git", "describe", "--tags", "--long"))
                .getStandardOutput()
                .getAsText()
                .map(raw -> {
                    var m = GIT_DESCRIBE.matcher(raw.trim());
                    if (!m.matches()) {
                        return new GitInfo("", 0);
                    }
                    return new GitInfo(m.group(1), Integer.parseInt(m.group(2)));
                });
        var release = providers.gradleProperty("release");
        var runNumber = providers.gradleProperty("run_number");
        if (release.isPresent()) {
            var info = gitInfo.get();
            if (!info.tag().equals(baseVersion)) {
                throw new GradleException("Git tag '" + info.tag() + "' does not match gradle.properties version '" + baseVersion + "'");
            }
            if (info.distance() != 0) {
                throw new GradleException("Release must be on a tagged commit (" + info.distance() + " commits ahead of '" + info.tag() + "')");
            }
            project.setVersion(baseVersion);
        } else if (runNumber.isPresent()) {
            project.setVersion(baseVersion + "+build." + gitInfo.get().distance() + ".run." + runNumber.get());
        } else {
            project.setVersion(baseVersion + "+local." + gitInfo.get().distance());
        }
    }

    private record GitInfo(String tag, int distance) { }

    private Versioning() {}

}
