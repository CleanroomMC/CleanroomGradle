package com.cleanroommc.gradle.api.source;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.IO;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class BundledVersionMetaValueSource implements ValueSource<VersionMeta, ValueSourceParameters.None> {

    @Override
    public VersionMeta obtain() {
        var version = Meta.ONE_TRUE_MINECRAFT_VERSION;
        var stream = BundledVersionMetaValueSource.class.getResourceAsStream("/meta/" + version + ".json");
        if (stream == null) {
            throw new RuntimeException("Bundled " + version + " version meta not found in plugin resources");
        }
        return IO.readJson(stream, VersionMeta.class);
    }

}
