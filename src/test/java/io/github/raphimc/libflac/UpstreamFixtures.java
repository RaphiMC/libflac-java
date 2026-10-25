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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class UpstreamFixtures {
    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /* min/maxFramesize and md5sum don't get written at first, so we have to leave them 0 */
    static StreamInfo streamInfo() {
        StreamInfo s = new StreamInfo();
        s.minBlocksize = s.maxBlocksize = 576;
        s.sampleRate = 44100;
        s.channels = 1;
        s.bitsPerSample = 8;
        return s;
    }

    /*
     * most of the actual numbers and data in the blocks don't matter,
     *         we just want to make sure the decoder parses them correctly
     */
    static StreamMetadata[] metadata() {
        VorbisComment v = new VorbisComment();
        v.comments = new String[] {"ab=cd", ""};
        Padding p = new Padding(1234);
        SeekTable st = new SeekTable();
        st.points = new SeekPoint[] {new SeekPoint(0, 0, 576), new SeekPoint(-1, 1000, 576)};
        Application a = new Application();
        a.id = bytes("This");
        a.data = new byte[] {(byte) 0xf0, (byte) 0xe1, (byte) 0xd2, (byte) 0xc3};
        Application b = new Application();
        b.id = bytes("Here");
        /* cuesheet guts */
        CueSheet c = new CueSheet();
        c.mediaCatalogNumber = "jC";
        c.leadIn = 2 * 44100;
        c.isCd = true;
        c.resizeTracks(3);
        c.tracks[0].offset = 0;
        c.tracks[0].number = 1;
        c.tracks[0].isrc = "ACBDE1234567";
        c.tracks[0].preEmphasis = true;
        c.tracks[0].indices = new CueSheetIndex[] {new CueSheetIndex(0, 0), new CueSheetIndex(123 * 588, 1)};
        c.tracks[1].offset = 1234 * 588;
        c.tracks[1].number = 2;
        c.tracks[1].isrc = "ACBDE7654321";
        c.tracks[1].type = 1;
        c.tracks[1].indices = new CueSheetIndex[] {new CueSheetIndex(0, 1)};
        c.tracks[2].offset = 12345 * 588;
        c.tracks[2].number = 170;
        Picture pic = new Picture();
        pic.pictureType = 3;
        pic.mimeType = "image/jpeg";
        pic.description = "desc";
        pic.width = pic.height = 300;
        pic.depth = 24;
        pic.data = bytes("SOMEJPEGDATA");
        UnknownMetadata u = new UnknownMetadata(126);
        u.isLast = true;
        u.data = new byte[] {(byte) 0xfe, (byte) 0xdc, (byte) 0xba, (byte) 0x98, (byte) 0xf0, (byte) 0xe1, (byte) 0xd2,
                (byte) 0xc3};
        return new StreamMetadata[] {v, p, st, a, b, c, pic, u};
    }

    static void configure(StreamEncoder e, StreamMetadata[] metadata) {
        e.setOggSerialNumber(12345);
        e.setVerify(true);
        e.setStreamableSubset(true);
        e.setDoMidSideStereo(false);
        e.setLooseMidSideStereo(false);
        e.setChannels(1);
        e.setBitsPerSample(8);
        e.setSampleRate(44100);
        e.setBlocksize(576);
        e.setMaxLpcOrder(0);
        e.setQlpCoeffPrecision(0);
        e.setDoQlpCoeffPrecSearch(false);
        e.setDoEscapeCoding(false);
        e.setDoExhaustiveModelSearch(false);
        e.setMinResidualPartitionOrder(0);
        e.setMaxResidualPartitionOrder(0);
        e.setRiceParameterSearchDist(0);
        e.setTotalSamplesEstimate(0);
        e.setMetadata(metadata);
    }

    static void generate(boolean ogg, String filename, int length, StreamMetadata[] metadata) throws IOException {
        try (StreamEncoder e = new StreamEncoder(); OutputStream out = new FileOutputStream(filename)) {
            configure(e, metadata);
            StreamEncoder.WriteCallback write = (enc, b, o, n, s, f) -> {
                out.write(b, o, n);
                return true;
            };
            StreamEncoderInitStatus status = ogg ? e.initOggStream(null, write, null, null, null)
                    : e.initStream(write, null, null, null);
            if (status != StreamEncoderInitStatus.OK)
                throw new IOException(status.toString());
            /* init the dummy sample buffer */
            int[] samples = new int[1024];
            for (int i = 0; i < samples.length; i++)
                samples[i] = i & 7;
            while (length > 0) {
                int n = Math.min(length, samples.length);
                if (!e.processInterleaved(samples, n))
                    throw new IOException(e.getState().toString());
                length -= n;
            }
            e.finish();
        }
    }
}
