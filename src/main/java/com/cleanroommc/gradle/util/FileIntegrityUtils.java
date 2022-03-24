package com.cleanroommc.gradle.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import static com.cleanroommc.gradle.Constants.CHARSET;

public class FileIntegrityUtils {

    public static boolean isFileCorrupt(File file, long size, String expectedHash, HashAlgorithm algo) throws IOException {
        return !file.exists() || file.length() != size || !expectedHash.equalsIgnoreCase(hash(file, algo));
    }

    public static void updateHash(File target, HashAlgorithm algo) throws IOException {
        updateHash(target, hash(target, algo), new File(target.getAbsolutePath() + algo.fileExtension));
    }

    public static String hash(File target, HashAlgorithm algo) throws IOException {
        return Files.asByteSource(target).hash(algo.hashFunction).toString();
    }

    public static String hash(String target, HashAlgorithm algo) {
        return algo.hashFunction.hashString(target, CHARSET).toString();
    }

    private static void updateHash(File target, String hash, File cache) throws IOException {
        if (target.exists()) {
            Files.write(hash.getBytes(), cache);
        } else if (cache.exists()) {
            cache.delete();
        }
    }

    private FileIntegrityUtils() { }

    public enum HashAlgorithm {

        MD5(Hashing.md5(), ".md5"),
        SHA1(Hashing.sha1(), ".sha1"),
        SHA256(Hashing.sha256(), ".sha256"),
        SHA384(Hashing.sha384(), ".sha384"),
        SHA512(Hashing.sha512(), ".sha512");

        private final HashFunction hashFunction;
        private final String fileExtension;

        HashAlgorithm(HashFunction hashFunction, String fileExtension) {
            this.hashFunction = hashFunction;
            this.fileExtension = fileExtension;
        }

        public HashFunction getHashFunction() {
            return hashFunction;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getHash(Function<HashFunction, HashCode> function) {
            return function.apply(hashFunction).toString();
        }
    }

}
