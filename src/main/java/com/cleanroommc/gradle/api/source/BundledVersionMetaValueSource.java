package com.cleanroommc.gradle.api.source;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.IO;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class BundledVersionMetaValueSource implements ValueSource<VersionMeta, ValueSourceParameters.None> {

    @Override
    public VersionMeta obtain() {
        var stream = BundledVersionMetaValueSource.class.getResourceAsStream("/meta/1.12.2.json");
        if (stream == null) {
            throw new RuntimeException("Bundled 1.12.2 version meta not found in plugin resources");
        }
        return IO.readJson(stream, VersionMeta.class);
    }

}
