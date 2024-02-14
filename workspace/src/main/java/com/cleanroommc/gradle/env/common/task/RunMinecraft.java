package com.cleanroommc.gradle.env.common.task;

import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.task.type.LazilyConstructedJavaExec;
import com.cleanroommc.gradle.api.types.Types;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import java.util.UUID;

@DisableCachingByDefault
public abstract class RunMinecraft extends LazilyConstructedJavaExec {

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Side> getSide();

    @InputDirectory
    public abstract DirectoryProperty getNatives();

    @Input
    public abstract Property<String> getAssetIndexVersion();

    @InputDirectory
    public abstract DirectoryProperty getVanillaAssetsLocation();

    @Input
    @Optional
    public abstract Property<String> getUsername();

    @Input
    @Optional
    public abstract Property<String> getUUID();

    @Input
    @Optional
    public abstract Property<String> getAccessToken();

    public RunMinecraft() {
        getAssetIndexVersion().convention(getMinecraftVersion());
        getVanillaAssetsLocation().convention(getProject().getLayout().getProjectDirectory());
        getAccessToken().convention("0");

        getUsername().convention("Developer");
        getUUID().convention(getUsername().map(u -> Types.resolveUuid(getProject(), u)).map(UUID::toString));

        setStandardInput(System.in);
        setStandardOutput(System.out);
        setErrorOutput(System.err);

        systemProperty("java.library.path", Providers.libraryPath(getProject(), getNatives().map(Directory::getAsFile)));

        args("--gameDir", Providers.of(this::getWorkingDir),
             "--version", getMinecraftVersion(),
             "--assetIndex", getAssetIndexVersion(),
             "--assetsDir", getVanillaAssetsLocation(),
             "--username", getUsername(),
             "--uuid", getUUID(),
             "--accessToken", getAccessToken()
        );

        setMinHeapSize("1G");
        setMaxHeapSize("1G");
    }

}
