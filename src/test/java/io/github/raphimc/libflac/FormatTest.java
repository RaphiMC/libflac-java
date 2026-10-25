/* test_libFLAC - Unit tester for libFLAC
 * Copyright (C) 2004-2009  Josh Coalson
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatTest {
    @Test
    void sampleRates() {
        assertTrue(Format.sampleRateIsValid(0));
        assertTrue(Format.sampleRateIsSubset(0));
        assertTrue(Format.sampleRateIsValid(1));
        assertTrue(Format.sampleRateIsSubset(1));
        assertTrue(Format.sampleRateIsValid(9));
        assertTrue(Format.sampleRateIsSubset(9));
        assertTrue(Format.sampleRateIsValid(10));
        assertTrue(Format.sampleRateIsSubset(10));
        assertTrue(Format.sampleRateIsValid(4000));
        assertTrue(Format.sampleRateIsSubset(4000));
        assertTrue(Format.sampleRateIsValid(8000));
        assertTrue(Format.sampleRateIsSubset(8000));
        assertTrue(Format.sampleRateIsValid(11025));
        assertTrue(Format.sampleRateIsSubset(11025));
        assertTrue(Format.sampleRateIsValid(12000));
        assertTrue(Format.sampleRateIsSubset(12000));
        assertTrue(Format.sampleRateIsValid(16000));
        assertTrue(Format.sampleRateIsSubset(16000));
        assertTrue(Format.sampleRateIsValid(22050));
        assertTrue(Format.sampleRateIsSubset(22050));
        assertTrue(Format.sampleRateIsValid(24000));
        assertTrue(Format.sampleRateIsSubset(24000));
        assertTrue(Format.sampleRateIsValid(32000));
        assertTrue(Format.sampleRateIsSubset(32000));
        assertTrue(Format.sampleRateIsValid(32768));
        assertTrue(Format.sampleRateIsSubset(32768));
        assertTrue(Format.sampleRateIsValid(44100));
        assertTrue(Format.sampleRateIsSubset(44100));
        assertTrue(Format.sampleRateIsValid(48000));
        assertTrue(Format.sampleRateIsSubset(48000));
        assertTrue(Format.sampleRateIsValid(65000));
        assertTrue(Format.sampleRateIsSubset(65000));
        assertTrue(Format.sampleRateIsValid(65535));
        assertTrue(Format.sampleRateIsSubset(65535));
        assertTrue(Format.sampleRateIsValid(65536));
        assertFalse(Format.sampleRateIsSubset(65536));
        assertTrue(Format.sampleRateIsValid(65540));
        assertTrue(Format.sampleRateIsSubset(65540));
        assertTrue(Format.sampleRateIsValid(65550));
        assertTrue(Format.sampleRateIsSubset(65550));
        assertTrue(Format.sampleRateIsValid(65555));
        assertFalse(Format.sampleRateIsSubset(65555));
        assertTrue(Format.sampleRateIsValid(66000));
        assertTrue(Format.sampleRateIsSubset(66000));
        assertTrue(Format.sampleRateIsValid(66001));
        assertFalse(Format.sampleRateIsSubset(66001));
        assertTrue(Format.sampleRateIsValid(96000));
        assertTrue(Format.sampleRateIsSubset(96000));
        assertTrue(Format.sampleRateIsValid(100000));
        assertTrue(Format.sampleRateIsSubset(100000));
        assertTrue(Format.sampleRateIsValid(100001));
        assertFalse(Format.sampleRateIsSubset(100001));
        assertTrue(Format.sampleRateIsValid(192000));
        assertTrue(Format.sampleRateIsSubset(192000));
        assertTrue(Format.sampleRateIsValid(500000));
        assertTrue(Format.sampleRateIsSubset(500000));
        assertTrue(Format.sampleRateIsValid(500001));
        assertFalse(Format.sampleRateIsSubset(500001));
        assertTrue(Format.sampleRateIsValid(500010));
        assertTrue(Format.sampleRateIsSubset(500010));
        assertTrue(Format.sampleRateIsValid(655349));
        assertFalse(Format.sampleRateIsSubset(655349));
        assertTrue(Format.sampleRateIsValid(655350));
        assertTrue(Format.sampleRateIsSubset(655350));
        assertTrue(Format.sampleRateIsValid(655351));
        assertFalse(Format.sampleRateIsSubset(655351));
        assertTrue(Format.sampleRateIsValid(655360));
        assertFalse(Format.sampleRateIsSubset(655360));
        assertTrue(Format.sampleRateIsValid(700000));
        assertFalse(Format.sampleRateIsSubset(700000));
        assertTrue(Format.sampleRateIsValid(700010));
        assertFalse(Format.sampleRateIsSubset(700010));
        assertTrue(Format.sampleRateIsValid(705600));
        assertFalse(Format.sampleRateIsSubset(705600));
        assertTrue(Format.sampleRateIsValid(768000));
        assertFalse(Format.sampleRateIsSubset(768000));
        assertTrue(Format.sampleRateIsValid(1000000));
        assertFalse(Format.sampleRateIsSubset(1000000));
        assertTrue(Format.sampleRateIsValid(1048575));
        assertFalse(Format.sampleRateIsSubset(1048575));
        assertFalse(Format.sampleRateIsValid(1100000));
        assertFalse(Format.sampleRateIsSubset(1100000));
    }

    @Test
    void commentNames() {
        assertTrue(Format.vorbiscommentEntryNameIsLegal(""));
        assertTrue(Format.vorbiscommentEntryNameIsLegal("a"));
        assertFalse(Format.vorbiscommentEntryNameIsLegal("="));
        assertFalse(Format.vorbiscommentEntryNameIsLegal("a="));
        assertFalse(Format.vorbiscommentEntryNameIsLegal("\u0001"));
        assertFalse(Format.vorbiscommentEntryNameIsLegal("\u001f"));
        assertTrue(Format.vorbiscommentEntryNameIsLegal("}"));
        assertFalse(Format.vorbiscommentEntryNameIsLegal("~"));
        assertFalse(Format.vorbiscommentEntryNameIsLegal("\u00ff"));
    }

    @Test
    void vcentry_values() {
        assertTrue(Format.vorbiscommentEntryValueIsLegal(""));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0000"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0001"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u007f"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0085"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0940"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u1140"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u1540"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u1940"));
    }

    @Test
    void vcentry_values_nt() {
        assertTrue(Format.vorbiscommentEntryValueIsLegal(""));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0001"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u007f"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0085"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u0940"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u1140"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u1540"));
        assertTrue(Format.vorbiscommentEntryValueIsLegal("\u1940"));
    }

    @Test
    void vcentries() {
        assertFalse(Format.vorbiscommentEntryIsLegal(""));
        assertFalse(Format.vorbiscommentEntryIsLegal("a"));
        assertTrue(Format.vorbiscommentEntryIsLegal("="));
        assertTrue(Format.vorbiscommentEntryIsLegal("a="));
        assertFalse(Format.vorbiscommentEntryIsLegal("\u0001="));
        assertFalse(Format.vorbiscommentEntryIsLegal("\u001f="));
        assertTrue(Format.vorbiscommentEntryIsLegal("}="));
        assertFalse(Format.vorbiscommentEntryIsLegal("~="));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u0001"));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u007f"));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u0085"));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u0940"));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u1140"));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u1540"));
        assertTrue(Format.vorbiscommentEntryIsLegal("a=\u1940"));
    }
}
