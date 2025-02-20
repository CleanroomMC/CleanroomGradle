package com.cleanroommc.gradle.newapi.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class IO {

    private static final Gson GSON;

    static {
        GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    }

    public static <T> T readJson(Path jsonPath, Class<T> type) {
        try (var reader = Files.newBufferedReader(jsonPath)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T readJson(File jsonPath, Class<T> type) {
        return readJson(jsonPath.toPath(), type);
    }

    public static <T> T readJson(String jsonString, Class<T> type) {
        try (var reader = new StringReader(jsonString)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static String sha1(Path path) {
        try (var is = Files.newInputStream(path)) {
            return DigestUtils.sha1Hex(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String sha1(File path) {
        return sha1(path.toPath());
    }

    public static boolean sha1Match(Path path, String expectedHash) {
        if (Files.exists(path)) {
            return sha1(path).equalsIgnoreCase(expectedHash);
        }
        return false;
    }

    public static boolean sha1Match(File path, String expectedHash) {
        return sha1Match(path.toPath(), expectedHash);
    }

    public static FileOutputStream out(File file) throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    public static FileInputStream in(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }

    public static ZipOutputStream zipOut(File file) throws FileNotFoundException {
        return new ZipOutputStream(out(file));
    }

    public static ZipInputStream zipIn(File file) throws FileNotFoundException {
        return new ZipInputStream(in(file));
    }

    public static File runDir(Project project, String version, Environment env, Side side) {
        return FileUtils.getFile(project.getProjectDir(), "run", version, env.toString(), side.name().toLowerCase(Locale.ENGLISH));
    }

    private IO() { }

}
