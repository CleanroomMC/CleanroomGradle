package com.cleanroommc.gradle.tasks.download;

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

public class ETaggedDownloadTask extends DefaultTask implements IDownloadTask {

    public static void setupDownloadVersionTask(Project project) {
        ETaggedDownloadTask dlVersionTask = Utils.createTask(project, DL_MINECRAFT_VERSIONS_TASK, ETaggedDownloadTask.class);
        dlVersionTask.setUrl(Utils.closure(ETaggedDownloadTask.class, () -> ManifestVersion.versions.get(MinecraftExtension.get(project).getVersion()).url));
        dlVersionTask.setOutputFile(Utils.closure(ETaggedDownloadTask.class, () -> VERSION_FILE.apply(MinecraftExtension.get(project).getVersion())));
        dlVersionTask.doFirst(Utils.closure(ETaggedDownloadTask.class, () -> {
            if (ManifestVersion.versions == null) {
                CleanroomLogger.log("Requesting Minecraft's Manifest...");
                // ManifestVersion.versions = Utils.GSON.fromJson(Utils.getWithETag(project, MINECRAFT_MANIFEST_LINK, MINECRAFT_MANIFEST_FILE, MINECRAFT_MANIFEST_ETAG),
                        // ManifestVersionsAdapter.TYPE);
            }
            return null;
        }));
        dlVersionTask.doLast(Utils.closure(ETaggedDownloadTask.class, () -> {
            try {
                // Normalize line endings
                File json = dlVersionTask.getOutputFile();
                if (!json.exists()) {
                    return true;
                }
                List<String> lines = Files.readLines(json, CHARSET);
                StringBuilder buf = new StringBuilder();
                for (String line : lines) {
                    buf.append(line).append('\n');
                }
                Files.write(buf.toString().getBytes(CHARSET), json);
                // Initialize the AssetIndex if it isn't present
                Version.parseVersionAndStoreDeps(project, json, false, json.getParentFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }));
    }

    public static void setupDownloadAssetIndexTask(Project project) {
        ETaggedDownloadTask dlAssetIndexTask = Utils.createTask(project, DL_MINECRAFT_ASSET_INDEX_TASK, ETaggedDownloadTask.class);
        dlAssetIndexTask.setUrl(Utils.closure(() -> Version.getCurrentVersion().assetIndex.url));
        dlAssetIndexTask.setOutputFile(Utils.closure(ETaggedDownloadTask.class, () -> ASSET_INDEX_FILE.apply(MinecraftExtension.get(project).getVersion())));
    }

    @Input private Closure<String> url;
    @Input private boolean dieIfErrored;

    @OutputFile private Closure<File> outputFile;

    public ETaggedDownloadTask() {
        this.getOutputs().upToDateWhen(FALSE_CLOSURE);
    }

    @Override
    @TaskAction
    public void downloadAndGet() throws IOException {
        URL url = getUrl();
        File output = getOutputFile();
        output.getParentFile().mkdirs();
        File etagFile = new File(output.getParentFile(), output.getName() + ".etag");

        String etag;
        if (etagFile.exists()) {
            etag = Files.asCharSource(etagFile, CHARSET).read();
        } else {
            etag = "";
        }

        CleanroomLogger.log("Downloading from {} to {}", url, output);

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
                        Files.write(etag, etagFile, CHARSET);
                    }
                    break;
                default: // ???
                    Utils.error(dieIfErrored, "Unexpected reponse " + con.getResponseCode() + " from " + url);
                    break;
            }

            con.disconnect();
        } catch (Throwable t) {
            Utils.error(dieIfErrored, t.getLocalizedMessage());
        }
    }

    @Override
    public URL getUrl() throws MalformedURLException {
        return new URL(url.call());
    }

    @Override
    public void setUrl(Closure<String> url) {
        this.url = url;
    }

    @Override
    public File getOutputFile() {
        return outputFile.call();
    }

    @Override
    public void setOutputFile(Closure<File> outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public void setToDieWhenError() {
        this.dieIfErrored = true;
    }

    @Override
    public void checkAgainst(Closure<String> hash, String hashFunc, Closure<Long> size) {
        // NO-OP
    }

}
