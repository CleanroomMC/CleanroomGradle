package com.cleanroommc.gradle.newapi.task.mc;

import com.cleanroommc.gradle.newapi.ext.CleanroomExtension;
import com.cleanroommc.gradle.newapi.util.Environment;
import com.cleanroommc.gradle.newapi.util.IO;
import com.cleanroommc.gradle.newapi.util.Objects;
import com.cleanroommc.gradle.newapi.util.lazy.Providers;
import com.cleanroommc.gradle.newapi.task.LazilyConstructedJavaExec;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
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

    @Input
    public abstract Property<Environment> getEnv();

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

    @Internal
    private boolean setCustomWorkingDir = false;

    public RunMinecraft() {
        this.getMinecraftVersion().convention("1.12.2");
        this.getAssetIndexVersion().convention("1.12");
        this.getVanillaAssetsLocation().convention(CleanroomExtension.get(this.getProject()).getCacheDirectory().file("assets"));
        this.getAccessToken().convention("0");

        this.getUsername().convention("Developer");
        this.getUUID().convention(getUsername()
                .map(u -> Objects.resolveUuid(this.getProject(), CleanroomExtension.get(this.getProject()), u))
                .map(UUID::toString));

        this.getMainClass().convention(this.getSide().map(side -> side.isClient() ? "net.minecraft.client.main.Main" : "net.minecraft.server.MinecraftServer"));

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

    @Override
    protected void beforeExec() {
        var logger = this.getLogger();

        if (!this.setCustomWorkingDir) {
            super.setWorkingDir(IO.runDir(this.getProject(), this.getMinecraftVersion().get(), this.getEnv().get(), this.getSide().get()));
        }

        // Thanks to RetroFuturaGradle for this QoL
        if (this.getSide().get().isServer()) {
            this.args("nogui");

            var serverProperties = new File(getWorkingDir(), "server.properties");
            if (!serverProperties.exists()) {
                logger.warn("Start the server in offline mode? If yes, type 'y': ");
                try {
                    FileUtils.write(serverProperties, "online-mode=" + !consoleInput(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.error("Issue writing online mode to file, it will be set to true at runtime by default.", e);
                }
            }
            var eula = new File(getWorkingDir(), "eula.txt");
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

    @Override
    public void setWorkingDir(File dir) {
        this.setCustomWorkingDir = true;
        super.setWorkingDir(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        this.setCustomWorkingDir = true;
        super.setWorkingDir(dir);
    }

    @Override
    public JavaExec workingDir(Object dir) {
        this.setCustomWorkingDir = true;
        return super.workingDir(dir);
    }

}
