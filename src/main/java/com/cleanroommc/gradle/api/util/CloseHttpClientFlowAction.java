package com.cleanroommc.gradle.api.util;

import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;

public abstract class CloseHttpClientFlowAction implements FlowAction<FlowParameters.None> {

    @Override
    public void execute(FlowParameters.None parameters) {
        IO.closeHttpClient();
    }

}
