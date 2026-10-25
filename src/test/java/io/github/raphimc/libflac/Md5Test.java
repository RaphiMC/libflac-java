/* test_libFLAC - Unit tester for libFLAC
 * Copyright (C) 2014-2023  Xiph.Org Foundation
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.github.raphimc.libflac;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Md5Test {
    private static final int MAX_CHANNEL_COUNT = 8;
    private static final int MD5_SAMPLE_COUNT = 64;
    private static final String[] target_digests = {
            /* 1 channel */ /* 1 byte per sample */ "c19a5beb578f26ebfb347cef04316d7d",
            /* 2 bytes per sample */ "d47890d3a9174e76ca4d272098368b2e",
            /* 3 bytes per sample */ "5a4bd6aca17084197c0dfb5ba97bcb54",
            /* 4 bytes per sample */ "79d57a32060bfe46a3e7bac5f7486f50",
            /* 2 channels */ "89accf91f18ceaab461274bc4e82be7d", "b917165bd81cc84e5a28fbba87747644",
            "ec6392ca4f6b9eb19fec3b2c1530fd2a", "054dfdb89d8aa2dd2647c6fb4f23676d",
            /* 3 channels */ "ad05daf37aa194db0c6106b294396ca9", "8bcc414de9e3c261618a8b22c64eaca7",
            "8ace97c186aebc73888b355a3733f9cf", "6959e83829808021b1d2baf628d66a83",
            /* 4 channels */ "614075ef22f10fa6086c88ff2c4e980b", "a0773a594abfd05ccce3b9832bf3df1a",
            "dbd7f1821360427c84e6cf30aba264f1", "4a9aad530574b11cb8d4ae7813f62a11",
            /* 5 channels */ "ccca44c054e2c9ba9932c965f33e4434", "40386addde89103c8eecdf15534c2c92",
            "c8950a7c1730c0ac8e34db7976647c6e", "3f06118a8d80b54f8bb58eb3273e41e8",
            /* 6 channels */ "61e4bdb1c02ff44c6e095abd90188b62", "47e76e3b1886601b0962c6c97c4c03b5",
            "7057bf67660fe30a6cd29766a2d2e479", "aa3fc7f57aa546f7eae3d51aa462befa",
            /* 7 channels */ "7c8dd28cfd91bb776f0ef0391f39c4ac", "fbab183f1e1da577e05cea456f64a464",
            "e3ac3350c1b193fbca4b15cb2dcdd5ef", "10fb0283760de5d23bb14c783b73f71a",
            /* 8 channels */ "657be592e21c953ed72f64a086ec1aed", "9d048fa4ea10ecb8a388e25d3ce2fb94",
            "5ad3d2756afaa742f3bf0ebc902af85f", "76e1e5f6e3440829ae7919eba857162a"};

    @Test
    void empty() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HexFormat.of().formatHex(new Md5Context().finish()));
    }

    @Test
    void codec() {
        /* Set up signal data using a trivial Linear Congruent PRNG. */
        int[][] signal = new int[MAX_CHANNEL_COUNT][MD5_SAMPLE_COUNT];
        int seed = 0x12345679;
        for (int c = 0; c < MAX_CHANNEL_COUNT; c++)
            for (int i = 0; i < MD5_SAMPLE_COUNT; i++) {
                seed = seed * 1103515245 + 12345;
                signal[c][i] = seed;
            }
        for (int c = 1; c <= MAX_CHANNEL_COUNT; c++)
            for (int bytes = 1; bytes <= 4; bytes++) {
                Md5Context md5 = new Md5Context();
                md5.accumulate(signal, 0, c, MD5_SAMPLE_COUNT, bytes);
                assertEquals(target_digests[(c - 1) * 4 + bytes - 1], HexFormat.of().formatHex(md5.finish()));
            }
    }
}
