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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EndswapTest {
    @Test
    void swap() {
        for (short v : new short[] {0x1234, (short) 0xabcd}) {
            assertNotEquals(v, Short.reverseBytes(v));
            assertEquals(v, Short.reverseBytes(Short.reverseBytes(v)));
        }
        for (int v : new int[] {0x12345678, 0xabcdef01}) {
            assertNotEquals(v, Integer.reverseBytes(v));
            assertEquals(v, Integer.reverseBytes(Integer.reverseBytes(v)));
        }

        short u16 = 0x1234;
        byte[] data = {(byte) u16, (byte) (u16 >>> 8)};
        assertArrayEquals(new byte[] {0x34, 0x12}, data);
        int u32 = 0x12345678;
        data = new byte[] {(byte) u32, (byte) (u32 >>> 8), (byte) (u32 >>> 16), (byte) (u32 >>> 24)};
        assertArrayEquals(new byte[] {0x78, 0x56, 0x34, 0x12}, data);
    }
}
