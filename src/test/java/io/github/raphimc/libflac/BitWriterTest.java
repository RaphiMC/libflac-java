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

import java.io.IOException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitWriterTest {
    @Test
    void rawPattern() throws IOException {
        BitWriter w = new BitWriter();
        w.clear();
        int[] values = {1, 1, 0xa, 0xf0, 0x2aa, 0xf, 0xaaaaaaaa};
        int[] widths = {1, 2, 5, 8, 10, 4, 32};
        for (int i = 0; i < values.length; i++)
            w.writeRawUInt32(values[i], widths[i]);
        w.writeZeroes(4);
        w.writeRawUInt32(3, 2);
        w.writeZeroes(8);
        w.writeRawUInt64(0xaaaaaaaadeadbeefL, 64);
        w.writeRawUInt32(0xace, 12);
        assertEquals(152, w.getInputBitsUnconsumed());
        assertEquals("aaf0aabeaaaaaaa8300aaaaaaaadeadbeeface", HexFormat.of().formatHex(w.getBuffer()));
        w.writeRawUInt32(0x3d, 6);
        assertEquals(158, w.getInputBitsUnconsumed());
        w.zeroPadToByteBoundary();
        byte[] b = w.getBuffer();
        assertEquals(0xf4, b[19] & 255);
    }

    @Test
    void utf8Boundaries() throws IOException {
        long[] values = {0, 127, 128, 2047, 2048, 65535, 65536, 0x1fffff, 0x200000, 0x3ffffff, 0x4000000, 0x7fffffffL,
                0x80000000L, 0xfffffffffL};
        String[] patterns = {"00", "7f", "c280", "dfbf", "e0a080", "efbfbf", "f0908080", "f7bfbfbf", "f888808080",
                "fbbfbfbfbf", "fc8480808080", "fdbfbfbfbfbf", "fe828080808080", "febfbfbfbfbfbf"};
        for (int operation = 0; operation < 2; operation++)
            for (int i = 0; i < (operation == 0 ? 12 : 14); i++) {
                BitWriter w = new BitWriter();
                if (operation == 0)
                    w.writeUtf8UInt32((int) values[i]);
                else
                    w.writeUtf8UInt64(values[i]);
                assertEquals(patterns[i], HexFormat.of().formatHex(w.getBuffer()));
                assertEquals(patterns[i].length() * 4, w.getInputBitsUnconsumed());
            }
    }

    @Test
    void grow() throws IOException {
        BitWriter w = new BitWriter();
        w.writeRawUInt32(5, 4);
        int capacity = BitWriter.BITWRITER_DEFAULT_CAPACITY;
        for (int i = 0; i < capacity; i++)
            w.writeRawUInt32(0xaaaaaaaa, 32);
        assertEquals(capacity * 32 + 4, w.getInputBitsUnconsumed());
        w.zeroPadToByteBoundary();
        byte[] data = w.getBuffer();
        assertEquals(0x5a, data[0] & 255);
        assertEquals(0xa0, data[data.length - 1] & 255);
    }
}
