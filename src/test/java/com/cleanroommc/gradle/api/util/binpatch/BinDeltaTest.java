/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.binpatch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class BinDeltaTest {

    @Test
    void roundTripsIdenticalBytes() {
        var original = bytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
        assertThat(BinDelta.decode(original, BinDelta.encode(original, original))).isEqualTo(original);
    }

    @Test
    void roundTripsSmallChangeBelowMinimumMatch() {
        var original = new byte[64];
        Arrays.fill(original, (byte) 7);
        var revised = original.clone();
        revised[3] = 8;
        assertThat(BinDelta.decode(original, BinDelta.encode(original, revised))).isEqualTo(revised);
    }

    @Test
    void roundTripsLargeCopyableBlock() {
        var original = new byte[256];
        for (var i = 0; i < original.length; i++) {
            original[i] = (byte) i;
        }
        var revised = new byte[300];
        System.arraycopy(original, 32, revised, 0, 200);
        Arrays.fill(revised, 200, 300, (byte) 0xA5);
        assertThat(BinDelta.decode(original, BinDelta.encode(original, revised))).isEqualTo(revised);
    }

    @Test
    void roundTripsEmptyAndUnrelatedInputs() {
        assertThat(BinDelta.decode(new byte[0], BinDelta.encode(new byte[0], new byte[0]))).isEqualTo(new byte[0]);
        var original = new byte[] { 1, 2, 3 };
        var revised = new byte[] { 9, 9, 9, 9 };
        assertThat(BinDelta.decode(original, BinDelta.encode(original, revised))).isEqualTo(revised);
    }

    @Test
    void fuzzRoundTrip() {
        var random = new Random(0xC1EA);
        for (var round = 0; round < 25; round++) {
            var original = new byte[random.nextInt(512)];
            random.nextBytes(original);
            var revised = new byte[random.nextInt(512)];
            random.nextBytes(revised);
            if (random.nextBoolean() && original.length > 16 && revised.length > 16) {
                var offset = random.nextInt(original.length - 16);
                var at = random.nextInt(revised.length - 16);
                var length = Math.min(16 + random.nextInt(32), Math.min(original.length - offset, revised.length - at));
                System.arraycopy(original, offset, revised, at, length);
            }
            assertThat(BinDelta.decode(original, BinDelta.encode(original, revised))).as("round " + round).isEqualTo(revised);
        }
    }

    @Test
    void rejectsCopyOutsideOriginal() {
        var original = new byte[16];
        var delta = new byte[] { 0, (byte) 0x80, 0x01, 1 };
        var failure = catchThrowableOfType(() -> BinDelta.decode(original, delta), IllegalArgumentException.class);
        assertThat(failure).as(failure.getMessage()).hasMessageContaining("COPY range is outside the original buffer");
    }

    @Test
    void rejectsInsertBeyondDelta() {
        var delta = new byte[] { 1, 10, 1, 2 };
        var failure = catchThrowableOfType(() -> BinDelta.decode(new byte[4], delta), IllegalArgumentException.class);
        assertThat(failure).as(failure.getMessage()).hasMessageContaining("INSERT length exceeds the remaining delta");
    }

    @Test
    void rejectsUnknownTagAndInvalidVarInts() {
        assertThatThrownBy(() -> BinDelta.decode(new byte[4], new byte[] { 7 }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown operation tag");
        assertThatThrownBy(() -> BinDelta.decode(new byte[4], new byte[] { 0 }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Truncated VarInt");
        assertThatThrownBy(() -> BinDelta.decode(new byte[4], new byte[] { 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x08 }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VarInt exceeds signed int range");
    }

    private static byte[] bytes(int... values) {
        var result = new byte[values.length];
        for (var i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

}
