package com.cleanroommc.gradle.api.util;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;

import java.util.function.Consumer;

/**
 * Structured problem identities and reporting helpers owned by CleanroomGradle.
 */
public final class CleanroomProblems {

    public static final ProblemGroup GROUP = ProblemGroup.create("cleanroom-gradle", "CleanroomGradle");

    public static final ProblemId MISSING_USERDEV = id("missing-userdev", "Missing Cleanroom userdev artifact");
    public static final ProblemId INVALID_VANILLA_ENVIRONMENT = id("invalid-vanilla-environment", "Invalid vanilla environment");
    public static final ProblemId OFFLINE_ASSETS = id("offline-assets", "Minecraft assets unavailable offline");
    public static final ProblemId USERDEV_PIPELINE_OVERRIDE = id("userdev-pipeline-override", "Userdev pipeline input set by the buildscript");
    public static final ProblemId DEOBF_ON_COMPILE_CLASSPATH = id("deobf-on-compile-classpath", "Deobfuscated dependency on a compile classpath");

    public static RuntimeException throwing(Problems problems, Throwable failure, ProblemId id, String details, String solution) {
        return throwing(problems, failure, id, spec -> spec.details(details).solution(solution));
    }

    public static RuntimeException throwing(Problems problems, Throwable failure, ProblemId id, Consumer<ProblemSpec> configure) {
        return problems.getReporter().throwing(failure, id, spec -> {
            spec.severity(Severity.ERROR).stackLocation();
            configure.accept(spec);
        });
    }

    private static ProblemId id(String name, String displayName) {
        return ProblemId.create(name, displayName, GROUP);
    }

    private CleanroomProblems() { }

}
