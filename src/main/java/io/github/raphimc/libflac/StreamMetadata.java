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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * FLAC metadata block structure.  (c.f. <A HREF="https://xiph.org/flac/format.html#metadata_block">format
 * specification</A>)
 */
public abstract class StreamMetadata {
    /**
     * The type of the metadata block; used to determine which metadata class to use.
     * If typeCode >= MetadataType.UNDEFINED.ordinal() then UnknownMetadata must be used.
     */
    final int typeCode;
    /** {@code true} if this metadata block is the last, else {@code false} */
    boolean isLast;

    StreamMetadata(int typeCode) {
        this.typeCode = typeCode;
    }

    public MetadataType getType() {
        return MetadataType.values()[Math.min(typeCode, MetadataType.UNDEFINED.ordinal())];
    }

    public int getTypeCode() {
        return typeCode;
    }

    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean value) {
        isLast = value;
    }

    /** Length, in bytes, of the block data as it appears in the stream. */
    public int getLength() {
        if (this instanceof Application application)
            return Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE + application.data.length;
        if (this instanceof SeekTable table)
            return table.points.length * Format.STREAM_METADATA_SEEKPOINT_LENGTH;
        if (this instanceof VorbisComment comment) {
            int length = (Format.STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN
                    + Format.STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN) / Byte.SIZE
                    + comment.vendorString.getBytes(StandardCharsets.UTF_8).length;
            for (String entry : comment.comments)
                length += Format.STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN / Byte.SIZE
                        + entry.getBytes(StandardCharsets.UTF_8).length;
            return length;
        }
        if (this instanceof CueSheet sheet) {
            int length = (Format.STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN
                    + Format.STREAM_METADATA_CUESHEET_LEAD_IN_LEN + Format.STREAM_METADATA_CUESHEET_IS_CD_LEN
                    + Format.STREAM_METADATA_CUESHEET_RESERVED_LEN + Format.STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN)
                    / Byte.SIZE;
            for (CueSheetTrack track : sheet.tracks)
                length += (Format.STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN
                        + Format.STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN
                        + Format.STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN
                        + Format.STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN
                        + Format.STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN
                        + Format.STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN
                        + Format.STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN) / Byte.SIZE
                        + track.indices.length * ((Format.STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN
                                + Format.STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN
                                + Format.STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN) / Byte.SIZE);
            return length;
        }
        if (this instanceof Picture picture)
            return (Format.STREAM_METADATA_PICTURE_TYPE_LEN + Format.STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN
                    + Format.STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN + Format.STREAM_METADATA_PICTURE_WIDTH_LEN
                    + Format.STREAM_METADATA_PICTURE_HEIGHT_LEN + Format.STREAM_METADATA_PICTURE_DEPTH_LEN
                    + Format.STREAM_METADATA_PICTURE_COLORS_LEN + Format.STREAM_METADATA_PICTURE_DATA_LENGTH_LEN)
                    / Byte.SIZE + picture.mimeType.getBytes(StandardCharsets.UTF_8).length
                    + picture.description.getBytes(StandardCharsets.UTF_8).length + picture.data.length;
        return ((UnknownMetadata) this).data.length;
    }

    /**
     * Create a copy of an existing metadata object.
     *
     *  The copy is a "deep" copy, i.e. dynamically allocated data within the
     *  object is also copied.
     *
     * @return The new instance.
     */
    public StreamMetadata copy() {
        StreamMetadata result;
        if (this instanceof StreamInfo s) {
            StreamInfo c = new StreamInfo();
            c.minBlocksize = s.minBlocksize;
            c.maxBlocksize = s.maxBlocksize;
            c.minFramesize = s.minFramesize;
            c.maxFramesize = s.maxFramesize;
            c.sampleRate = s.sampleRate;
            c.channels = s.channels;
            c.bitsPerSample = s.bitsPerSample;
            c.totalSamples = s.totalSamples;
            c.md5sum = s.md5sum.clone();
            result = c;
        } else if (this instanceof Padding p)
            result = new Padding(p.length);
        else if (this instanceof Application a) {
            Application c = new Application();
            c.id = a.id.clone();
            c.data = a.data.clone();
            result = c;
        } else if (this instanceof SeekTable t) {
            SeekTable c = new SeekTable();
            c.points = Arrays.stream(t.points).map(SeekPoint::copy).toArray(SeekPoint[]::new);
            result = c;
        } else if (this instanceof VorbisComment v) {
            VorbisComment c = new VorbisComment();
            c.vendorString = v.vendorString;
            c.comments = v.comments.clone();
            result = c;
        } else if (this instanceof CueSheet c) {
            CueSheet d = new CueSheet();
            d.mediaCatalogNumber = c.mediaCatalogNumber;
            d.leadIn = c.leadIn;
            d.isCd = c.isCd;
            d.tracks = Arrays.stream(c.tracks).map(CueSheetTrack::copy).toArray(CueSheetTrack[]::new);
            result = d;
        } else if (this instanceof Picture p) {
            Picture c = new Picture();
            c.pictureType = p.pictureType;
            c.mimeType = p.mimeType;
            c.description = p.description;
            c.width = p.width;
            c.height = p.height;
            c.depth = p.depth;
            c.colors = p.colors;
            c.data = p.data.clone();
            result = c;
        } else {
            UnknownMetadata u = (UnknownMetadata) this;
            UnknownMetadata c = new UnknownMetadata(typeCode);
            c.data = u.data.clone();
            result = c;
        }
        result.isLast = isLast;
        return result;
    }

    private static boolean compareBlockDataStreamInfo(StreamInfo block1, StreamInfo block2) {
        return block1.minBlocksize == block2.minBlocksize && block1.maxBlocksize == block2.maxBlocksize
                && block1.minFramesize == block2.minFramesize && block1.maxFramesize == block2.maxFramesize
                && block1.sampleRate == block2.sampleRate && block1.channels == block2.channels
                && block1.bitsPerSample == block2.bitsPerSample && block1.totalSamples == block2.totalSamples
                && Arrays.equals(block1.md5sum, block2.md5sum);
    }

    private static boolean compareBlockDataApplication(Application block1, Application block2) {
        return Arrays.equals(block1.id, block2.id) && Arrays.equals(block1.data, block2.data);
    }

    private static boolean compareBlockDataSeekTable(SeekTable block1, SeekTable block2) {
        if (block1.points.length != block2.points.length)
            return false;
        for (int i = 0; i < block1.points.length; i++) {
            SeekPoint x = block1.points[i], y = block2.points[i];
            if (x.sampleNumber != y.sampleNumber || x.streamOffset != y.streamOffset
                    || x.frameSamples != y.frameSamples)
                return false;
        }
        return true;
    }

    private static boolean compareBlockDataVorbisComment(VorbisComment block1, VorbisComment block2) {
        return block1.vendorString.equals(block2.vendorString) && Arrays.equals(block1.comments, block2.comments);
    }

    private static boolean compareBlockDataCueSheet(CueSheet block1, CueSheet block2) {
        if (!block1.mediaCatalogNumber.equals(block2.mediaCatalogNumber) || block1.leadIn != block2.leadIn
                || block1.isCd != block2.isCd || block1.tracks.length != block2.tracks.length)
            return false;
        for (int i = 0; i < block1.tracks.length; i++) {
            CueSheetTrack x = block1.tracks[i], y = block2.tracks[i];
            if (x.offset != y.offset || x.number != y.number || !x.isrc.equals(y.isrc) || x.type != y.type
                    || x.preEmphasis != y.preEmphasis || x.indices.length != y.indices.length)
                return false;
            for (int j = 0; j < x.indices.length; j++)
                if (x.indices[j].offset != y.indices[j].offset || x.indices[j].number != y.indices[j].number)
                    return false;
        }
        return true;
    }

    private static boolean compareBlockDataPicture(Picture block1, Picture block2) {
        return block1.pictureType == block2.pictureType && block1.mimeType.equals(block2.mimeType)
                && block1.description.equals(block2.description) && block1.width == block2.width
                && block1.height == block2.height && block1.depth == block2.depth && block1.colors == block2.colors
                && Arrays.equals(block1.data, block2.data);
    }

    private static boolean compareBlockDataUnknown(UnknownMetadata block1, UnknownMetadata block2) {
        return Arrays.equals(block1.data, block2.data);
    }

    /**
     * Compares two metadata objects.
     *
     *  The compare is "deep", i.e. dynamically allocated data within the
     *  object is also compared.
     *
     * @return {@code true} if objects are identical, else {@code false}.
     */
    public boolean isEqual(StreamMetadata other) {
        if (other == null || typeCode != other.typeCode || isLast != other.isLast || getLength() != other.getLength())
            return false;
        if (this instanceof StreamInfo a && other instanceof StreamInfo b)
            return compareBlockDataStreamInfo(a, b);
        /* we don't compare the padding guts */
        if (this instanceof Padding)
            return true;
        if (this instanceof Application a && other instanceof Application b)
            return compareBlockDataApplication(a, b);
        if (this instanceof SeekTable a && other instanceof SeekTable b)
            return compareBlockDataSeekTable(a, b);
        if (this instanceof VorbisComment a && other instanceof VorbisComment b)
            return compareBlockDataVorbisComment(a, b);
        if (this instanceof CueSheet a && other instanceof CueSheet b)
            return compareBlockDataCueSheet(a, b);
        if (this instanceof Picture a && other instanceof Picture b)
            return compareBlockDataPicture(a, b);
        return this instanceof UnknownMetadata a && other instanceof UnknownMetadata b && compareBlockDataUnknown(a, b);
    }

    /**
     * Get the raw (binary) representation of a StreamMetadata objeect.
     *  The length of the buffer is
     *  the length of the input metadata object plus 4 bytes for the header.
     *
     * @return {@code null} if there was an error, else a buffer holding
     *        the requested data.
     */
    public byte[] getRaw() {
        try {
            BitWriter writer = new BitWriter();
            StreamEncoderFraming.addMetadataBlock(this, writer, false);
            return writer.getBuffer();
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /**
     * Turn a raw (binary) representation into a StreamMetadata objeect.
     *
     * @param buffer A buffer containing a binary representation
     *                   to be converted to a StreamMetadata object
     * @return {@code null} if there was an error, else a StreamMetadata
     *        holding the requested data.
     */
    public static StreamMetadata setRaw(byte[] buffer) throws IOException {
        StreamMetadata[] result = {null};
        boolean[] error = {false};
        int[] position = {-4};
        try (StreamDecoder decoder = new StreamDecoder()) {
            decoder.setMd5Checking(false);
            decoder.setMetadataRespondAll();
            StreamDecoderInitStatus init = decoder.initStream((d, output, offset, length) -> {
                if (position[0] == -4) {
                    if (length < 4)
                        return -2;
                    System.arraycopy(Format.STREAM_SYNC_STRING, 0, output, offset, 4);
                    position[0] = 0;
                    return 4;
                }
                if (position[0] == buffer.length)
                    return -1;
                int count = Math.min(length, buffer.length - position[0]);
                System.arraycopy(buffer, position[0], output, offset, count);
                position[0] += count;
                return count;
            }, null, null, null, null, (d, frame, pcm) -> true, (d, block) -> {
                if (!error[0] && result[0] == null) {
                    if (block.typeCode > Format.MAX_METADATA_TYPE_CODE)
                        error[0] = true;
                    else
                        result[0] = block.copy();
                }
            }, (d, status) -> {
                if (status != StreamDecoderErrorStatus.LOST_SYNC)
                    error[0] = true;
            });
            if (init != StreamDecoderInitStatus.OK || error[0])
                return null;
            if ((!decoder.processUntilEndOfMetadata() && decoder.getState() != StreamDecoderState.END_OF_STREAM)
                    || error[0])
                return null;
        }
        return result[0];
    }

}
