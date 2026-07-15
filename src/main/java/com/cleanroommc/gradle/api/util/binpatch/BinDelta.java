package com.cleanroommc.gradle.api.util.binpatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes a compact COPY/INSERT delta against an original byte array.
 *
 * <p>The format has no external dependencies so the same decoder can be used by a runtime class transformer.</p>
 */
public final class BinDelta {

    private static final int COPY = 0;
    private static final int INSERT = 1;
    private static final int HASH_LENGTH = 8;
    private static final int MINIMUM_MATCH = 16;
    private static final int MAXIMUM_CANDIDATES = 64;

    public static byte[] encode(byte[] original, byte[] revised) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(revised, "revised");

        Map<Long, List<Integer>> index = index(original);
        var output = new ByteArrayOutputStream(Math.max(64, revised.length / 4));
        int position = 0;
        int literalStart = 0;
        while (position < revised.length) {
            Match match = longestMatch(original, revised, position, index);
            if (match.length() >= MINIMUM_MATCH) {
                writeInsert(output, revised, literalStart, position);
                output.write(COPY);
                writeVarInt(output, match.offset());
                writeVarInt(output, match.length());
                position += match.length();
                literalStart = position;
            } else {
                position++;
            }
        }
        writeInsert(output, revised, literalStart, revised.length);
        return output.toByteArray();
    }

    public static byte[] decode(byte[] original, byte[] delta) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(delta, "delta");

        var input = new ByteArrayInputStream(delta);
        var output = new ByteArrayOutputStream(original.length + 64);
        int tag;
        while ((tag = input.read()) != -1) {
            switch (tag) {
                case COPY -> {
                    int offset = readVarInt(input);
                    int length = readVarInt(input);
                    if (offset > original.length || length > original.length - offset) {
                        throw corrupt("COPY range is outside the original buffer");
                    }
                    output.write(original, offset, length);
                }
                case INSERT -> {
                    int length = readVarInt(input);
                    if (length > input.available()) {
                        throw corrupt("INSERT length exceeds the remaining delta");
                    }
                    output.write(delta, delta.length - input.available(), length);
                    input.skip(length);
                }
                default -> throw corrupt("unknown operation tag " + tag);
            }
        }
        return output.toByteArray();
    }

    private static Map<Long, List<Integer>> index(byte[] original) {
        Map<Long, List<Integer>> index = new HashMap<>();
        for (int offset = 0; offset + HASH_LENGTH <= original.length; offset++) {
            index.computeIfAbsent(hash(original, offset), ignored -> new ArrayList<>()).add(offset);
        }
        return index;
    }

    private static Match longestMatch(byte[] original, byte[] revised, int revisedOffset, Map<Long, List<Integer>> index) {
        if (revisedOffset + HASH_LENGTH > revised.length) {
            return Match.NONE;
        }
        List<Integer> candidates = index.get(hash(revised, revisedOffset));
        if (candidates == null) {
            return Match.NONE;
        }
        int bestOffset = -1;
        int bestLength = 0;
        int first = Math.max(0, candidates.size() - MAXIMUM_CANDIDATES);
        for (int i = candidates.size() - 1; i >= first; i--) {
            int candidate = candidates.get(i);
            int length = matchLength(original, candidate, revised, revisedOffset);
            if (length > bestLength) {
                bestOffset = candidate;
                bestLength = length;
            }
        }
        return new Match(bestOffset, bestLength);
    }

    private static int matchLength(byte[] original, int originalOffset, byte[] revised, int revisedOffset) {
        int maximum = Math.min(original.length - originalOffset, revised.length - revisedOffset);
        int length = 0;
        while (length < maximum && original[originalOffset + length] == revised[revisedOffset + length]) {
            length++;
        }
        return length;
    }

    private static long hash(byte[] data, int offset) {
        long hash = 1125899906842597L;
        for (int i = 0; i < HASH_LENGTH; i++) {
            hash = 31 * hash + data[offset + i];
        }
        return hash;
    }

    private static void writeInsert(ByteArrayOutputStream output, byte[] data, int from, int to) {
        if (to <= from) {
            return;
        }
        output.write(INSERT);
        writeVarInt(output, to - from);
        output.write(data, from, to - from);
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            output.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        output.write(remaining);
    }

    private static int readVarInt(ByteArrayInputStream input) {
        int value = 0;
        for (int byteIndex = 0; byteIndex < 5; byteIndex++) {
            int next = input.read();
            if (next == -1) {
                throw corrupt("Truncated VarInt");
            }
            if (byteIndex == 4 && (next & 0xF8) != 0) {
                throw corrupt("VarInt exceeds signed int range");
            }
            value |= (next & 0x7F) << (byteIndex * 7);
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw corrupt("VarInt is too long");
    }

    private static IllegalArgumentException corrupt(String detail) {
        return new IllegalArgumentException("Corrupt binpatch delta: " + detail);
    }

    private record Match(int offset, int length) {

        private static final Match NONE = new Match(-1, 0);

    }

    private BinDelta() { }

}
