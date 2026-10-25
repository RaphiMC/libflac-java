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

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodersTest {
    @TempDir
    Path temp;

    @TestFactory
    Stream<DynamicTest> encoders() {
        return IntStream.range(0, 8)
                .mapToObj(n -> DynamicTest.dynamicTest((n >= 4 ? "Ogg " : "Native ") + "layer " + n % 4,
                        () -> encode(n % 4, n >= 4)));
    }

    private void encode(int layer, boolean ogg) throws Exception {
        Path path = temp.resolve("encoder-" + layer + "-" + ogg);
        RandomAccessFile f = layer < 3 ? new RandomAccessFile(path.toFile(), "rw") : null;
        try (StreamEncoder e = new StreamEncoder()) {
            e.setCompressionLevel(-1);
            UpstreamFixtures.configure(e, UpstreamFixtures.metadata());
            e.setLimitMinBitrate(true);
            StreamEncoder.WriteCallback write = (enc, b, o, n, s, frame) -> {
                f.write(b, o, n);
                return true;
            };
            StreamEncoder.SeekCallback seek = (enc, p) -> {
                f.seek(p);
                return StreamEncoderSeekStatus.OK;
            };
            StreamEncoder.TellCallback tell = (enc) -> f.getFilePointer();
            StreamEncoder.ReadCallback read = (enc, b, o, n) -> f.read(b, o, n);
            StreamEncoderInitStatus status = switch (layer) {
                case 0 -> ogg ? e.initOggStream(null, write, null, null, (enc, m) -> {
                }) : e.initStream(write, null, null, (enc, m) -> {
                });
                case 1 -> ogg ? e.initOggStream(read, write, seek, tell, null) : e.initStream(write, seek, tell, null);
                case 2 -> ogg ? e.initOggFile(f, (enc, b, s, fr, t) -> {
                }) : e.initFile(f, (enc, b, s, fr, t) -> {
                });
                default -> ogg ? e.initOggFile(path.toString(), (enc, b, s, fr, t) -> {
                }) : e.initFile(path.toString(), (enc, b, s, fr, t) -> {
                });
            };
            assertEquals(StreamEncoderInitStatus.OK, status);
            assertEquals(StreamEncoderState.OK, e.getState());
            assertNotNull(e.getVerifyDecoderState());
            assertNotNull(e.getVerifyDecoderErrorStats());
            assertTrue(e.getVerify());
            assertTrue(e.getStreamableSubset());
            assertFalse(e.getDoMidSideStereo());
            assertFalse(e.getLooseMidSideStereo());
            assertEquals(1, e.getChannels());
            assertEquals(8, e.getBitsPerSample());
            assertEquals(44100, e.getSampleRate());
            assertEquals(576, e.getBlocksize());
            assertEquals(0, e.getMaxLpcOrder());
            /* we asked the encoder to auto select this so we accept anything */
            assertTrue(e.getQlpCoeffPrecision() > 0);
            assertFalse(e.getDoQlpCoeffPrecSearch());
            assertFalse(e.getDoEscapeCoding());
            assertFalse(e.getDoExhaustiveModelSearch());
            assertEquals(0, e.getMinResidualPartitionOrder());
            assertEquals(0, e.getMaxResidualPartitionOrder());
            assertEquals(0, e.getRiceParameterSearchDist());
            assertEquals(0, e.getTotalSamplesEstimate());
            assertTrue(e.getLimitMinBitrate());
            int[] samples = new int[1024];
            for (int i = 0; i < samples.length; i++)
                /* init the dummy sample buffer */
                samples[i] = i & 7;
            assertTrue(e.process(new int[][] {samples}, samples.length));
            assertTrue(e.processInterleaved(samples, samples.length));
            e.finish();
        } finally {
            if (f != null && layer < 2)
                f.close();
        }
    }
}
