package com.cleanroommc.gradle.api.userdev;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTransform
public abstract class ExtractUserdevExtra implements TransformAction<ExtractUserdevExtra.Parameters> {

    public interface Parameters extends TransformParameters {
        @Input
        Property<String> getSide();
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var config = UserdevConfig.readFromJar(input);
        var prefix = (getParameters().getSide().get().equals("client")
                ? config.layout().clientExtra() : config.layout().serverExtra()) + "/";
        UserdevArchive.select(input, outputs.file(getParameters().getSide().get() + "-extra.jar"),
                name -> name.startsWith(prefix), prefix);
    }

}
