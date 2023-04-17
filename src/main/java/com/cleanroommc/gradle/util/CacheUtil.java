package com.cleanroommc.gradle.util;

import com.cleanroommc.gradle.CleanroomLogging;
import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class CacheUtil {
    private CacheUtil() {
    }

    public static void checkShaForFileAndDeleteFile(final Project project, final File file, final String expectedSha1) {
        try {
            CacheUtil.checkSha1ForFile(file, expectedSha1);
        } catch (CacheUtil.ChecksumMismatch checksumMismatch) {
            CleanroomLogging.warn(project.getLogger(), checksumMismatch.toString());
            CleanroomLogging.warn(project.getLogger(), "Deleting file at {}, refresh gradle to redownload the file", file.getAbsolutePath());
            if (!file.delete()) {
                throw new UncheckedIOException(new IOException(String.format("Unable to delete file at %s", file.getAbsolutePath())));
            }
        }
    }

    public static void checkSha1ForFile(final File file, final String expectedSha1) throws ChecksumMismatch{
        if (file.exists()) {
            if (file.isDirectory()) {
                throw new RuntimeException(String.format("File at %s is pointing to a directory", file.getAbsolutePath()));
            }
        } else {
            throw new RuntimeException(String.format("File at %s does not exist", file.getAbsolutePath()));
        }

        final String fileSha1;
        try {
            fileSha1 = new DigestUtils(DigestUtils.getSha1Digest()).digestAsHex(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!fileSha1.equals(expectedSha1)) {
            throw new ChecksumMismatch(file, expectedSha1, fileSha1);
        }
    }

    public static class ChecksumMismatch extends Exception {
        public ChecksumMismatch(File file, String expectedSha1, String fileSha1) {
            super(String.format("Mismatched checksum for file %s, expected: %s actual: %s", file.getAbsolutePath(), expectedSha1, fileSha1));
        }
    }

}
