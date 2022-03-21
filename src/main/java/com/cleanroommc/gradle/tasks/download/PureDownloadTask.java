package com.cleanroommc.gradle.tasks.download;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.Version;
import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import static com.cleanroommc.gradle.Constants.*;

public class PureDownloadTask extends DefaultTask implements IDownloadTask {

    public static void setupDownloadClientTask(Project project) {
        PureDownloadTask downloadClientTask = project.getTasks().create(DL_MINECRAFT_CLIENT_TASK, PureDownloadTask.class);
        downloadClientTask.setOutputFile(Utils.closure(() -> MINECRAFT_CLIENT_FILE.apply(MinecraftExtension.get(project).getVersion())));
        downloadClientTask.setUrl(Utils.closure(() -> Version.getCurrentVersion().getClientUrl()));
        downloadClientTask.checkAgainst(Utils.closure(() -> Version.getCurrentVersion().getClientHash()), "SHA1");
        downloadClientTask.dependsOn(project.getTasks().getByPath(DL_MINECRAFT_VERSIONS_TASK));
    }

    public static void setupDownloadServerTask(Project project) {
        PureDownloadTask downloadServerTask = project.getTasks().create(DL_MINECRAFT_SERVER_TASK, PureDownloadTask.class);
        downloadServerTask.setOutputFile(Utils.closure(() -> MINECRAFT_SERVER_FILE.apply(MinecraftExtension.get(project).getVersion())));
        downloadServerTask.setUrl(Utils.closure(() -> Version.getCurrentVersion().getServerUrl()));
        downloadServerTask.checkAgainst(Utils.closure(() -> Version.getCurrentVersion().getServerHash()), "SHA1");
        downloadServerTask.dependsOn(project.getTasks().getByPath(DL_MINECRAFT_VERSIONS_TASK));
    }

    @Input private Closure<String> url;
    @Input private boolean dieIfErrored;
    @Input @Nullable private Closure<String> hash;
    @Input private String hashFunc;

    @OutputFile
    private Closure<File> outputFile;

    @TaskAction
    public void download() throws IOException {
        File outputFile = getOutputFile();
        if (hash != null && outputFile.exists()) {
            if (hash.call().equals(Utils.hash(outputFile, hashFunc))) {
                CleanroomLogger.log("{} already exists and download will be skipped.", outputFile.getName());
                return;
            }
        }
        outputFile.getParentFile().mkdirs();
        outputFile.createNewFile();

        URL url = getUrl();
        CleanroomLogger.log("Downloading from {} to {}", url, outputFile);

        HttpURLConnection connect = (HttpURLConnection) url.openConnection();
        connect.setRequestProperty("User-Agent", USER_AGENT);
        connect.setInstanceFollowRedirects(true);

        try (ReadableByteChannel inChannel = Channels.newChannel(connect.getInputStream());
             FileOutputStream outFile = new FileOutputStream(outputFile);
             FileChannel outChannel = outFile.getChannel()) {
            // If length is longer than what is available, it copies what is available according to java docs
            // Therefore, use Long.MAX_VALUE which is a theoretical maximum
            outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
            CleanroomLogger.log("Download complete.");
        } catch (Throwable t) {
            Utils.error(dieIfErrored, t.getLocalizedMessage());
        }
    }

    public URL getUrl() throws MalformedURLException {
        return new URL(url.call());
    }

    public void setUrl(Closure<String> url) {
        this.url = url;
    }

    public File getOutputFile() {
        return outputFile.call();
    }

    public void setOutputFile(Closure<File> outputFile) {
        this.outputFile = outputFile;
    }

    public void setToDieWhenError() {
        this.dieIfErrored = true;
    }

    @Override
    public void checkAgainst(Closure<String> hash, String hashFunc) {
        this.hash = hash;
        this.hashFunc = hashFunc;
    }

}
