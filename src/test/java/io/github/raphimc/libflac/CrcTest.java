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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrcTest {
    private static final int DATA_SIZE = 32768;
    private static final int CRC8_POLYNOMIAL = 0x07;
    private static final int CRC16_POLYNOMIAL = 0x8005;

    /* Reference implementations of CRC-8 and CRC-16 to check against. */
    private int ref8(int value, int crc) {
        crc ^= value & 255;
        for (int i = 0; i < 8; i++)
            crc = ((crc << 1) ^ ((crc >>> 7) != 0 ? CRC8_POLYNOMIAL : 0)) & 255;
        return crc;
    }

    private int ref16(int value, int crc) {
        crc ^= (value & 255) << 8;
        for (int i = 0; i < 8; i++)
            crc = ((crc << 1) ^ ((crc >>> 15) != 0 ? CRC16_POLYNOMIAL : 0)) & 65535;
        return crc;
    }

    @Test
    void checksums() {
        /* Initialize data reproducibly with pseudo-random values. */
        byte[] data = new byte[DATA_SIZE];
        for (int i = 1; i < data.length; i++)
            data[i] = (byte) ref8(i % 256, data[i - 1] & 255);
        int crc8 = 0, crc16 = 0, incremental = 0;
        assertEquals(0, Crc.crc8(data, 0, 0));
        assertEquals(0, Crc.crc16(data, 0, 0));
        for (int i = 0; i < data.length; i++) {
            crc8 = ref8(data[i], crc8);
            crc16 = ref16(data[i], crc16);
            incremental = Crc.crc16Update(data[i], incremental);
            assertEquals(crc8, Crc.crc8(data, 0, i + 1));
            assertEquals(crc16, Crc.crc16(data, 0, i + 1));
            assertEquals(crc16, incremental);
        }

        for (int wordBytes : new int[] {4, 8})
            for (int n = 1; n <= 16; n++) {
                int ref = 0, actual = 0;
                for (int i = 0; i <= data.length / wordBytes - n; i += n)
                    for (int k = 0; k < n; k++) {
                        long word = 0;
                        for (int j = 0; j < wordBytes; j++) {
                            int index = (i + k) * wordBytes + wordBytes - 1 - j;
                            word = (word << Byte.SIZE) | (data[index] & 0xffL);
                        }
                        for (int shift = (wordBytes - 1) * 8; shift >= 0; shift -= 8) {
                            int value = (int) (word >>> shift);
                            ref = ref16(value, ref);
                            actual = Crc.crc16Update(value, actual);
                        }
                        assertEquals(ref, actual);
                    }
            }
    }
}
