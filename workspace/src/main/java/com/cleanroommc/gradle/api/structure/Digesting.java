package com.cleanroommc.gradle.api.structure;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class Digesting {

    public static String sha1(File location) {
        try (var is = new FileInputStream(location)) {
            return DigestUtils.sha1Hex(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean checkSha1(File location, String expectedHash) {
        if (IO.exists(location)) {
            return sha1(location).equalsIgnoreCase(expectedHash);
        }
        return false;
    }

    public static boolean check(Path location, String digestAlgorithm, String hash) {
        if (Files.exists(location)) {
            try {
                var digest = MessageDigest.getInstance(digestAlgorithm);
                var hex = bytesToHex(digest.digest(Files.readAllBytes(location)));
                return hex.equals(hash);
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public static boolean check(Path location, String digestAlgorithm) {
        if (Files.exists(location)) {
            var hashFile = hashFile(location, digestAlgorithm);
            if (Files.exists(hashFile)) {
                try {
                    var digest = MessageDigest.getInstance(digestAlgorithm);
                    var hex = bytesToHex(digest.digest(Files.readAllBytes(location)));
                    return hex.equals(Files.readString(hashFile));
                } catch (NoSuchAlgorithmException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    public static void generate(Path location, String digestAlgorithm) {
        if (Files.exists(location)) {
            var hashFile = hashFile(location, digestAlgorithm);
            if (!Files.exists(hashFile)) {
                try {
                    var digest = MessageDigest.getInstance(digestAlgorithm);
                    var hex = bytesToHex(digest.digest(Files.readAllBytes(location)));
                    Files.writeString(hashFile, hex);
                } catch (NoSuchAlgorithmException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static String generateString(Path location, String digestAlgorithm) {
        if (Files.exists(location)) {
            try {
                var digest = MessageDigest.getInstance(digestAlgorithm);
                return bytesToHex(digest.digest(Files.readAllBytes(location)));
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("File does not exist.");
    }

    public static String get(Path location, String digestAlgorithm) {
        if (Files.exists(location)) {
            try {
                var digest = MessageDigest.getInstance(digestAlgorithm);
                return bytesToHex(digest.digest(Files.readAllBytes(location)));
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("No file found at " + location);
    }

    public static String bytesToHex(byte[] digested) {
        return Hex.encodeHexString(digested);
    }

    private static Path hashFile(Path location, MessageDigest digest) {
        return hashFile(location, digest.getAlgorithm());
    }

    private static Path hashFile(Path location, String digestAlgorithm) {
        return location.resolveSibling(location.getFileName() + "." + digestAlgorithm.toLowerCase(Locale.ENGLISH).replace("-", ""));
    }

    private Digesting() { }

}
