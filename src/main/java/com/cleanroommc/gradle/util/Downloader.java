package com.cleanroommc.gradle.util;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;
import java.util.function.Consumer;

import static com.cleanroommc.gradle.Constants.CACHE_TIMEOUT;
import static com.cleanroommc.gradle.Constants.CHARSET;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

public final class Downloader {

    private Downloader() { }

    public static boolean downloadEtaggedFile(URL url, File output, boolean offline) throws IOException {
        if (output.exists() && (offline || output.lastModified() > System.currentTimeMillis() - CACHE_TIMEOUT)) {
            return true;
        }
        File etagFile = new File(output.getAbsolutePath(), ".etag");
        String etagString = "";
        if (etagFile.exists()) {
            etagString = new String(Files.readAllBytes(etagFile.toPath()), CHARSET);
        }
        final String initialETagString = etagString;
        HttpURLConnection httpURLConnection = connectHttpWithRedirects(url, (setupCon) -> {
            if (output.exists()) {
                setupCon.setIfModifiedSince(output.lastModified());
            }
            if (!initialETagString.isEmpty()) {
                setupCon.setRequestProperty("If-None-Match", initialETagString);
            }
        });
        if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            output.setLastModified(new Date().getTime());
            return true;
        } else if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try {
                InputStream stream = httpURLConnection.getInputStream();
                int len = httpURLConnection.getContentLength();
                int read;
                output.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(output)) {
                    read = IOUtils.copy(stream, out);
                }
                if (read != len) {
                    output.delete();
                    throw new IOException("Failed to read all of data from " + url + " got " + read + " expected " + len);
                }
                etagString = httpURLConnection.getHeaderField("ETag");
                if (etagString == null || etagString.isEmpty()) {
                    Files.write(etagFile.toPath(), new byte[0]);
                } else {
                    Files.write(etagFile.toPath(), etagString.getBytes(CHARSET));
                }
                return true;
            } catch (IOException e) {
                output.delete();
                throw e;
            }
        }
        return false;
    }

    public static boolean downloadFile(URL url, File output, boolean deleteOn404) {
        try {
            URLConnection urlConnection = openConnection(url);
            if (urlConnection instanceof HttpURLConnection) {
                HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
                switch (httpURLConnection.getResponseCode()) {
                    case HTTP_OK:
                        return downloadFile(urlConnection, output);
                    case HTTP_NOT_FOUND:
                        if (deleteOn404 && output.exists()) {
                            output.delete();
                        }
                    default:
                        return false;
                }
            }
            return downloadFile(urlConnection, output);
        } catch (FileNotFoundException e) {
            if (deleteOn404 && output.exists()) {
                output.delete();
            }
        } catch (IOException e) {
            // Invalid URLs/File paths will cause FileNotFound or 404 errors.
            // As well as any errors during download.
            // So delete the output if it exists as it's invalid, and return false
            if (output.exists()) {
                output.delete();
            }
        }
        return false;
    }

    @Nullable
    public static String downloadString(URL url) throws IOException {
        URLConnection urlConnection = openConnection(url);
        if (urlConnection instanceof HttpURLConnection) {
            if (((HttpURLConnection) urlConnection).getResponseCode() == HTTP_OK) {
                return downloadString(urlConnection);
            }
        }
        return downloadString(urlConnection);
    }

    public static URLConnection openConnection(String url) throws IOException {
        return openConnection(new URL(url));
    }

    public static URLConnection openConnection(URL url) throws IOException {
        String proto = url.getProtocol().toLowerCase();
        if ("http".equals(proto) || "https".equals(proto)) {
            HttpURLConnection con = connectHttpWithRedirects(url);
            return connectHttpWithRedirects(url);
        } else {
            URLConnection con = url.openConnection();
            con.connect();
            return con;
        }
    }

    public static HttpURLConnection connectHttpWithRedirects(URL url) throws IOException {
        return connectHttpWithRedirects(url, null);
    }

    public static HttpURLConnection connectHttpWithRedirects(URL url, @Nullable Consumer<HttpURLConnection> setup) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        if (setup != null) {
            setup.accept(con);
        }
        con.connect();
        if ("http".equalsIgnoreCase(url.getProtocol())) {
            int responseCode = con.getResponseCode();
            switch (responseCode) {
                case HttpURLConnection.HTTP_MOVED_TEMP:
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_SEE_OTHER:
                    String newLocation = con.getHeaderField("Location");
                    URL newUrl = new URL(newLocation);
                    if ("https".equalsIgnoreCase(newUrl.getProtocol())) {
                        con.disconnect();
                        // Escalate from http to https.
                        // This is not done automatically by HttpURLConnection.setInstanceFollowRedirects
                        // See https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4959149
                        return connectHttpWithRedirects(newUrl, setup);
                    }
                    break;
            }
        }
        return con;
    }

    private static boolean downloadFile(URLConnection con, File output) throws IOException {
        try {
            InputStream stream = con.getInputStream();
            int len = con.getContentLength();
            int read;
            output.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(output)) {
                read = IOUtils.copy(stream, out);
            }
            if (read != len) {
                output.delete();
                throw new IOException("Failed to read all of data from " + con.getURL() + " got " + read + " expected " + len);
            }
            return true;
        } catch (IOException e) {
            output.delete();
            throw e;
        }
    }

    private static String downloadString(URLConnection con) throws IOException {
        InputStream stream = con.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = con.getContentLength();
        int read = IOUtils.copy(stream, out);
        if (read != len) {
            throw new IOException("Failed to read all of data from " + con.getURL() + " got " + read + " expected " + len);
        }
        return new String(out.toByteArray(), CHARSET);
    }

    public enum DownloadType {

        HASH,
        MAVEN,
        RAW_URL,
        ETAGGED_URL;

    }

    private static class DownloadKey {

        private final Project project;
        private final String artifact;
        private final boolean changing;
        private final boolean generated;
        private final boolean gradle;
        private final boolean manual;

        private DownloadKey(Project project, String artifact, boolean changing, boolean generated, boolean gradle, boolean manual) {
            this.project = project;
            this.artifact = artifact;
            this.changing = changing;
            this.generated = generated;
            this.gradle = gradle;
            this.manual = manual;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DownloadKey that = (DownloadKey) o;
            return changing == that.changing &&
                    generated == that.generated &&
                    gradle == that.gradle &&
                    manual == that.manual &&
                    project.equals(that.project) &&
                    artifact.equals(that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(project, artifact, changing, generated, gradle, manual);
        }

    }

}
