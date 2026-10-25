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

/**
 * The level 0 interface consists of individual routines to read the
 *  STREAMINFO, VORBIS_COMMENT, CUESHEET, and PICTURE blocks, requiring
 *  only a filename.
 *
 *  They try to skip any ID3v2 tag at the head of the file.
 */
public final class Metadata {
    private Metadata() {
    }

    private static StreamMetadata getOneMetadataBlock(String filename, MetadataType type) throws IOException {
        StreamMetadata[] result = {null};
        boolean[] error = {false};
        try (StreamDecoder decoder = new StreamDecoder()) {
            decoder.setMetadataIgnoreAll();
            decoder.setMetadataRespond(type);
            StreamDecoderInitStatus status = decoder.initFile(filename, (d, f, pcm) -> true, (d, block) -> {
                if (!error[0] && result[0] == null)
                    result[0] = block.copy();
            }, (d, e) -> {
                if (e != StreamDecoderErrorStatus.LOST_SYNC)
                    error[0] = true;
            });
            if (status != StreamDecoderInitStatus.OK || !decoder.processUntilEndOfMetadata() || error[0])
                return null;
        }
        return result[0];
    }

    /**
     * Read the STREAMINFO metadata block of the given FLAC file.  This function
     *  will try to skip any ID3v2 tag at the head of the file.
     *
     * @param filename The path to the FLAC file to read.
     * @return The STREAMINFO block read from filename, or null if the file does not contain the block.
     */
    public static StreamInfo getStreamInfo(String filename) throws IOException {
        return (StreamInfo) getOneMetadataBlock(filename, MetadataType.STREAMINFO);
    }

    /**
     * Read the VORBIS_COMMENT metadata block of the given FLAC file.  This
     *  function will try to skip any ID3v2 tag at the head of the file.
     *
     * @param filename The path to the FLAC file to read.
     * @return The VORBIS_COMMENT block read from filename, or null if the file does not contain the block.
     */
    public static VorbisComment getTags(String filename) throws IOException {
        return (VorbisComment) getOneMetadataBlock(filename, MetadataType.VORBIS_COMMENT);
    }

    /**
     * Read the CUESHEET metadata block of the given FLAC file.  This
     *  function will try to skip any ID3v2 tag at the head of the file.
     *
     * @param filename The path to the FLAC file to read.
     * @return The CUESHEET block read from filename, or null if the file does not contain the block.
     */
    public static CueSheet getCueSheet(String filename) throws IOException {
        return (CueSheet) getOneMetadataBlock(filename, MetadataType.CUESHEET);
    }

    /**
     * Read a PICTURE metadata block of the given FLAC file.  This
     *  function will try to skip any ID3v2 tag at the head of the file.
     *  Since there can be more than one PICTURE block in a file, this
     *  function takes a number of parameters that act as constraints to
     *  the search.  The PICTURE block with the largest area matching all
     *  the constraints will be returned, or {@code null} will be returned if there was no such block.
     *
     * @param filename The path to the FLAC file to read.
     * @param type The desired picture type.  Use {@code -1} to mean
     *                    "any type".
     * @param mimeType The desired MIME type, e.g. "image/jpeg".  The
     *                    string will be matched exactly.  Use {@code null} to
     *                    mean "any MIME type".
     * @param description The desired description.  The string will be
     *                    matched exactly.  Use {@code null} to mean "any
     *                    description".
     * @param maxWidth The maximum width in pixels desired.  Use
     *                    {@code -1} to mean "any width".
     * @param maxHeight The maximum height in pixels desired.  Use
     *                    {@code -1} to mean "any height".
     * @param maxDepth The maximum color depth in bits-per-pixel desired.
     *                    Use {@code -1} to mean "any depth".
     * @param maxColors The maximum number of colors desired.  Use
     *                    {@code -1} to mean "any number of colors".
     * @return The PICTURE block read from filename, or null if the file does not contain the block.
     */
    public static Picture getPicture(String filename, int type, String mimeType, String description, int maxWidth,
            int maxHeight, int maxDepth, int maxColors) throws IOException {
        Picture best = null;
        long maxAreaSeen = 0, maxDepthSeen = 0;
        try (MetadataSimpleIterator iterator = new MetadataSimpleIterator()) {
            if (!iterator.init(filename, true, true))
                return null;
            do {
                if (iterator.getBlockType() == MetadataType.PICTURE) {
                    Picture picture = (Picture) iterator.getBlock();
                    if (picture == null)
                        break;
                    long area = Integer.toUnsignedLong(picture.width) * Integer.toUnsignedLong(picture.height);
                    /* check constraints */
                    if ((type == -1 || picture.pictureType == type)
                            && (mimeType == null || picture.mimeType.equals(mimeType))
                            && (description == null || picture.description.equals(description))
                            && Integer.compareUnsigned(picture.width, maxWidth) <= 0
                            && Integer.compareUnsigned(picture.height, maxHeight) <= 0
                            && Integer.compareUnsigned(picture.depth, maxDepth) <= 0
                            && Integer.compareUnsigned(picture.colors, maxColors) <= 0
                            && (Long.compareUnsigned(area, maxAreaSeen) > 0
                                    || area == maxAreaSeen && Integer.toUnsignedLong(picture.depth) > maxDepthSeen)) {
                        best = picture;
                        maxAreaSeen = area;
                        maxDepthSeen = Integer.toUnsignedLong(picture.depth);
                    }
                }
            } while (iterator.next());
        }
        return best;
    }
}
