/* test_libFLAC - Unit tester for libFLAC
 * Copyright (C) 2000-2009  Josh Coalson
 * Copyright (C) 2011-2023  Xiph.Org Foundation
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

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitReaderTest {
    private byte[] data() {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) (i * 8 + 7);
        return b;
    }

    @Test
    void rawAndCrc() throws Exception {
        BitReader r = new BitReader(new ByteArrayInputStream(data()));
        for (int n : new int[] {1, 2, 5, 8, 10, 4, 32, 4, 2, 8})
            r.readRawUInt32(n);
        r.readRawUInt64(64);
        r.readRawUInt32(12);
        assertEquals(19, data().length - r.getInputBitsUnconsumed() / Byte.SIZE);
        assertTrue(r.isConsumedByteAligned());
        assertEquals(0x5e4c, r.getReadCrc16());
        r = new BitReader(new ByteArrayInputStream(data()));
        r.resetReadCrc16(65535);
        assertEquals(65535, r.getReadCrc16());
        r.resetReadCrc16(0);
        assertEquals(0, r.getReadCrc16());
        r.readRawUInt32(16);
        r.resetReadCrc16(0);
        r.readRawUInt32(32);
        assertEquals(0x7f6b, r.getReadCrc16());
    }

    @Test
    void unaligned() throws Exception {
        for (int more : new int[] {0, 32}) {
            BitReader r = new BitReader(new ByteArrayInputStream(data()));
            r.skipBitsNoCrc(8);
            r.resetReadCrc16(0);
            for (int n : new int[] {1, 2, 5, 8})
                r.readRawUInt32(n);
            r.readRawUInt32(more);
            assertEquals(more == 0 ? 0x2272 : 0x42bf, r.getReadCrc16());
        }
    }
}
