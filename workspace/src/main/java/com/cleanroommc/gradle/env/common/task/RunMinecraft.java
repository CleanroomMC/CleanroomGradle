package com.cleanroommc.gradle.env.common.task;

import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.task.type.LazilyConstructedJavaExec;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.api.types.Types;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

@DisableCachingByDefault
public abstract class RunMinecraft extends LazilyConstructedJavaExec {

    private static boolean consoleInput() throws IOException {
        var scanner = new Scanner(System.in);
        var userInput = scanner.nextLine().trim();
        return userInput.startsWith("y") || userInput.startsWith("Y");
    }

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Side> getSide();

    @InputFiles
    public abstract RegularFileProperty getNatives();

    @Input
    public abstract Property<String> getAssetIndexVersion();

    @InputFiles
    public abstract RegularFileProperty getVanillaAssetsLocation();

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
        this.getAssetIndexVersion().convention(this.getMinecraftVersion());
        this.getVanillaAssetsLocation().convention(this.getProject().getLayout().getProjectDirectory().file("."));
        this.getAccessToken().convention("0");

        this.getUsername().convention("Developer");
        this.getUUID().convention(getUsername().map(u -> Types.resolveUuid(this.getProject(), u)).map(UUID::toString));

        this.setStandardInput(System.in);
        this.setStandardOutput(System.out);
        this.setErrorOutput(System.err);

        this.jvmArgs("-Dfile.encoding=UTF-8");

        this.systemProperty("java.library.path", Providers.libraryPath(getProject(), this.getNatives().map(RegularFile::getAsFile)));

        this.args("--gameDir", Providers.of(this::getWorkingDir),
                "--version", getMinecraftVersion(),
                "--assetIndex", getAssetIndexVersion(),
                "--assetsDir", getVanillaAssetsLocation(),
                "--username", getUsername(),
                "--uuid", getUUID(),
                "--accessToken", getAccessToken()
        );

        this.setMinHeapSize("1G");
        this.setMaxHeapSize("1G");
    }

    /**
     * Thanks to RetroFuturaGradle for this QoL idea
     */
    @Override
    protected void beforeExec() {
        var logger = this.getLogger();
        if (getSide().get().isServer()) {
            this.args("nogui");

            var serverProperties = Locations.file(getWorkingDir(), "server.properties");
            if (!serverProperties.exists()) {
                logger.warn("Start the server in offline mode? If yes, type 'y': ");
                try {
                    FileUtils.write(serverProperties, "online-mode=" + !consoleInput(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.error("Issue writing online mode to file, it will be set to true at runtime by default.", e);
                }
            }
            var eula = Locations.file(getWorkingDir(), "eula.txt");
            if (!eula.exists()) {
                logger.warn("Do you accept the Minecraft EULA (https://www.minecraft.net/en-us/eula)? If yes, type 'y': ");
                try {
                    FileUtils.write(eula, "eula=" + consoleInput(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.error("Issue accepting EULA!", e);
                }
            }
        }
    }

}
