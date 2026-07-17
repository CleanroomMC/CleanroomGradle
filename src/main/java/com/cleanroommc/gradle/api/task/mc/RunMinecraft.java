package com.cleanroommc.gradle.api.task.mc;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.Environment;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.LaunchArguments;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.Platform;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.function.Supplier;

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

    @Internal
    public abstract Property<VersionMeta> getVersionMeta();

    @Input
    public abstract Property<String> getLauncherVersion();

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

        this.getVersionMeta().convention(ext.getVersionMeta());
        var pluginVersion = String.valueOf(this.getProject().getVersion());
        this.getLauncherVersion().convention("unspecified".equals(pluginVersion) ? "dev" : pluginVersion);

        this.getMainClass().convention(this.getSide().map(side -> side.isClient() ? "net.minecraft.client.main.Main" : "net.minecraft.server.MinecraftServer"));

        this.setStandardInput(System.in);
        this.setStandardOutput(System.out);
        this.setErrorOutput(System.err);

        this.jvmArgs("-Dfile.encoding=UTF-8");

        this.systemProperty("java.library.path", Providers.libraryPath(this.getProject(), this.getNatives().map(Directory::getAsFile)));

        this.setMinHeapSize("1G");
        this.setMaxHeapSize("1G");
    }

    @Override
    protected void beforeExec() {
        var logger = this.getLogger();
        var side = this.getSide().get();

        if (!this.setCustomWorkingDir) {
            super.setWorkingDir(IO.runDir(this.getProjectLayout().getProjectDirectory().getAsFile(), this.getMinecraftVersion().get(), this.getEnv().get(), side));
        }

        var consumerArgs = new ArrayList<>(this.getArgs());
        this.setArgs(new ArrayList<>());
        this.generateArguments(side);
        if (!consumerArgs.isEmpty()) {
            this.args(consumerArgs);
        }

        // Thanks to RetroFuturaGradle for this QoL
        if (side.isServer()) {
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

    private void generateArguments(Side side) {
        if (side.isClient() && this.getEnv().get() == Environment.VANILLA) {
            var meta = this.getVersionMeta().get();
            var launch = new LaunchArguments(meta, buildSubstitutions(meta), Platform.CURRENT, this.getLogger()::warn);
            if (launch.hasGameArguments()) {
                this.args(launch.gameArguments().toArray());
                var jvm = launch.jvmArguments();
                if (!jvm.isEmpty()) {
                    this.jvmArgs(jvm);
                }
                return;
            }
            this.getLogger().warn("Version meta for {} declares neither 'arguments' nor 'minecraftArguments'; using legacy launch arguments.", this.getMinecraftVersion().get());
        }
        appendLegacyArguments();
    }

    /** The pre-1.13 hardcoded argument list, resolved from the same properties as before (lazily, at exec time). */
    private void appendLegacyArguments() {
        this.args("--gameDir", (Supplier<File>) this::getWorkingDir,
                "--version", this.getMinecraftVersion(),
                "--assetIndex", this.getAssetIndexVersion(),
                "--assetsDir", this.getVanillaAssetsLocation(),
                "--username", this.getUsername(),
                "--uuid", this.getUUID(),
                "--accessToken", this.getAccessToken()
        );
    }

    private Map<String, String> buildSubstitutions(VersionMeta meta) {
        var substitutions = new LinkedHashMap<String, String>();
        substitutions.put("auth_player_name", getUsername().get());
        substitutions.put("version_name", getMinecraftVersion().get());
        substitutions.put("game_directory", getWorkingDir().getAbsolutePath());
        substitutions.put("assets_root", getVanillaAssetsLocation().get().getAsFile().getAbsolutePath());
        substitutions.put("assets_index_name", getAssetIndexVersion().get());
        substitutions.put("auth_uuid", getUUID().get());
        substitutions.put("auth_access_token", getAccessToken().get());
        substitutions.put("user_type", "legacy");
        substitutions.put("version_type", meta.type() != null ? meta.type() : "release");
        substitutions.put("natives_directory", getNatives().get().getAsFile().getAbsolutePath());
        substitutions.put("clientid", "0");
        substitutions.put("auth_xuid", "0");
        substitutions.put("user_properties", "{}");
        substitutions.put("launcher_name", "cleanroomgradle");
        substitutions.put("launcher_version", getLauncherVersion().get());
        return substitutions;
    }

}
