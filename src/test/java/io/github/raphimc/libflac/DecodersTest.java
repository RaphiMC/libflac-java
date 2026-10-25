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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DecodersTest {
    @TempDir
    Path temp;

    @TestFactory
    Stream<DynamicTest> decoders() {
        return IntStream.range(0, 8)
                .mapToObj(n -> DynamicTest.dynamicTest((n >= 4 ? "Ogg " : "Native ") + "layer " + n % 4,
                        () -> decode(n % 4, n >= 4)));
    }

    private StreamDecoderInitStatus init(StreamDecoder d, Path path, RandomAccessFile f, int layer, boolean ogg,
            List<StreamMetadata> received, boolean[] ignoreErrors) throws IOException {
        StreamDecoder.ReadCallback read = (dec, b, o, n) -> f.read(b, o, n);
        StreamDecoder.SeekCallback seek = (dec, p) -> {
            f.seek(p);
            return StreamDecoderSeekStatus.OK;
        };
        StreamDecoder.TellCallback tell = (dec) -> f.getFilePointer();
        StreamDecoder.LengthCallback length = (dec) -> f.length();
        StreamDecoder.EofCallback eof = (dec) -> f.getFilePointer() >= f.length();
        StreamDecoder.WriteCallback write = (dec, frame, pcm) -> {
            assertEquals(1, frame.header.channels);
            assertNotNull(pcm[0]);
            return true;
        };
        StreamDecoder.MetadataCallback metadata = (dec, b) -> received.add(b.copy());
        StreamDecoder.ErrorCallback error = (dec, status) -> {
            if (!ignoreErrors[0])
                fail(status.toString());
        };
        return switch (layer) {
            case 0 -> ogg ? d.initOggStream(read, null, null, null, null, write, metadata, error)
                    : d.initStream(read, null, null, null, null, write, metadata, error);
            case 1 -> ogg ? d.initOggStream(read, seek, tell, length, eof, write, metadata, error)
                    : d.initStream(read, seek, tell, length, eof, write, metadata, error);
            case 2 -> ogg ? d.initOggFile(f, write, metadata, error) : d.initFile(f, write, metadata, error);
            default -> ogg ? d.initOggFile(path.toString(), write, metadata, error)
                    : d.initFile(path.toString(), write, metadata, error);
        };
    }

    private void decode(int layer, boolean ogg) throws Exception {
        Path path = temp.resolve("decoder-" + layer + "-" + ogg);
        StreamMetadata[] blocks = UpstreamFixtures.metadata();
        UpstreamFixtures.generate(ogg, path.toString(), 512 * 1024, blocks);
        List<StreamMetadata> received = new ArrayList<>();
        boolean[] ignoreErrors = {false};
        RandomAccessFile f = layer < 3 ? new RandomAccessFile(path.toFile(), "r") : null;
        try (StreamDecoder d = new StreamDecoder()) {
            if (ogg)
                d.setOggSerialNumber(12345);
            d.setMd5Checking(true);
            assertEquals(StreamDecoderInitStatus.OK, init(d, path, f, layer, ogg, received, ignoreErrors));
            assertNotNull(d.getState());
            assertTrue(d.getMd5Checking());
            assertTrue(d.processUntilEndOfMetadata());
            assertTrue(d.processSingle());
            assertTrue(d.skipSingleFrame());
            if (layer < 2) {
                d.flush();
                ignoreErrors[0] = true;
                assertTrue(d.processSingle());
                ignoreErrors[0] = false;
            }
            assertEquals(layer != 0, d.seekAbsolute(0));
            assertTrue(d.processUntilEndOfStream());
            assertEquals(layer != 0, d.seekAbsolute(0));
            assertEquals(1, d.getChannels());
            assertEquals(8, d.getBitsPerSample());
            assertEquals(44100, d.getSampleRate());
            /* value could be anything since we're at the last block, so accept any reasonable answer */
            assertTrue(d.getBlocksize() > 0);
            assertNotNull(d.getChannelAssignment());
            if (layer < 2) {
                assertTrue(d.reset());
                if (layer == 0)
                    f.seek(0);
                received.clear();
                assertTrue(d.processUntilEndOfStream());
            }
            assertTrue(d.finish());
            /* for LAYER_FILE, StreamDecoder.finish() closes the file */
            if (f != null && layer == 2)
                f = null;
            for (int filter = 0; filter < 12; filter++) {
                received.clear();
                if (layer < 2)
                    f.seek(0);
                /* for StreamEncoder.initFile(), the StreamEncoder.finish() closes the file so we have to keep re-opening: */
                else if (layer == 2)
                    f = new RandomAccessFile(path.toFile(), "r");
                List<Integer> expected = new ArrayList<>();
                List<Integer> all = new ArrayList<>(List.of(0, 4, 1, 3, 2, 2, 5, 6, 126));
                if (ogg)
                    all.remove(Integer.valueOf(3));
                byte[] id1 = UpstreamFixtures.bytes("This"), id2 = UpstreamFixtures.bytes("Here");
                switch (filter) {
                    /* respond all */
                    case 0 -> {
                        d.setMetadataRespondAll();
                        expected.addAll(all);
                    }
                    /* ignore all */
                    case 1 -> d.setMetadataIgnoreAll();
                    /* respond all, ignore VORBIS_COMMENT */
                    case 2 -> {
                        d.setMetadataRespondAll();
                        d.setMetadataIgnore(MetadataType.VORBIS_COMMENT);
                        expected.addAll(all);
                        expected.remove(Integer.valueOf(4));
                    }
                    /* respond all, ignore APPLICATION */
                    case 3 -> {
                        d.setMetadataRespondAll();
                        d.setMetadataIgnore(MetadataType.APPLICATION);
                        expected.addAll(all);
                        expected.removeIf(x -> x == 2);
                    }
                    /* respond all, ignore APPLICATION id of app#1 */
                    case 4 -> {
                        d.setMetadataRespondAll();
                        d.setMetadataIgnoreApplication(id1);
                        expected.addAll(all);
                        expected.remove(Integer.valueOf(2));
                    }
                    /* respond all, ignore APPLICATION id of app#1 & app#2 */
                    case 5 -> {
                        d.setMetadataRespondAll();
                        d.setMetadataIgnoreApplication(id1);
                        d.setMetadataIgnoreApplication(id2);
                        expected.addAll(all);
                        expected.removeIf(x -> x == 2);
                    }
                    /* ignore all, respond VORBIS_COMMENT */
                    case 6 -> {
                        d.setMetadataIgnoreAll();
                        d.setMetadataRespond(MetadataType.VORBIS_COMMENT);
                        expected.add(4);
                    }
                    /* ignore all, respond APPLICATION */
                    case 7 -> {
                        d.setMetadataIgnoreAll();
                        d.setMetadataRespond(MetadataType.APPLICATION);
                        expected.addAll(List.of(2, 2));
                    }
                    /* ignore all, respond APPLICATION id of app#1 */
                    case 8 -> {
                        d.setMetadataIgnoreAll();
                        d.setMetadataRespondApplication(id1);
                        expected.add(2);
                    }
                    /* ignore all, respond APPLICATION id of app#1 & app#2 */
                    case 9 -> {
                        d.setMetadataIgnoreAll();
                        d.setMetadataRespondApplication(id1);
                        d.setMetadataRespondApplication(id2);
                        expected.addAll(List.of(2, 2));
                    }
                    /* respond all, ignore APPLICATION, respond APPLICATION id of app#1 */
                    case 10 -> {
                        d.setMetadataRespondAll();
                        d.setMetadataIgnore(MetadataType.APPLICATION);
                        d.setMetadataRespondApplication(id1);
                        expected.addAll(all);
                        expected.remove(Integer.valueOf(2));
                    }
                    /* ignore all, respond APPLICATION, ignore APPLICATION id of app#1 */
                    case 11 -> {
                        d.setMetadataIgnoreAll();
                        d.setMetadataRespond(MetadataType.APPLICATION);
                        d.setMetadataIgnoreApplication(id1);
                        expected.add(2);
                    }
                }
                assertEquals(StreamDecoderInitStatus.OK, init(d, path, f, layer, ogg, received, ignoreErrors));
                assertTrue(d.processUntilEndOfStream());
                assertEquals(expected, received.stream().map(StreamMetadata::getTypeCode).toList());
                for (StreamMetadata b : received)
                    if (!(b instanceof StreamInfo)) {
                        StreamMetadata expectedBlock = Arrays.stream(blocks)
                                .filter(x -> x.typeCode == b.typeCode
                                        && (!(b instanceof Application a) || Arrays.equals(a.id, ((Application) x).id)))
                                .findFirst()
                                .orElseThrow();
                        assertTrue(expectedBlock.isEqual(b), "metadata type " + b.typeCode);
                    }
                assertTrue(d.finish());
                if (layer == 2)
                    f = null;
            }
        } finally {
            if (f != null && layer < 2)
                f.close();
        }
    }
}
