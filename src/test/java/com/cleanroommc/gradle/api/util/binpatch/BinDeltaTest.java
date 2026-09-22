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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinDeltaTest {

    @Test
    void roundTripsEdgeCases() {
        var counting = new byte[256];
        for (var i = 0; i < counting.length; i++) {
            counting[i] = (byte) i;
        }
        var shifted = new byte[300];
        System.arraycopy(counting, 32, shifted, 0, 200);
        var repeated = new byte[64];
        Arrays.fill(repeated, (byte) 7);
        var oneByteOff = repeated.clone();
        oneByteOff[3] = 8;

        assertRoundTrip(new byte[0], new byte[0]);
        assertRoundTrip(repeated, oneByteOff);
        assertRoundTrip(counting, counting);
        assertRoundTrip(counting, shifted);
        assertRoundTrip(new byte[] { 1, 2, 3 }, new byte[] { 9, 9, 9, 9 });
    }

    @Test
    void roundTripsRandomInputs() {
        var random = new Random(0xC1EA);
        for (var round = 0; round < 25; round++) {
            var original = new byte[random.nextInt(512)];
            random.nextBytes(original);
            var revised = new byte[random.nextInt(512)];
            random.nextBytes(revised);
            if (original.length > 16 && revised.length > 16) {
                var offset = random.nextInt(original.length - 16);
                var at = random.nextInt(revised.length - 16);
                System.arraycopy(original, offset, revised, at, Math.min(48, Math.min(original.length - offset, revised.length - at)));
            }
            assertRoundTrip(original, revised);
        }
    }

    @ParameterizedTest
    @CsvSource(
            {
                    "00800101,   COPY range is outside the original buffer",
                    "010a0102,   INSERT length exceeds the remaining delta",
                    "07,         unknown operation tag",
                    "00,         Truncated VarInt",
                    "01ffffffff08, VarInt exceeds signed int range"
            }
    )
    void rejectsMalformedDeltas(String delta, String message) {
        assertThatThrownBy(() -> BinDelta.decode(new byte[16], HexFormat.of().parseHex(delta)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private static void assertRoundTrip(byte[] original, byte[] revised) {
        assertThat(BinDelta.decode(original, BinDelta.encode(original, revised))).isEqualTo(revised);
    }

}
