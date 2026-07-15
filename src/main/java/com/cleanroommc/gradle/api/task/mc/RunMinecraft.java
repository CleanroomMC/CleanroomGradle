package com.cleanroommc.gradle.api.task.mc;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.lazy.Providers;
import com.cleanroommc.gradle.api.task.LazilyConstructedJavaExec;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

@DisableCachingByDefault(because = "Launches the game")
public abstract class RunMinecraft extends LazilyConstructedJavaExec {

    private static boolean consoleInput() throws IOException {
        var scanner = new Scanner(System.in);
        var userInput = scanner.nextLine().trim();
        return userInput.startsWith("y") || userInput.startsWith("Y");
    }

    @Inject
    public abstract ProjectLayout getProjectLayout();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Side> getSide();

    @Input
    public abstract Property<Environment> getEnv();

    // A real input so the producing task (extractNatives) is inferred as a dependency
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getNatives();

    @Internal
    public abstract DirectoryProperty getVanillaAssetsLocation();

    @Input
    public abstract Property<String> getAssetIndexVersion();

    @Input
    @Optional
    public abstract Property<String> getUsername();

    @Input
    @Optional
    public abstract Property<String> getUUID();

    @Input
    @Optional
    public abstract Property<String> getAccessToken();

    private boolean setCustomWorkingDir = false;

    public RunMinecraft() {
        var ext = CleanroomExtension.get(this.getProject());
        var offline = this.getProject().getGradle().getStartParameter().isOffline();

        this.getMinecraftVersion().convention("1.12.2");
        this.getAssetIndexVersion().convention("1.12");
        this.getVanillaAssetsLocation().convention(ext.getCacheDirectory().dir("assets"));
        this.getAccessToken().convention("0");

        this.getUsername().convention("Developer");
        var uuidCache = ext.getCacheDirectory().file("uuid_cache.properties");
        this.getUUID().convention(getUsername()
                .map(u -> Objects.resolveUuid(offline, uuidCache.get().getAsFile(), u))
                .map(UUID::toString));

        this.getMainClass().convention(this.getSide().map(side -> side.isClient() ? "net.minecraft.client.main.Main" : "net.minecraft.server.MinecraftServer"));

        this.setStandardInput(System.in);
        this.setStandardOutput(System.out);
        this.setErrorOutput(System.err);

        this.jvmArgs("-Dfile.encoding=UTF-8");

        this.systemProperty("java.library.path", Providers.libraryPath(this.getProject(), this.getNatives().map(Directory::getAsFile)));

        this.args("--gameDir", this.getProject().provider(this::getWorkingDir),
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
            super.setWorkingDir(IO.runDir(this.getProjectLayout().getProjectDirectory().getAsFile(), this.getMinecraftVersion().get(), this.getEnv().get(), this.getSide().get()));
        }

        // Thanks to RetroFuturaGradle for this QoL
        if (this.getSide().get().isServer()) {
            this.args("nogui");

            var serverProperties = new File(this.getWorkingDir(), "server.properties");
            if (!serverProperties.exists()) {
                logger.warn("Start the server in offline mode? If yes, type 'y': ");
                try {
                    FileUtils.write(serverProperties, "online-mode=" + !consoleInput(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.error("Issue writing online mode to file, it will be set to true at runtime by default.", e);
                }
            }
            var eula = new File(this.getWorkingDir(), "eula.txt");
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
