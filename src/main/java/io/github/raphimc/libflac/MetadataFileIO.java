/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2001-2009  Josh Coalson
 * Copyright (C) 2011-2023  Xiph.Org Foundation
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Xiph.org Foundation nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.github.raphimc.libflac;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class MetadataFileIO {
    /**
     * Vorbis comment entry structure used in VORBIS_COMMENT blocks.  (c.f. <A
     * HREF="https://xiph.org/flac/format.html#metadata_block_vorbis_comment">format specification</A>)
     */
    private record VorbisCommentEntry(int length, String entry) {
    }

    private static final int id_bytes = Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE;
    private static final int entry_length_len = Format.STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN / Byte.SIZE;
    private static final int num_comments_len = Format.STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN / Byte.SIZE;

    private MetadataFileIO() {
    }

    private static void packUint32(int val, byte[] b, int offset, int bytes) {
        offset += bytes;
        for (int i = 0; i < bytes; i++) {
            b[--offset] = (byte) (val & 0xff);
            val >>>= Byte.SIZE;
        }
    }

    private static void packUint32LittleEndian(int val, byte[] b, int offset, int bytes) {
        for (int i = 0; i < bytes; i++) {
            b[offset++] = (byte) (val & 0xff);
            val >>>= Byte.SIZE;
        }
    }

    private static void packUint64(long val, byte[] b, int offset, int bytes) {
        offset += bytes;
        for (int i = 0; i < bytes; i++) {
            b[--offset] = (byte) (val & 0xff);
            val >>>= Byte.SIZE;
        }
    }

    private static int unpackUint32(byte[] b, int offset, int bytes) {
        int ret = 0;
        for (int i = 0; i < bytes; i++)
            ret = (ret << Byte.SIZE) | (b[offset++] & 0xff);
        return ret;
    }

    private static int unpackUint32LittleEndian(byte[] b, int offset, int bytes) {
        int ret = 0;
        offset += bytes;
        for (int i = 0; i < bytes; i++)
            ret = (ret << Byte.SIZE) | (b[--offset] & 0xff);
        return ret;
    }

    private static long unpackUint64(byte[] b, int offset, int bytes) {
        long ret = 0;
        for (int i = 0; i < bytes; i++)
            ret = (ret << Byte.SIZE) | (b[offset++] & 0xffL);
        return ret;
    }

    static void writeHeader(IOCallbacks callbacks, StreamMetadata block) throws IOException {
        int length = block.getLength();
        if (Integer.compareUnsigned(length, 1 << Format.STREAM_METADATA_LENGTH_LEN) >= 0)
            throw new Failure(MetadataSimpleIteratorStatus.WRITE_ERROR);
        byte[] buffer = new byte[Format.STREAM_METADATA_HEADER_LENGTH];
        buffer[0] = (byte) ((block.isLast ? 0x80 : 0) | block.typeCode);
        packUint32(length, buffer, 1, 3);
        callbacks.write(buffer, 0, Format.STREAM_METADATA_HEADER_LENGTH);
    }

    static void writeData(IOCallbacks callbacks, StreamMetadata block) throws IOException {
        if (block instanceof StreamInfo info)
            writeStreamInfo(callbacks, info);
        else if (block instanceof Padding padding)
            writePadding(callbacks, padding, padding.length);
        else if (block instanceof Application application)
            writeApplication(callbacks, application);
        else if (block instanceof SeekTable table)
            writeSeekTable(callbacks, table);
        else if (block instanceof VorbisComment comment)
            writeVorbisComment(callbacks, comment);
        else if (block instanceof CueSheet cueSheet)
            writeCueSheet(callbacks, cueSheet);
        else if (block instanceof Picture picture)
            writePicture(callbacks, picture);
        else
            writeUnknown(callbacks, (UnknownMetadata) block);
    }

    private static void writeStreamInfo(IOCallbacks callbacks, StreamInfo block) throws IOException {
        byte[] buffer = new byte[Format.STREAM_METADATA_STREAMINFO_LENGTH];
        int channels1 = block.channels - 1;
        int bps1 = block.bitsPerSample - 1;
        /*
         * we are using hardcoded numbers for simplicity but we should
         * probably eventually write a bit-level packer and use the
         * _STREAMINFO_ constants.
         */
        packUint32(block.minBlocksize, buffer, 0, 2);
        packUint32(block.maxBlocksize, buffer, 2, 2);
        packUint32(block.minFramesize, buffer, 4, 3);
        packUint32(block.maxFramesize, buffer, 7, 3);
        buffer[10] = (byte) (block.sampleRate >>> 12);
        buffer[11] = (byte) (block.sampleRate >>> 4);
        buffer[12] = (byte) (((block.sampleRate & 0x0f) << 4) | (channels1 << 1) | (bps1 >>> 4));
        buffer[13] = (byte) (((bps1 & 0x0f) << 4) | ((block.totalSamples >>> 32) & 0x0f));
        packUint32((int) block.totalSamples, buffer, 14, 4);
        System.arraycopy(block.md5sum, 0, buffer, 18, 16);
        callbacks.write(buffer, 0, Format.STREAM_METADATA_STREAMINFO_LENGTH);
    }

    private static void writePadding(IOCallbacks callbacks, Padding block, int blockLength) throws IOException {
        byte[] buffer = new byte[1024];
        while (blockLength >= 1024) {
            callbacks.write(buffer, 0, 1024);
            blockLength -= 1024;
        }
        callbacks.write(buffer, 0, blockLength);
    }

    private static void writeApplication(IOCallbacks callbacks, Application block) throws IOException {
        callbacks.write(block.id, 0, id_bytes);
        callbacks.write(block.data, 0, block.data.length);
    }

    private static void writeSeekTable(IOCallbacks callbacks, SeekTable block) throws IOException {
        byte[] buffer = new byte[Format.STREAM_METADATA_SEEKPOINT_LENGTH];
        for (SeekPoint point : block.points) {
            packUint64(point.sampleNumber, buffer, 0, Long.BYTES);
            packUint64(point.streamOffset, buffer, Long.BYTES, Long.BYTES);
            packUint32(point.frameSamples, buffer, 2 * Long.BYTES, Short.BYTES);
            callbacks.write(buffer, 0, Format.STREAM_METADATA_SEEKPOINT_LENGTH);
        }
    }

    private static void writeVorbisComment(IOCallbacks callbacks, VorbisComment block) throws IOException {
        byte[] buffer = new byte[Integer.BYTES];
        byte[] entry = block.vendorString.getBytes(StandardCharsets.UTF_8);
        packUint32LittleEndian(entry.length, buffer, 0, entry_length_len);
        callbacks.write(buffer, 0, entry_length_len);
        callbacks.write(entry, 0, entry.length);
        packUint32LittleEndian(block.comments.length, buffer, 0, num_comments_len);
        callbacks.write(buffer, 0, num_comments_len);
        for (int i = 0; i < block.comments.length; i++) {
            entry = block.comments[i].getBytes(StandardCharsets.UTF_8);
            packUint32LittleEndian(entry.length, buffer, 0, entry_length_len);
            callbacks.write(buffer, 0, entry_length_len);
            callbacks.write(entry, 0, entry.length);
        }
    }

    private static void writeCueSheet(IOCallbacks callbacks, CueSheet block) throws IOException {
        byte[] buffer = new byte[1024];
        int len = Format.STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN / Byte.SIZE;
        callbacks.write(Arrays.copyOf(block.mediaCatalogNumber.getBytes(StandardCharsets.UTF_8), len), 0, len);
        len = Format.STREAM_METADATA_CUESHEET_LEAD_IN_LEN / Byte.SIZE;
        packUint64(block.leadIn, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        len = (Format.STREAM_METADATA_CUESHEET_IS_CD_LEN + Format.STREAM_METADATA_CUESHEET_RESERVED_LEN) / Byte.SIZE;
        Arrays.fill(buffer, 0, len, (byte) 0);
        if (block.isCd)
            buffer[0] |= (byte) 0x80;
        callbacks.write(buffer, 0, len);
        len = Format.STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN / Byte.SIZE;
        packUint32(block.tracks.length, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        for (CueSheetTrack track : block.tracks) {
            len = Format.STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN / Byte.SIZE;
            packUint64(track.offset, buffer, 0, len);
            callbacks.write(buffer, 0, len);
            len = Format.STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN / Byte.SIZE;
            packUint32(track.number, buffer, 0, len);
            callbacks.write(buffer, 0, len);
            len = Format.STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN / Byte.SIZE;
            callbacks.write(Arrays.copyOf(track.isrc.getBytes(StandardCharsets.UTF_8), len), 0, len);
            len = (Format.STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN
                    + Format.STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN
                    + Format.STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) / Byte.SIZE;
            Arrays.fill(buffer, 0, len, (byte) 0);
            buffer[0] = (byte) ((track.type << 7) | (track.preEmphasis ? 0x40 : 0));
            callbacks.write(buffer, 0, len);
            len = Format.STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN / Byte.SIZE;
            packUint32(track.indices.length, buffer, 0, len);
            callbacks.write(buffer, 0, len);
            for (CueSheetIndex indx : track.indices) {
                len = Format.STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN / Byte.SIZE;
                packUint64(indx.offset, buffer, 0, len);
                callbacks.write(buffer, 0, len);
                len = Format.STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN / Byte.SIZE;
                packUint32(indx.number, buffer, 0, len);
                callbacks.write(buffer, 0, len);
                len = Format.STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN / Byte.SIZE;
                Arrays.fill(buffer, 0, len, (byte) 0);
                callbacks.write(buffer, 0, len);
            }
        }
    }

    private static void writePicture(IOCallbacks callbacks, Picture block) throws IOException {
        byte[] buffer = new byte[Integer.BYTES];
        int len = Format.STREAM_METADATA_PICTURE_TYPE_LEN / Byte.SIZE;
        packUint32(block.pictureType, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        len = Format.STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN / Byte.SIZE;
        byte[] mimeType = block.mimeType.getBytes(StandardCharsets.UTF_8);
        packUint32(mimeType.length, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        callbacks.write(mimeType, 0, mimeType.length);
        len = Format.STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN / Byte.SIZE;
        byte[] description = block.description.getBytes(StandardCharsets.UTF_8);
        packUint32(description.length, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        callbacks.write(description, 0, description.length);
        len = Format.STREAM_METADATA_PICTURE_WIDTH_LEN / Byte.SIZE;
        packUint32(block.width, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        len = Format.STREAM_METADATA_PICTURE_HEIGHT_LEN / Byte.SIZE;
        packUint32(block.height, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        len = Format.STREAM_METADATA_PICTURE_DEPTH_LEN / Byte.SIZE;
        packUint32(block.depth, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        len = Format.STREAM_METADATA_PICTURE_COLORS_LEN / Byte.SIZE;
        packUint32(block.colors, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        len = Format.STREAM_METADATA_PICTURE_DATA_LENGTH_LEN / Byte.SIZE;
        packUint32(block.data.length, buffer, 0, len);
        callbacks.write(buffer, 0, len);
        callbacks.write(block.data, 0, block.data.length);
    }

    private static void writeUnknown(IOCallbacks callbacks, UnknownMetadata block) throws IOException {
        callbacks.write(block.data, 0, block.data.length);
    }

    static final class FileCallbacks implements IOCallbacks {
        private final RandomAccessFile file;

        FileCallbacks(RandomAccessFile file) {
            this.file = file;
        }

        /**
         * Signature for the read callback.
         *
         * @return The number of bytes read, or -1 at end-of-stream.
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return file.read(buffer, offset, length);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            file.write(buffer, offset, length);
        }

        @Override
        public void seek(long offset) throws IOException {
            file.seek(offset);
        }

        @Override
        public long tell() throws IOException {
            return file.getFilePointer();
        }

        @Override
        public boolean eof() throws IOException {
            return file.getFilePointer() >= file.length();
        }
    }

    record Header(boolean isLast, int type, int length) {
    }

    static final class Failure extends IOException {
        final MetadataSimpleIteratorStatus status;

        Failure(MetadataSimpleIteratorStatus status) {
            super(status.name());
            this.status = status;
        }
    }

    static final class Reader {
        private final IOCallbacks callbacks;
        private long position;
        private MetadataSimpleIteratorStatus failureStatus = MetadataSimpleIteratorStatus.READ_ERROR;

        Reader(IOCallbacks callbacks, long position) {
            this.callbacks = callbacks;
            this.position = position;
        }

        long position() {
            return position;
        }

        MetadataSimpleIteratorStatus failureStatus() {
            return failureStatus;
        }

        Header readHeader() throws IOException {
            byte[] rawHeader = read(Format.STREAM_METADATA_HEADER_LENGTH);
            /*
             * Note that we don't check:
             *    if(type >= MetadataType.UNDEFINED.ordinal())
             * we just will read in an opaque block
             */
            return new Header((rawHeader[0] & 0x80) != 0, rawHeader[0] & 0x7f, unpackUint32(rawHeader, 1, 3));
        }

        StreamMetadata readData(Header header) throws IOException {
            int length = header.length;
            StreamMetadata block;
            switch (header.type) {
                case 0 -> block = readStreamInfo();
                case 1 -> block = readPadding(length);
                case 2 -> block = readApplication(length);
                case 3 -> block = readSeekTable(length);
                case 4 -> block = readVorbisComment(length);
                case 5 -> block = readCueSheet();
                case 6 -> block = readPicture();
                default -> block = readUnknown(header.type, length);
            }
            block.isLast = header.isLast;
            return block;
        }

        private StreamInfo readStreamInfo() throws IOException {
            byte[] buffer = read(Format.STREAM_METADATA_STREAMINFO_LENGTH);
            StreamInfo block = new StreamInfo();
            int b = 0;
            /*
             * we are using hardcoded numbers for simplicity but we should
             * probably eventually write a bit-level unpacker and use the
             * _STREAMINFO_ constants.
             */
            block.minBlocksize = unpackUint32(buffer, b, 2);
            b += 2;
            block.maxBlocksize = unpackUint32(buffer, b, 2);
            b += 2;
            block.minFramesize = unpackUint32(buffer, b, 3);
            b += 3;
            block.maxFramesize = unpackUint32(buffer, b, 3);
            b += 3;
            block.sampleRate = (unpackUint32(buffer, b, 2) << 4) | ((buffer[b + 2] & 0xf0) >>> 4);
            block.channels = ((buffer[b + 2] & 0x0e) >>> 1) + 1;
            block.bitsPerSample = (((buffer[b + 2] & 0x01) << 4) | ((buffer[b + 3] & 0xf0) >>> 4)) + 1;
            block.totalSamples = ((long) (buffer[b + 3] & 0x0f) << 32) | unpackUint64(buffer, b + 4, 4);
            System.arraycopy(buffer, b + 8, block.md5sum, 0, 16);
            return block;
        }

        private Padding readPadding(int blockLength) throws IOException {
            /* nothing to do; we don't care about reading the padding bytes */
            skip(blockLength);
            return new Padding(blockLength);
        }

        private Application readApplication(int blockLength) throws IOException {
            Application block = new Application();
            block.id = read(id_bytes);
            if (blockLength < id_bytes)
                throw new Failure(MetadataSimpleIteratorStatus.READ_ERROR);
            if (blockLength > id_bytes)
                block.data = read(blockLength - id_bytes);
            return block;
        }

        private SeekTable readSeekTable(int blockLength) throws IOException {
            if (blockLength % Format.STREAM_METADATA_SEEKPOINT_LENGTH != 0)
                throw new Failure(MetadataSimpleIteratorStatus.BAD_METADATA);
            SeekTable block = new SeekTable();
            block.resizePoints(blockLength / Format.STREAM_METADATA_SEEKPOINT_LENGTH);
            for (SeekPoint point : block.points) {
                byte[] buffer = read(Format.STREAM_METADATA_SEEKPOINT_LENGTH);
                point.sampleNumber = unpackUint64(buffer, 0, Long.BYTES);
                point.streamOffset = unpackUint64(buffer, Long.BYTES, Long.BYTES);
                point.frameSamples = unpackUint32(buffer, 2 * Long.BYTES, Short.BYTES);
            }
            return block;
        }

        private VorbisCommentEntry readVorbisCommentEntry(int maxLength) throws IOException {
            if (maxLength < entry_length_len)
                throw new Failure(MetadataSimpleIteratorStatus.BAD_METADATA);
            int length = unpackUint32LittleEndian(read(entry_length_len), 0, entry_length_len);
            if (Integer.compareUnsigned(length, maxLength - entry_length_len) > 0)
                throw new Failure(MetadataSimpleIteratorStatus.BAD_METADATA);
            return new VorbisCommentEntry(length, length == 0 ? "" : new String(read(length), StandardCharsets.UTF_8));
        }

        private VorbisComment readVorbisComment(int blockLength) throws IOException {
            VorbisComment block = new VorbisComment();
            MetadataSimpleIteratorStatus status = MetadataSimpleIteratorStatus.OK;
            VorbisCommentEntry vendor;
            try {
                vendor = readVorbisCommentEntry(blockLength);
            } catch (Failure e) {
                if (e.status != MetadataSimpleIteratorStatus.BAD_METADATA)
                    throw e;
                if (blockLength >= entry_length_len)
                    blockLength -= entry_length_len;
                if (blockLength > 0)
                    skip(blockLength);
                throw e;
            }
            blockLength -= entry_length_len + vendor.length;
            block.vendorString = vendor.entry;
            if (blockLength < num_comments_len) {
                if (blockLength > 0)
                    skip(blockLength);
                return block;
            }
            blockLength -= num_comments_len;
            int count = unpackUint32LittleEndian(read(num_comments_len), 0, num_comments_len);
            /* each comment needs at least 4 byte */
            if (Integer.compareUnsigned(count, Integer.divideUnsigned(blockLength, entry_length_len)) > 0)
                status = MetadataSimpleIteratorStatus.BAD_METADATA;
            else {
                block.comments = new String[count];
                for (int i = 0; i < count; i++) {
                    try {
                        VorbisCommentEntry entry = readVorbisCommentEntry(blockLength);
                        blockLength -= entry_length_len + entry.length;
                        block.comments[i] = entry.entry;
                    } catch (Failure e) {
                        if (e.status != MetadataSimpleIteratorStatus.BAD_METADATA)
                            throw e;
                        if (blockLength >= entry_length_len)
                            blockLength -= entry_length_len;
                        status = e.status;
                        break;
                    }
                }
            }
            if (blockLength > 0)
                skip(blockLength);
            /* bad metadata */
            if (status != MetadataSimpleIteratorStatus.OK)
                throw new Failure(status);
            return block;
        }

        private void readCueSheetTrack(CueSheetTrack track) throws IOException {
            track.offset = unpackUint64(read(Format.STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN / Byte.SIZE), 0,
                    Long.BYTES);
            track.number = read(Format.STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN / Byte.SIZE)[0] & 255;
            track.isrc = readFixedText(Format.STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN / Byte.SIZE);
            int flags = read((Format.STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN
                    + Format.STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN
                    + Format.STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN) / Byte.SIZE)[0] & 255;
            track.type = flags >>> 7;
            track.preEmphasis = (flags & 64) != 0;
            track.resizeIndices(read(Format.STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN / Byte.SIZE)[0] & 255);
            for (CueSheetIndex index : track.indices) {
                index.offset = unpackUint64(read(Format.STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN / Byte.SIZE), 0,
                        Long.BYTES);
                index.number = read(Format.STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN / Byte.SIZE)[0] & 255;
                read(Format.STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN / Byte.SIZE);
            }
        }

        private String readFixedText(int byteLength) throws IOException {
            byte[] data = read(byteLength);
            int end = byteLength;
            while (end > 0 && data[end - 1] == 0)
                end--;
            return new String(data, 0, end, StandardCharsets.UTF_8);
        }

        private CueSheet readCueSheet() throws IOException {
            CueSheet block = new CueSheet();
            block.mediaCatalogNumber = readFixedText(
                    Format.STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN / Byte.SIZE);
            block.leadIn = unpackUint64(read(Format.STREAM_METADATA_CUESHEET_LEAD_IN_LEN / Byte.SIZE), 0, Long.BYTES);
            block.isCd = (read(
                    (Format.STREAM_METADATA_CUESHEET_IS_CD_LEN + Format.STREAM_METADATA_CUESHEET_RESERVED_LEN)
                            / Byte.SIZE)[0]
                    & 128) != 0;
            int tracks = read(Format.STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN / Byte.SIZE)[0] & 255;
            if (tracks == 0)
                throw new Failure(MetadataSimpleIteratorStatus.BAD_METADATA);
            block.resizeTracks(tracks);
            for (CueSheetTrack track : block.tracks)
                readCueSheetTrack(track);
            return block;
        }

        private byte[] readPictureString(int lengthLen) throws IOException {
            /* convert to bytes */
            int length = unpackUint32(read(lengthLen / Byte.SIZE), 0, lengthLen / Byte.SIZE);
            /* data cannot be larger than FLAC metadata block */
            if (Integer.compareUnsigned(length, 1 << Format.STREAM_METADATA_LENGTH_LEN) > 0)
                throw new Failure(MetadataSimpleIteratorStatus.BAD_METADATA);
            return length == 0 ? new byte[0] : read(length);
        }

        private Picture readPicture() throws IOException {
            Picture picture = new Picture();
            picture.pictureType = unpackUint32(read(Format.STREAM_METADATA_PICTURE_TYPE_LEN / Byte.SIZE), 0,
                    Integer.BYTES);
            picture.mimeType = new String(readPictureString(Format.STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN),
                    StandardCharsets.UTF_8);
            picture.description = new String(readPictureString(Format.STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN),
                    StandardCharsets.UTF_8);
            picture.width = unpackUint32(read(Format.STREAM_METADATA_PICTURE_WIDTH_LEN / Byte.SIZE), 0, Integer.BYTES);
            picture.height = unpackUint32(read(Format.STREAM_METADATA_PICTURE_HEIGHT_LEN / Byte.SIZE), 0,
                    Integer.BYTES);
            picture.depth = unpackUint32(read(Format.STREAM_METADATA_PICTURE_DEPTH_LEN / Byte.SIZE), 0, Integer.BYTES);
            picture.colors = unpackUint32(read(Format.STREAM_METADATA_PICTURE_COLORS_LEN / Byte.SIZE), 0,
                    Integer.BYTES);
            picture.data = readPictureString(Format.STREAM_METADATA_PICTURE_DATA_LENGTH_LEN);
            return picture;
        }

        private UnknownMetadata readUnknown(int type, int blockLength) throws IOException {
            if (type > Format.MAX_METADATA_TYPE_CODE)
                throw new Failure(MetadataSimpleIteratorStatus.MEMORY_ALLOCATION_ERROR);
            UnknownMetadata block = new UnknownMetadata(type);
            if (blockLength > 0)
                block.data = read(blockLength);
            return block;
        }

        boolean seekToFirstMetadataBlock() throws IOException {
            byte[] marker;
            try {
                marker = read(Format.STREAM_SYNC_LENGTH);
            } catch (Failure e) {
                return false;
            }
            /* skip any id3v2 tag */
            if (marker[0] == 'I' && marker[1] == 'D' && marker[2] == '3') {
                /* skip to the tag length */
                skip(2);
                /* read the length */
                int tagLength = 0;
                for (int i = 0; i < 4; i++) {
                    int b = read(1)[0] & 255;
                    if ((b & 128) != 0)
                        throw new Failure(MetadataSimpleIteratorStatus.READ_ERROR);
                    tagLength = (tagLength << 7) | b;
                }
                /* skip the rest of the tag */
                skip(tagLength);
                try {
                    /* read the stream sync code */
                    marker = read(Format.STREAM_SYNC_LENGTH);
                } catch (Failure e) {
                    return false;
                }
            }
            /* check for the fLaC signature */
            return Arrays.equals(marker, Format.STREAM_SYNC_STRING);
        }

        byte[] read(int count) throws IOException {
            byte[] data = new byte[count];
            failureStatus = MetadataSimpleIteratorStatus.READ_ERROR;
            int n = callbacks.read(data, 0, count);
            if (n > 0)
                position += n;
            if (n != count)
                throw new Failure(MetadataSimpleIteratorStatus.READ_ERROR);
            return data;
        }

        void skip(long count) throws IOException {
            seek(position + count);
        }

        void seek(long offset) throws IOException {
            failureStatus = MetadataSimpleIteratorStatus.SEEK_ERROR;
            callbacks.seek(offset);
            position = offset;
        }

        long tell() throws IOException {
            failureStatus = MetadataSimpleIteratorStatus.READ_ERROR;
            long offset = callbacks.tell();
            if (offset < 0)
                throw new Failure(failureStatus);
            position = offset;
            return offset;
        }
    }
}
