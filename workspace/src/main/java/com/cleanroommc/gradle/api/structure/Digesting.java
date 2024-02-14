package com.cleanroommc.gradle.api.structure;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class Digesting {

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
        return new BigInteger(1, digested).toString(16);
    }

    private static Path hashFile(Path location, MessageDigest digest) {
        return hashFile(location, digest.getAlgorithm());
    }

    private static Path hashFile(Path location, String digestAlgorithm) {
        return location.resolveSibling(location.getFileName() + "." + digestAlgorithm.toLowerCase(Locale.ENGLISH).replace("-", ""));
    }

    private Digesting() { }

}
