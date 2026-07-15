package com.cleanroommc.gradle.api.source;

import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.IO;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class VersionMetaValueSource implements ValueSource<VersionMeta, VersionMetaValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        RegularFileProperty getCacheFile();

        Property<String> getVersionMetaUrl();

        Property<Boolean> getOffline();

    }

    @Override
    public VersionMeta obtain() {
        var dest = this.getParameters().getCacheFile().get().getAsFile();
        if (this.getParameters().getOffline().getOrElse(false)) {
            if (!dest.isFile()) {
                throw new IllegalStateException("Gradle is offline and no cached version metadata exists at %s. Network access".formatted(dest));
            }
        } else {
            IO.downloadWithETag(getParameters().getVersionMetaUrl().get(), dest);
        }
        return IO.readJson(dest, VersionMeta.class);
    }

}
