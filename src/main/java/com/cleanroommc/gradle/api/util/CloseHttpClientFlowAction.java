/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;

public abstract class CloseHttpClientFlowAction implements FlowAction<FlowParameters.None> {

    @Override
    public void execute(FlowParameters.None parameters) {
        IO.closeHttpClient();
    }

}
