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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MetadataManipTest {
    @TempDir
    Path temp;
    private List<StreamMetadata> expected;
    private Path path;

    private Padding pad(int i) {
        return (Padding) expected.get(i);
    }

    /* functions for working with our metadata copy */
    private void insert(StreamMetadata b, int i) {
        expected.add(i, b.copy());
    }

    private void replace(StreamMetadata b, int i) {
        expected.set(i, b.copy());
    }

    private void delete(int i) {
        expected.remove(i);
    }

    private void normalize() {
        /* set the isLast flags */
        for (int i = 0; i < expected.size(); i++)
            expected.get(i).isLast = i == expected.size() - 1;
    }

    private void generate(boolean extras, boolean ogg) throws IOException {
        path = temp.resolve("metadata-" + ogg + ".flac");
        List<StreamMetadata> blocks = new ArrayList<>();
        blocks.add(new VorbisComment());
        if (extras) {
            CueSheet c = new CueSheet();
            c.mediaCatalogNumber = "bogo-MCN";
            c.leadIn = 123;
            c.insertBlankTrack(0);
            c.tracks[0].number = 1;
            c.tracks[0].insertBlankIndex(0);
            blocks.add(c);
            blocks.add(UpstreamFixtures.metadata()[6]);
        }
        blocks.add(new Padding(1234));
        expected = new ArrayList<>();
        expected.add(UpstreamFixtures.streamInfo());
        for (StreamMetadata b : blocks)
            expected.add(b.copy());
        normalize();
        UpstreamFixtures.generate(ogg, path.toString(), 512 * 1024, blocks.toArray(StreamMetadata[]::new));
    }

    private void checkFile(boolean ogg) throws IOException {
        normalize();
        List<StreamMetadata> got = new ArrayList<>();
        /* decoder callbacks for checking the file */
        try (StreamDecoder d = new StreamDecoder()) {
            d.setMetadataRespondAll();
            d.setMd5Checking(true);
            StreamDecoder.WriteCallback write = (dec, f, pcm) -> true;
            /* this version is used when we want to compare to our metadata copy */
            StreamDecoder.MetadataCallback metadata = (dec, b) -> got.add(b.copy());
            StreamDecoder.ErrorCallback error = (dec, status) -> fail(status.toString());
            assertEquals(StreamDecoderInitStatus.OK, ogg ? d.initOggFile(path.toString(), write, metadata, error)
                    : d.initFile(path.toString(), write, metadata, error));
            assertTrue(d.processUntilEndOfStream());
            assertTrue(d.finish());
        }
        assertEquals(expected.size(), got.size());
        for (int i = 0; i < got.size(); i++)
            assertTrue(expected.get(i).isEqual(got.get(i)), "block " + i);
    }

    /* function for comparing our metadata to a MetadataChain */
    private void checkChain(MetadataChain chain) {
        normalize();
        assertEquals(expected.size(), chain.nodes);
        MetadataChain.Node node = chain.head;
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(expected.get(i).isEqual(node.data), "chain block " + i);
            node = node.next;
        }
    }

    private static final IOCallbacks IO = new FileCallbacks(null);

    private void readChain(MetadataChain chain, boolean filename, boolean ogg) throws IOException {
        if (filename)
            assertTrue(ogg ? chain.readOgg(path.toString()) : chain.read(path.toString()));
        else
            try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "r")) {
                IOCallbacks io = new FileCallbacks(f);
                assertTrue(ogg ? chain.readOggWithCallbacks(io) : chain.readWithCallbacks(io));
            }
    }

    private void writeChain(MetadataChain chain, boolean padding, boolean preserve, boolean filename)
            throws IOException {
        if (filename) {
            assertTrue(chain.write(padding, preserve));
            return;
        }
        if (chain.checkIfTempfileNeeded(padding)) {
            Path tmp = Files.createTempFile(temp, "metadata-", ".tmp");
            BasicFileAttributes attrs = preserve ? Files.readAttributes(path, BasicFileAttributes.class) : null;
            try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "r");
                    RandomAccessFile t = new RandomAccessFile(tmp.toFile(), "rw")) {
                assertTrue(chain.writeWithCallbacksAndTempfile(padding, new FileCallbacks(f), new FileCallbacks(t)));
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            if (attrs != null)
                Files.getFileAttributeView(path, BasicFileAttributeView.class)
                        .setTimes(attrs.lastModifiedTime(), attrs.lastAccessTime(), null);
        } else
            try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
                assertTrue(chain.writeWithCallbacks(padding, new FileCallbacks(f)));
            }
    }

    @Test
    void level0() throws Exception {
        generate(true, false);
        checkFile(false);
        StreamInfo info = Metadata.getStreamInfo(path.toString());
        /* check to see if some basic data matches (c.f. generate()) */
        assertEquals(1, info.channels);
        assertEquals(8, info.bitsPerSample);
        assertEquals(44100, info.sampleRate);
        assertEquals(576, info.minBlocksize);
        assertEquals(576, info.maxBlocksize);
        assertEquals(0, Metadata.getTags(path.toString()).comments.length);
        assertEquals(123, Metadata.getCueSheet(path.toString()).leadIn);
        /* check to see if some basic data matches (c.f. generate()) */
        assertEquals(3, Metadata.getPicture(path.toString(), -1, null, null, -1, -1, -1, -1).pictureType);
    }

    @Test
    void level1ReadOnly() throws Exception {
        generate(false, false);
        assertTrue(path.toFile().setWritable(false));
        try (MetadataSimpleIterator it = new MetadataSimpleIterator()) {
            assertTrue(it.init(path.toString(), false, false));
            assertFalse(it.isWritable());
            assertEquals(MetadataType.STREAMINFO, it.getBlockType());
            StreamInfo b = (StreamInfo) it.getBlock();
            /* check to see if some basic data matches (c.f. generate()) */
            assertFalse(b.isLast);
            assertEquals(34, b.getLength());
            assertEquals(1, b.channels);
            assertEquals(8, b.bitsPerSample);
            assertEquals(44100, b.sampleRate);
            assertEquals(576, b.minBlocksize);
            assertEquals(576, b.maxBlocksize);
            assertTrue(it.next());
            assertTrue(it.next());
            assertEquals(MetadataType.PADDING, it.getBlockType());
            assertTrue(it.isLast());
            assertEquals(1234, it.getBlockLength());
            assertFalse(it.next());
            assertTrue(it.prev());
            assertTrue(it.prev());
            assertFalse(it.prev());
            assertFalse(it.setBlock(null, false));
        } finally {
            assertTrue(path.toFile().setWritable(true));
        }
    }

    @Test
    void level1() throws Exception {
        generate(false, false);
        MetadataSimpleIterator iterator = new MetadataSimpleIterator();
        assertTrue(iterator.init(path.toString(), false, false));
        int pos = 0;
        StreamMetadata block = null;
        Application app = null;
        Padding padding = null;
        byte[] data = new byte[1000];
        app = new Application();
        app.id = new byte[] {'d', 'u', 'h', 0};
        padding = new Padding();
        padding.length = 20;
        pos = 0;
        assertFalse(iterator.setBlock(app, false));
        assertEquals(MetadataSimpleIteratorStatus.ILLEGAL_INPUT, iterator.status());
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        padding.length = 25;
        assertTrue(iterator.insertBlockAfter(padding, false));
        insert(padding, ++pos);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        padding.length = 30;
        assertTrue(iterator.insertBlockAfter(padding, false));
        insert(padding, ++pos);
        checkFile(false);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        assertFalse(iterator.deleteBlock(false));
        assertEquals(MetadataSimpleIteratorStatus.ILLEGAL_INPUT, iterator.status());
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.deleteBlock(true));
        pos--;
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.deleteBlock(true));
        pos--;
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        assertTrue(pos == 0);
        /* check to see if some basic data matches (c.f. generate()) */
        block = iterator.getBlock();
        ((StreamInfo) block).sampleRate = 32000;
        replace(block, pos);
        assertTrue(iterator.setBlock(block, false));
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        /* twiddle the id so that our comparison doesn't miss transposition */
        app.id[0] = 'e';
        assertTrue(iterator.insertBlockAfter(app, true));
        insert(app, ++pos);
        pad(pos + 1).length -= (32 / 8) + app.getLength();
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        /* twiddle the id */
        app.id[0] = 'f';
        assertTrue(iterator.setBlock(app, true));
        insert(app, pos);
        pad(pos + 1).length -= (32 / 8) + app.getLength();
        checkFile(false);
        /* twiddle the id */
        app.id[0] = 'g';
        app.setData(Arrays.copyOf(data, data.length), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app, false));
        checkFile(false);
        /* twiddle the id */
        app.id[0] = 'h';
        app.setData(Arrays.copyOf(data, 12), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app, false));
        checkFile(false);
        /* twiddle the id */
        app.id[0] = 'i';
        app.setData(Arrays.copyOf(data, data.length), true);
        replace(app, pos);
        pad(pos + 1).length -= (data.length - 12);
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        /* twiddle the id */
        app.id[0] = 'j';
        app.setData(Arrays.copyOf(data, 23), true);
        replace(app, pos);
        insert(padding, pos + 1);
        pad(pos + 1).length = data.length - 23 - 4;
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        padding.length = 5;
        replace(padding, pos);
        assertTrue(iterator.setBlock(padding, false));
        checkFile(false);
        /* twiddle the id */
        app.id[0] = 'k';
        replace(app, pos);
        assertTrue(iterator.setBlock(app, false));
        checkFile(false);
        padding.length = 27;
        replace(padding, pos);
        assertTrue(iterator.setBlock(padding, false));
        checkFile(false);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        padding.length = 5;
        assertTrue(iterator.insertBlockAfter(padding, false));
        insert(padding, ++pos);
        checkFile(false);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        app.setData(Arrays.copyOf(data, 32), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        app.setData(Arrays.copyOf(data, 60), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        app.setData(Arrays.copyOf(data, 87), true);
        replace(app, pos);
        pad(pos + 1).length = 0;
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        app.setData(Arrays.copyOf(data, 91), true);
        replace(app, pos);
        delete(pos + 1);
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        app.setData(Arrays.copyOf(data, 100), true);
        replace(app, pos);
        delete(pos + 1);
        assertTrue(iterator.setBlock(app, true));
        checkFile(false);
        padding.length = app.getLength();
        replace(padding, pos);
        assertTrue(iterator.setBlock(padding, true));
        checkFile(false);
        assertTrue(iterator.insertBlockAfter(padding, false));
        insert(padding, ++pos);
        checkFile(false);
        padding.length = 5;
        assertTrue(iterator.insertBlockAfter(padding, false));
        insert(padding, ++pos);
        checkFile(false);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        app.setData(Arrays.copyOf(data, 101), true);
        insert(app, ++pos);
        assertTrue(iterator.insertBlockAfter(app, true));
        checkFile(false);
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        app.setData(Arrays.copyOf(data, 97), true);
        insert(app, ++pos);
        assertTrue(iterator.insertBlockAfter(app, true));
        checkFile(false);
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        app.setData(Arrays.copyOf(data, 100), true);
        insert(app, ++pos);
        delete(pos + 1);
        assertTrue(iterator.insertBlockAfter(app, true));
        checkFile(false);
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        app.setData(Arrays.copyOf(data, 96), true);
        insert(app, ++pos);
        pad(pos + 1).length = 0;
        assertTrue(iterator.insertBlockAfter(app, true));
        checkFile(false);
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.deleteBlock(false));
        delete(pos--);
        checkFile(false);
        app.setData(Arrays.copyOf(data, 1), true);
        insert(app, ++pos);
        delete(pos + 1);
        assertTrue(iterator.insertBlockAfter(app, true));
        checkFile(false);
        iterator.close();
    }

    @TestFactory
    Stream<DynamicTest> level2() {
        return IntStream.range(0, 4)
                .mapToObj(n -> DynamicTest.dynamicTest(
                        "level2 " + (n % 2 == 0 ? "filename" : "callbacks") + (n >= 2 ? " Ogg" : " native"),
                        () -> level2(n % 2 == 0, n >= 2)));
    }

    private void level2(boolean filename, boolean ogg) throws Exception {
        generate(false, ogg);
        MetadataChain chain = new MetadataChain();
        assertTrue(path.toFile().setWritable(false));
        try {
            readChain(chain, filename, ogg);
            checkChain(chain);
            checkFile(ogg);
        } finally {
            assertTrue(path.toFile().setWritable(true));
        }
        if (ogg)
            return;
        MetadataIterator iterator = null;
        StreamMetadata block = null;
        Application app = null;
        Padding padding = null;
        int pos = 0;
        byte[] data = new byte[2000];
        iterator = new MetadataIterator();
        pos = 0;
        iterator.init(chain);
        block = iterator.getBlock();
        assertTrue(block.getType() == MetadataType.STREAMINFO);
        ((StreamInfo) block).sampleRate = 32000;
        replace(block, pos);
        writeChain(chain, false, true, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        block = iterator.getBlock();
        app = new Application();
        app.id = new byte[] {'d', 'u', 'h', 0};
        app.setData(Arrays.copyOf(data, block.getLength() - (32 / 8)), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, false, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 26), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, false, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 28), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, false, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 36), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, false, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 33), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, true, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        padding = new Padding();
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 29), true);
        replace(app, pos);
        padding.length = 0;
        insert(padding, pos + 1);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, true, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 16), true);
        replace(app, pos);
        pad(pos + 1).length = 13;
        assertTrue(iterator.setBlock(app));
        writeChain(chain, true, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 50), true);
        replace(app, pos);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, true, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 56), true);
        replace(app, pos);
        pad(pos + 1).length -= (56 - 50);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, true, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        app = (Application) expected.get(pos).copy();
        app.setData(Arrays.copyOf(data, 67), true);
        replace(app, pos);
        delete(pos + 1);
        assertTrue(iterator.setBlock(app));
        writeChain(chain, true, false, filename);
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        checkFile(false);
        assertTrue(iterator.prev());
        pos--;
        assertTrue(iterator.prev());
        pos--;
        padding = new Padding();
        padding.length = 30;
        assertFalse(iterator.insertBlockBefore(padding));
        assertTrue(iterator.next());
        pos++;
        insert(padding, ++pos);
        assertTrue(iterator.insertBlockAfter(padding));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        padding = (Padding) expected.get(pos).copy();
        padding.length = 17;
        insert(padding, pos);
        assertTrue(iterator.insertBlockBefore(padding));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        padding = (Padding) expected.get(pos).copy();
        padding.length = 0;
        insert(padding, pos);
        assertTrue(iterator.insertBlockBefore(padding));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        padding = (Padding) expected.get(2).copy();
        padding.length = 57;
        insert(padding, ++pos);
        assertTrue(iterator.insertBlockAfter(padding));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        padding = (Padding) expected.get(2).copy();
        padding.length = 99;
        insert(padding, pos);
        assertTrue(iterator.insertBlockBefore(padding));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        pos = 0;
        chain.mergePadding();
        pad(2).length += (4 + pad(3).length);
        pad(2).length += (4 + pad(4).length);
        pad(6).length += (4 + pad(7).length);
        delete(7);
        delete(4);
        delete(3);
        writeChain(chain, true, false, filename);
        checkChain(chain);
        checkFile(false);
        chain.sortPadding();
        pad(4).length += (4 + pad(2).length);
        delete(2);
        writeChain(chain, true, false, filename);
        checkChain(chain);
        checkFile(false);
        iterator = new MetadataIterator();
        pos = 0;
        iterator.init(chain);
        assertTrue(iterator.next());
        pos++;
        assertTrue(iterator.next());
        pos++;
        padding = new Padding();
        padding.length = 71;
        replace(padding, pos--);
        assertTrue(iterator.deleteBlock(true));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        assertTrue(iterator.next());
        pos++;
        delete(pos--);
        assertTrue(iterator.deleteBlock(false));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        assertTrue(iterator.next());
        pos++;
        padding = new Padding();
        padding.length = 219;
        replace(padding, pos--);
        assertTrue(iterator.deleteBlock(true));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        assertTrue(iterator.next());
        pos++;
        delete(pos--);
        assertTrue(iterator.deleteBlock(false));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        assertTrue(iterator.prev());
        pos--;
        assertFalse(iterator.deleteBlock(false));
        checkChain(chain);
        assertTrue(expected.get(pos).isEqual(iterator.getBlock()));
        pos = 0;
        chain.mergePadding();
        writeChain(chain, false, false, filename);
        checkChain(chain);
        checkFile(false);
        chain.sortPadding();
        writeChain(chain, false, false, filename);
        checkChain(chain);
        checkFile(false);
    }

    @Test
    void mismatchedReadWrite() throws Exception {
        generate(false, false);
        MetadataChain c = new MetadataChain();
        assertTrue(c.read(path.toString()));
        assertFalse(c.writeWithCallbacks(false, IO));
        assertEquals(MetadataChainStatus.READ_WRITE_MISMATCH, c.status());
        assertTrue(c.read(path.toString()));
        assertFalse(c.writeWithCallbacksAndTempfile(false, IO, IO));
        assertEquals(MetadataChainStatus.READ_WRITE_MISMATCH, c.status());
        readChain(c, false, false);
        assertFalse(c.write(false, false));
        assertEquals(MetadataChainStatus.READ_WRITE_MISMATCH, c.status());
        readChain(c, false, false);
        assertFalse(c.checkIfTempfileNeeded(false));
        assertFalse(c.writeWithCallbacksAndTempfile(false, IO, IO));
        assertEquals(MetadataChainStatus.WRONG_WRITE_CALL, c.status());
        readChain(c, false, false);
        MetadataIterator it = new MetadataIterator();
        it.init(c);
        assertTrue(it.next());
        assertTrue(it.deleteBlock(false));
        assertTrue(c.checkIfTempfileNeeded(false));
        assertFalse(c.writeWithCallbacks(false, IO));
        assertEquals(MetadataChainStatus.WRONG_WRITE_CALL, c.status());
    }

    private static final class FileCallbacks implements IOCallbacks {
        private final RandomAccessFile file;

        FileCallbacks(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public int read(byte[] b, int o, int n) throws IOException {
            return file.read(b, o, n);
        }

        @Override
        public void write(byte[] b, int o, int n) throws IOException {
            file.write(b, o, n);
        }

        @Override
        public void seek(long p) throws IOException {
            file.seek(p);
        }

        @Override
        public long tell() throws IOException {
            return file.getFilePointer();
        }
    }
}
