package com.cleanroommc.gradle.tasks;

import com.cleanroommc.gradle.CleanroomLogger;
import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersion;
import com.cleanroommc.gradle.util.json.deserialization.manifest.ManifestVersionsAdapter;
import com.cleanroommc.gradle.util.json.deserialization.mcversion.Version;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static com.cleanroommc.gradle.Constants.*;
import static com.cleanroommc.gradle.Constants.MINECRAFT_MANIFEST_ETAG;

public class DownloadTask extends DefaultTask {

    public static void setupDownloadVersionTask(Project project) {
        DownloadTask dlVersionTask = project.getTasks().create("DownloadVersion", DownloadTask.class);
        dlVersionTask.setUrl(Utils.closure(DownloadTask.class, () -> ManifestVersion.versions.get(MinecraftExtension.get(project).getVersion()).url));
        dlVersionTask.setOutputFile(Utils.closure(DownloadTask.class, () -> JSON_VERSION.apply(MinecraftExtension.get(project).getVersion())));
        dlVersionTask.doFirst(Utils.closure(DownloadTask.class, () -> {
            if (ManifestVersion.versions == null) {
                CleanroomLogger.log("Requesting Minecraft's Manifest...");
                ManifestVersion.versions = Utils.GSON.fromJson(Utils.getWithETag(project, MINECRAFT_MANIFEST_LINK, MINECRAFT_MANIFEST_FILE, MINECRAFT_MANIFEST_ETAG),
                        ManifestVersionsAdapter.TYPE);
            }
            return null;
        }));
        dlVersionTask.doLast(Utils.closure(DownloadTask.class, () -> {
            try {
                // Normalize line endings
                File json = dlVersionTask.getOutputFile();
                if (!json.exists()) {
                    return true;
                }
                List<String> lines = Files.readLines(json, Charsets.UTF_8);
                StringBuilder buf = new StringBuilder();
                for (String line : lines) {
                    buf.append(line).append('\n');
                }
                Files.write(buf.toString().getBytes(Charsets.UTF_8), json);
                // Initialize the AssetIndex if it isn't present
                Version.parseVersionAndStoreDeps(project, json, false, json.getParentFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }));
    }

    public static void setupDownloadAssetIndexTask(Project project) {
        DownloadTask dlAssetIndexTask = project.getTasks().create("DownloadAssetIndex", DownloadTask.class);
        dlAssetIndexTask.setUrl(Utils.closure(() -> Version.getCurrentVersion().assetIndex.url));
        dlAssetIndexTask.setOutputFile(Utils.closure(DownloadTask.class, () -> JSON_ASSET_INDEX.apply(MinecraftExtension.get(project).getVersion())));
    }

    @Input private Closure<String> url;
    @Input private boolean dieIfErrored;

    @OutputFile private Closure<File> outputFile;

    public DownloadTask() {
        this.getOutputs().upToDateWhen(FALSE_CLOSURE);
    }

    @TaskAction
    public void download() throws IOException {
        URL url = getUrl();
        File output = getOutputFile();
        File etagFile = getProject().file(output.getPath() + ".etag");

        output.getParentFile().mkdirs();

        String etag;
        if (etagFile.exists()) {
            etag = Files.asCharSource(etagFile, Charsets.UTF_8).read();
        } else {
            etag = "";
        }

        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setRequestProperty("If-None-Match", etag);

            con.connect();

            switch (con.getResponseCode()) {
                case 404: // File not found...
                    Utils.error(dieIfErrored, "" + url + "  404'd!");
                    break;
                case 304: // Content is the same
                    this.setDidWork(false);
                    break;
                case 200: // Worked
                    // Write to output file
                    try (InputStream stream = con.getInputStream()) {
                        Files.write(ByteStreams.toByteArray(stream), output);
                    }
                    // Write ETag
                    etag = con.getHeaderField("ETag");
                    if (!Strings.isNullOrEmpty(etag)) {
                        Files.write(etag, etagFile, Charsets.UTF_8);
                    }
                    break;
                default: // ???
                    Utils.error(dieIfErrored, "Unexpected reponse " + con.getResponseCode() + " from " + url);
                    break;
            }

            con.disconnect();
        } catch (Throwable e) {
            Utils.error(dieIfErrored, e.getLocalizedMessage());
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

}
