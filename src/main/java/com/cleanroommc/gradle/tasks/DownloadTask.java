package com.cleanroommc.gradle.tasks;

import com.cleanroommc.gradle.util.Utils;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.cleanroommc.gradle.Constants.FALSE_CLOSURE;
import static com.cleanroommc.gradle.Constants.USER_AGENT;

public class DownloadTask extends DefaultTask {

    @Input private URL url;
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

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
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
