/* test_libFLAC - Unit tester for libFLAC
 * Copyright (C) 2002-2009  Josh Coalson
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataObjectTest {
    private void cloneEqual(StreamMetadata b) {
        assertTrue(b.isEqual(b.copy()));
    }

    @Test
    void creationAndApplication() {
        StreamMetadata[] blocks = {new StreamInfo(), new Padding(), new Application(), new SeekTable(),
                new VorbisComment(), new CueSheet(), new Picture()};
        int[] lengths = {34, 0, 4, 0, 8 + Format.VENDOR_STRING.getBytes(StandardCharsets.UTF_8).length, 396, 32};
        for (int i = 0; i < blocks.length; i++) {
            assertEquals(lengths[i], blocks[i].getLength());
            cloneEqual(blocks[i]);
        }
        Application a = new Application();
        byte[] dummy = {'a', 'b', 'c', 'd'};
        for (boolean copy : new boolean[] {true, false}) {
            a.setData(dummy.clone(), copy);
            assertEquals(8, a.getLength());
            assertArrayEquals(dummy, a.data);
            cloneEqual(a);
        }
    }

    private void points(SeekTable s, long... expected) {
        assertEquals(18 * expected.length, s.getLength());
        assertArrayEquals(expected, Arrays.stream(s.points).mapToLong(SeekPoint::getSampleNumber).toArray());
        for (SeekPoint p : s.points) {
            assertEquals(0, p.streamOffset);
            assertEquals(0, p.frameSamples);
        }
    }

    @Test
    void seektable() {
        SeekTable s = new SeekTable();
        points(s);
        cloneEqual(s);
        s.resizePoints(2);
        points(s, -1, -1);
        s.resizePoints(1);
        points(s, -1);
        assertTrue(s.isLegal());
        s.resizePoints(0);
        points(s);
        s.insertPoint(0, new SeekPoint());
        points(s, -1);
        s.insertPoint(0, new SeekPoint(1, 0, 0));
        points(s, 1, -1);
        s.insertPoint(1, new SeekPoint(2, 0, 0));
        points(s, 1, 2, -1);
        s.insertPoint(3, new SeekPoint(3, 0, 0));
        points(s, 1, 2, -1, 3);
        cloneEqual(s);
        s.deletePoint(2);
        points(s, 1, 2, 3);
        s.deletePoint(2);
        points(s, 1, 2);
        s.deletePoint(0);
        points(s, 2);
        s.setPoint(0, new SeekPoint(1, 0, 0));
        points(s, 1);
        s = new SeekTable();
        /* seektable template functions */
        s.templateAppendPlaceholders(2);
        points(s, -1, -1);
        s.templateAppendPoint(7);
        points(s, -1, -1, 7);
        s.templateAppendPoints(new long[] {3, 7});
        points(s, -1, -1, 7, 3, 7);
        s.templateAppendSpacedPoints(3, 30);
        points(s, -1, -1, 7, 3, 7, 0, 10, 20);
        s.templateSort(true);
        points(s, 0, 3, 7, 10, 20, -1, -1);
        assertTrue(s.isLegal());
        s.templateSort(false);
        points(s, 0, 3, 7, 10, 20, -1, -1);
        assertTrue(s.isLegal());
        s.templateAppendSpacedPointsBySamples(10, 30);
        points(s, 0, 3, 7, 10, 20, -1, -1, 0, 10, 20);
        s.templateAppendSpacedPointsBySamples(11, 30);
        points(s, 0, 3, 7, 10, 20, -1, -1, 0, 10, 20, 0, 11, 22);
    }

    @Test
    void commentEntry() {
        String e = VorbisComment.entryFromNameValuePair("name", "value");
        assertEquals("name=value", e);
        assertArrayEquals(new String[] {"name", "value"}, VorbisComment.entryToNameValuePair(e));
        assertTrue(VorbisComment.entryMatches(e, "name"));
        assertFalse(VorbisComment.entryMatches(e, "blah"));
    }

    private void comments(VorbisComment v, String vendor, String... expected) {
        assertArrayEquals(expected, v.comments);
        assertEquals(vendor, v.vendorString);
        int length = 8 + vendor.getBytes(StandardCharsets.UTF_8).length;
        for (String c : expected)
            length += 4 + c.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(length, v.getLength());
    }

    @Test
    void comments() {
        VorbisComment v = new VorbisComment();
        int j;
        String entry = "";
        v = new VorbisComment();
        v.resizeComments(2);
        comments(v, Format.VENDOR_STRING, "", "");
        v.resizeComments(1);
        comments(v, Format.VENDOR_STRING, "");
        v.resizeComments(0);
        comments(v, Format.VENDOR_STRING);
        assertTrue(v.appendComment("name1=field1"));
        comments(v, Format.VENDOR_STRING, "name1=field1");
        assertTrue(v.appendComment("name2=field2"));
        comments(v, Format.VENDOR_STRING, "name1=field1", "name2=field2");
        v.resizeComments(0);
        comments(v, Format.VENDOR_STRING);
        assertTrue(v.insertComment(0, "name1=field1"));
        comments(v, Format.VENDOR_STRING, "name1=field1");
        assertTrue(v.insertComment(0, "name2=field2"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name1=field1");
        assertTrue(v.insertComment(1, "name3=field3"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1");
        assertTrue(v.insertComment(3, "name4=field4"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4");
        assertTrue(v.insertComment(4, "name3=field3dup1"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1");
        assertTrue(v.insertComment(5, "name3=field3dup1"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        j = v.findEntryFrom(0, "name3");
        assertEquals(1, j);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        j = v.findEntryFrom(j + 1, "name3");
        assertEquals(4, j);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        j = v.findEntryFrom(j + 1, "name3");
        assertEquals(5, j);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        j = v.findEntryFrom(0, "name2");
        assertEquals(0, j);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        j = v.findEntryFrom(j + 1, "name2");
        assertEquals(-1, j);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        j = v.findEntryFrom(0, "blah");
        assertEquals(-1, j);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        assertTrue(v.replaceComment("name3=field3new1", false));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new1", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        assertTrue(v.replaceComment("name3=field3new2", true));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new2", "name1=field1", "name4=field4");
        v.deleteComment(2);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new2", "name4=field4");
        v.deleteComment(2);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new2");
        v.deleteComment(0);
        comments(v, Format.VENDOR_STRING, "name3=field3new2");
        assertTrue(v.appendComment("rem0=val0"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2", "rem0=val0");
        assertTrue(v.appendComment("rem0=val1"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2", "rem0=val0", "rem0=val1");
        assertTrue(v.appendComment("rem0=val2"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2", "rem0=val0", "rem0=val1", "rem0=val2");
        assertFalse(v.removeEntryMatching("blah"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2", "rem0=val0", "rem0=val1", "rem0=val2");
        assertTrue(v.removeEntryMatching("rem0"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2", "rem0=val1", "rem0=val2");
        assertEquals(0, v.removeEntriesMatching("blah"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2", "rem0=val1", "rem0=val2");
        assertEquals(2, v.removeEntriesMatching("rem0"));
        comments(v, Format.VENDOR_STRING, "name3=field3new2");
        assertTrue(v.setComment(0, "name5=field5"));
        comments(v, Format.VENDOR_STRING, "name5=field5");
        v.setVendorString("name6=field6");
        comments(v, "name6=field6", "name5=field5");
        v = new VorbisComment();
        assertTrue(v.appendComment("name1=field1"));
        comments(v, Format.VENDOR_STRING, "name1=field1");
        assertTrue(v.appendComment("name2=field2"));
        comments(v, Format.VENDOR_STRING, "name1=field1", "name2=field2");
        v = new VorbisComment();
        assertTrue(v.insertComment(0, "name1=field1"));
        comments(v, Format.VENDOR_STRING, "name1=field1");
        assertTrue(v.insertComment(0, "name2=field2"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name1=field1");
        assertTrue(v.insertComment(1, "name3=field3"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1");
        assertTrue(v.insertComment(3, "name4=field4"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4");
        assertTrue(v.insertComment(4, "name3=field3dup1"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1");
        assertTrue(v.insertComment(5, "name3=field3dup1"));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        assertTrue(v.replaceComment("name3=field3new1", false));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new1", "name1=field1", "name4=field4",
                "name3=field3dup1", "name3=field3dup1");
        assertTrue(v.replaceComment("name3=field3new2", true));
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new2", "name1=field1", "name4=field4");
        v.deleteComment(2);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new2", "name4=field4");
        v.deleteComment(2);
        comments(v, Format.VENDOR_STRING, "name2=field2", "name3=field3new2");
        v.deleteComment(0);
        comments(v, Format.VENDOR_STRING, "name3=field3new2");
        assertTrue(v.setComment(0, "name5=field5"));
        comments(v, Format.VENDOR_STRING, "name5=field5");
        v.setVendorString("name6=field6");
        comments(v, "name6=field6", "name5=field5");
    }

    private CueSheetTrack track(long offset, int number, String isrc) {
        CueSheetTrack t = new CueSheetTrack();
        t.offset = offset;
        t.number = number;
        t.isrc = isrc;
        return t;
    }

    private void trackNumbers(CueSheet c, int... numbers) {
        assertEquals(numbers.length, c.tracks.length);
        for (int i = 0; i < numbers.length; i++) {
            CueSheetTrack t = c.tracks[i];
            assertEquals(numbers[i], t.number);
            assertEquals(numbers[i] == 0 ? 0 : 10L * (numbers[i] - 1), t.offset);
            assertEquals(numbers[i] == 0 ? "" : (char) ('A' + numbers[i] - 1) + "BCDE1234567", t.isrc);
            assertEquals(0, t.type);
            assertFalse(t.preEmphasis);
        }
        cloneEqual(c);
    }

    @Test
    void cueSheet() {
        CueSheetTrack empty = new CueSheetTrack();
        assertEquals(empty.number, empty.copy().number);
        CueSheet c = new CueSheet();
        cloneEqual(c);
        c.resizeTracks(2);
        trackNumbers(c, 0, 0);
        c.resizeTracks(1);
        trackNumbers(c, 0);
        c.resizeTracks(0);
        trackNumbers(c);
        for (boolean copy : new boolean[] {true, false}) {
            c = new CueSheet();
            int base = copy ? 0 : 60, number = copy ? 1 : 7;
            char initial = copy ? 'A' : 'G';
            c.insertTrack(0, track(base, number, initial + "BCDE1234567"), copy);
            trackNumbers(c, number);
            c.insertTrack(0, track(base + 10, number + 1, (char) (initial + 1) + "BCDE1234567"), copy);
            trackNumbers(c, number + 1, number);
            c.insertTrack(1, track(base + 20, number + 2, (char) (initial + 2) + "BCDE1234567"), copy);
            trackNumbers(c, number + 1, number + 2, number);
            c.insertTrack(3, track(base + 30, number + 3, (char) (initial + 3) + "BCDE1234567"), copy);
            trackNumbers(c, number + 1, number + 2, number, number + 3);
            if (copy) {
                c.insertBlankTrack(4);
                trackNumbers(c, 2, 3, 1, 4, 0);
                c.deleteTrack(4);
            }
            c.deleteTrack(2);
            trackNumbers(c, number + 1, number + 2, number + 3);
            c.deleteTrack(2);
            trackNumbers(c, number + 1, number + 2);
            c.deleteTrack(0);
            trackNumbers(c, number + 2);
            c.setTrack(0, track(base + 40, number + 4, (char) (initial + 4) + "BCDE1234567"), copy);
            trackNumbers(c, number + 4);
            assertEquals(base + 40, c.tracks[0].offset);
            assertEquals((char) (initial + 4) + "BCDE1234567", c.tracks[0].isrc);
            if (copy) {
                CueSheetTrack t = c.tracks[0];
                t.resizeIndices(2);
                assertEquals(2, t.indices.length);
                t.resizeIndices(1);
                assertEquals(1, t.indices.length);
                t.resizeIndices(0);
                assertEquals(0, t.indices.length);
                t.insertIndex(0, new CueSheetIndex(0, 1));
                t.insertIndex(0, new CueSheetIndex(10, 2));
                t.insertIndex(1, new CueSheetIndex(20, 3));
                t.insertIndex(3, new CueSheetIndex(30, 4));
                t.insertBlankIndex(4);
                assertArrayEquals(new int[] {2, 3, 1, 4, 0},
                        Arrays.stream(t.indices).mapToInt(CueSheetIndex::getNumber).toArray());
                cloneEqual(c);
                t.deleteIndex(4);
                t.deleteIndex(2);
                t.deleteIndex(2);
                t.deleteIndex(0);
                assertEquals(3, t.indices[0].number);
                assertEquals(20, t.indices[0].offset);
            } else
                assertFalse(c.isLegal(true));
        }
    }

    @Test
    void picture() {
        Picture p = new Picture();
        cloneEqual(p);
        for (boolean copy : new boolean[] {true, false}) {
            p.setMimeType("image/png\t");
            assertFalse(p.isLegal());
            p.setMimeType("image/png");
            assertTrue(p.isLegal());

            p.setDescription("DESCRIPTION");
            assertTrue(p.isLegal());
            p.setData(UpstreamFixtures.bytes("PNGDATA"), copy);
            assertEquals(32 + 9 + 11 + 7, p.getLength());
            cloneEqual(p);
        }
    }
}
