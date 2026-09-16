/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.deobf;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

import javax.inject.Inject;

@CacheableRule
public abstract class ClassifiedDeobfRule implements ComponentMetadataRule {

    private final String artifactName;
    private final String classifier;
    private final String capability;

    @Inject
    public ClassifiedDeobfRule(String artifactName, String classifier, String capability) {
        this.artifactName = artifactName;
        this.classifier = classifier;
        this.capability = capability;
    }

    @Override
    public void execute(ComponentMetadataContext context) {
        var details = context.getDetails();
        var id = details.getId();
        // POMs and Java module metadata use different names for the same compile/runtime variants.
        for (var base : new String[] { "compile", "runtime", "apiElements", "runtimeElements" }) {
            details.maybeAddVariant(this.capability + "-" + base, base, variant -> {
                variant.withCapabilities(capabilities -> {
                    capabilities.removeCapability(id.getGroup(), id.getName());
                    capabilities.addCapability(id.getGroup(), this.capability, id.getVersion());
                });
                variant.withFiles(files -> {
                    files.removeAllFiles();
                    files.addFile(this.artifactName + "-" + id.getVersion() + "-" + this.classifier + ".jar");
                });
            });
        }
    }

}
