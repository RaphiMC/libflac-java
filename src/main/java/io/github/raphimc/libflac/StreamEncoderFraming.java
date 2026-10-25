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

final class StreamEncoderFraming {
    private StreamEncoderFraming() {
    }

    static void addMetadataBlock(StreamMetadata metadata, BitWriter bw, boolean updateVendorString) throws IOException {
        int startBits = bw.getInputBitsUnconsumed();
        bw.writeRawUInt32(metadata.isLast ? 1 : 0, Format.STREAM_METADATA_IS_LAST_LEN);
        bw.writeRawUInt32(metadata.typeCode, Format.STREAM_METADATA_TYPE_LEN);
        /* First, for VORBIS_COMMENTs, adjust the length to reflect our vendor string */
        int metadataLength = metadata.getLength();
        if (metadata instanceof VorbisComment comment && updateVendorString) {
            metadataLength -= comment.vendorString.getBytes(StandardCharsets.UTF_8).length;
            metadataLength += Format.VENDOR_STRING.getBytes(StandardCharsets.UTF_8).length;
        }
        bw.writeRawUInt32(metadataLength, Format.STREAM_METADATA_LENGTH_LEN);
        if (metadata instanceof StreamInfo info) {
            bw.writeRawUInt32(info.minBlocksize, Format.STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN);
            bw.writeRawUInt32(info.maxBlocksize, Format.STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN);
            bw.writeRawUInt32(info.minFramesize, Format.STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN);
            bw.writeRawUInt32(info.maxFramesize, Format.STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN);
            bw.writeRawUInt32(info.sampleRate, Format.STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN);
            bw.writeRawUInt32(info.channels - 1, Format.STREAM_METADATA_STREAMINFO_CHANNELS_LEN);
            bw.writeRawUInt32(info.bitsPerSample - 1, Format.STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN);
            bw.writeRawUInt64(
                    Long.compareUnsigned(info.totalSamples,
                            1L << Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN) >= 0 ? 0 : info.totalSamples,
                    Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN);
            bw.writeByteBlock(info.md5sum);
        } else if (metadata instanceof Padding padding)
            bw.writeZeroes(padding.length * Byte.SIZE);
        else if (metadata instanceof Application application) {
            bw.writeByteBlock(application.id);
            bw.writeByteBlock(application.data);
        } else if (metadata instanceof SeekTable table) {
            for (SeekPoint point : table.points) {
                bw.writeRawUInt64(point.sampleNumber, Format.STREAM_METADATA_SEEKPOINT_SAMPLE_NUMBER_LEN);
                bw.writeRawUInt64(point.streamOffset, Format.STREAM_METADATA_SEEKPOINT_STREAM_OFFSET_LEN);
                bw.writeRawUInt32(point.frameSamples, Format.STREAM_METADATA_SEEKPOINT_FRAME_SAMPLES_LEN);
            }
        } else if (metadata instanceof VorbisComment comment) {
            byte[] entry = (updateVendorString ? Format.VENDOR_STRING : comment.vendorString)
                    .getBytes(StandardCharsets.UTF_8);
            bw.writeRawUInt32LittleEndian(entry.length);
            bw.writeByteBlock(entry);
            bw.writeRawUInt32LittleEndian(comment.comments.length);
            for (int i = 0; i < comment.comments.length; i++) {
                entry = comment.comments[i].getBytes(StandardCharsets.UTF_8);
                bw.writeRawUInt32LittleEndian(entry.length);
                bw.writeByteBlock(entry);
            }
        } else if (metadata instanceof CueSheet sheet) {
            bw.writeByteBlock(Arrays.copyOf(sheet.mediaCatalogNumber.getBytes(StandardCharsets.UTF_8),
                    Format.STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN / Byte.SIZE));
            bw.writeRawUInt64(sheet.leadIn, Format.STREAM_METADATA_CUESHEET_LEAD_IN_LEN);
            bw.writeRawUInt32(sheet.isCd ? 1 : 0, Format.STREAM_METADATA_CUESHEET_IS_CD_LEN);
            bw.writeZeroes(Format.STREAM_METADATA_CUESHEET_RESERVED_LEN);
            bw.writeRawUInt32(sheet.tracks.length, Format.STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN);
            for (CueSheetTrack track : sheet.tracks) {
                bw.writeRawUInt64(track.offset, Format.STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN);
                bw.writeRawUInt32(track.number, Format.STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN);
                bw.writeByteBlock(Arrays.copyOf(track.isrc.getBytes(StandardCharsets.UTF_8),
                        Format.STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN / Byte.SIZE));
                bw.writeRawUInt32(track.type, Format.STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN);
                bw.writeRawUInt32(track.preEmphasis ? 1 : 0, Format.STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN);
                bw.writeZeroes(Format.STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN);
                bw.writeRawUInt32(track.indices.length, Format.STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN);
                for (CueSheetIndex indx : track.indices) {
                    bw.writeRawUInt64(indx.offset, Format.STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN);
                    bw.writeRawUInt32(indx.number, Format.STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN);
                    bw.writeZeroes(Format.STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN);
                }
            }
        } else if (metadata instanceof Picture picture) {
            bw.writeRawUInt32(picture.pictureType, Format.STREAM_METADATA_PICTURE_TYPE_LEN);
            byte[] mimeType = picture.mimeType.getBytes(StandardCharsets.UTF_8);
            int len = mimeType.length;
            bw.writeRawUInt32(len, Format.STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN);
            bw.writeByteBlock(mimeType);
            byte[] description = picture.description.getBytes(StandardCharsets.UTF_8);
            len = description.length;
            bw.writeRawUInt32(len, Format.STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN);
            bw.writeByteBlock(description);
            bw.writeRawUInt32(picture.width, Format.STREAM_METADATA_PICTURE_WIDTH_LEN);
            bw.writeRawUInt32(picture.height, Format.STREAM_METADATA_PICTURE_HEIGHT_LEN);
            bw.writeRawUInt32(picture.depth, Format.STREAM_METADATA_PICTURE_DEPTH_LEN);
            bw.writeRawUInt32(picture.colors, Format.STREAM_METADATA_PICTURE_COLORS_LEN);
            bw.writeRawUInt32(picture.data.length, Format.STREAM_METADATA_PICTURE_DATA_LENGTH_LEN);
            bw.writeByteBlock(picture.data);
        } else
            bw.writeByteBlock(((UnknownMetadata) metadata).data);
        /* Now check whether metadata block length was correct */
        if (bw.getInputBitsUnconsumed() - startBits != metadataLength * Byte.SIZE
                + Format.STREAM_METADATA_HEADER_LENGTH * Byte.SIZE)
            throw new IllegalArgumentException("metadata length does not match its fields");
    }

    static boolean frameAddHeader(FrameHeader header, BitWriter bw) throws IOException {
        bw.writeRawUInt32(Format.FRAME_HEADER_SYNC, Format.FRAME_HEADER_SYNC_LEN);
        bw.writeRawUInt32(0, Format.FRAME_HEADER_RESERVED_LEN);
        bw.writeRawUInt32(header.numberType == FrameNumberType.FRAME_NUMBER ? 0 : 1,
                Format.FRAME_HEADER_BLOCKING_STRATEGY_LEN);

        int blocksizeHint = 0;
        int u = switch (header.blocksize) {
            case 192 -> 1;
            case 576 -> 2;
            case 1152 -> 3;
            case 2304 -> 4;
            case 4608 -> 5;
            case 256 -> 8;
            case 512 -> 9;
            case 1024 -> 10;
            case 2048 -> 11;
            case 4096 -> 12;
            case 8192 -> 13;
            case 16384 -> 14;
            case 32768 -> 15;
            default -> {
                blocksizeHint = header.blocksize <= 0x100 ? 6 : 7;
                yield blocksizeHint;
            }
        };
        bw.writeRawUInt32(u, Format.FRAME_HEADER_BLOCK_SIZE_LEN);

        int sampleRateHint = 0;
        u = switch (header.sampleRate) {
            case 88200 -> 1;
            case 176400 -> 2;
            case 192000 -> 3;
            case 8000 -> 4;
            case 16000 -> 5;
            case 22050 -> 6;
            case 24000 -> 7;
            case 32000 -> 8;
            case 44100 -> 9;
            case 48000 -> 10;
            case 96000 -> 11;
            default -> {
                if (header.sampleRate <= 255000 && header.sampleRate % 1000 == 0)
                    sampleRateHint = 12;
                else if (header.sampleRate <= 655350 && header.sampleRate % 10 == 0)
                    sampleRateHint = 14;
                else if (header.sampleRate <= 0xffff)
                    sampleRateHint = 13;
                yield sampleRateHint;
            }
        };
        bw.writeRawUInt32(u, Format.FRAME_HEADER_SAMPLE_RATE_LEN);

        u = switch (header.channelAssignment) {
            case INDEPENDENT -> header.channels - 1;
            case LEFT_SIDE -> 8;
            case RIGHT_SIDE -> 9;
            case MID_SIDE -> 10;
        };
        bw.writeRawUInt32(u, Format.FRAME_HEADER_CHANNEL_ASSIGNMENT_LEN);

        u = switch (header.bitsPerSample) {
            case 8 -> 1;
            case 12 -> 2;
            case 16 -> 4;
            case 20 -> 5;
            case 24 -> 6;
            case 32 -> 7;
            default -> 0;
        };
        bw.writeRawUInt32(u, Format.FRAME_HEADER_BITS_PER_SAMPLE_LEN);
        bw.writeRawUInt32(0, Format.FRAME_HEADER_ZERO_PAD_LEN);
        if (header.numberType == FrameNumberType.FRAME_NUMBER) {
            int frameNumber = (int) header.number;
            if (frameNumber < 0)
                return false;
            bw.writeUtf8UInt32(frameNumber);
        } else {
            if (header.number < 0 || header.number > 0xfffffffffL)
                return false;
            bw.writeUtf8UInt64(header.number);
        }
        if (blocksizeHint != 0)
            bw.writeRawUInt32(header.blocksize - 1, blocksizeHint == 6 ? 8 : 16);
        switch (sampleRateHint) {
            case 12 -> bw.writeRawUInt32(header.sampleRate / 1000, 8);
            case 13 -> bw.writeRawUInt32(header.sampleRate, 16);
            case 14 -> bw.writeRawUInt32(header.sampleRate / 10, 16);
        }
        /* write the CRC */
        int crc = bw.getWriteCrc8();
        bw.writeRawUInt32(crc, Format.FRAME_HEADER_CRC_LEN);
        return true;
    }

    static void subframeAddConstant(Subframe subframe, int subframeBps, int wastedBits, BitWriter bw)
            throws IOException {
        bw.writeRawUInt32(Format.SUBFRAME_TYPE_CONSTANT_BYTE_ALIGNED_MASK | (wastedBits != 0 ? 1 : 0),
                Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN + Format.SUBFRAME_WASTED_BITS_FLAG_LEN);
        if (wastedBits != 0)
            bw.writeUnaryUnsigned(wastedBits - 1);
        bw.writeRawInt64(subframe.value, subframeBps);
    }

    static void subframeAddFixed(Subframe subframe, int residualSamples, int subframeBps, int wastedBits, BitWriter bw)
            throws IOException {
        bw.writeRawUInt32(
                Format.SUBFRAME_TYPE_FIXED_BYTE_ALIGNED_MASK | (subframe.order << 1) | (wastedBits != 0 ? 1 : 0),
                Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN + Format.SUBFRAME_WASTED_BITS_FLAG_LEN);
        if (wastedBits != 0)
            bw.writeUnaryUnsigned(wastedBits - 1);
        for (int i = 0; i < subframe.order; i++)
            bw.writeRawInt64(subframe.warmup[i], subframeBps);
        addEntropyCodingMethod(bw, subframe.entropyCodingMethod);
        EntropyCodingMethod method = subframe.entropyCodingMethod;
        addResidualPartitionedRice(bw, subframe.residual, residualSamples, subframe.order, method.parameters,
                method.rawBits, method.order, method.type == EntropyCodingMethodType.PARTITIONED_RICE2);
    }

    static void subframeAddLpc(Subframe subframe, int residualSamples, int subframeBps, int wastedBits, BitWriter bw)
            throws IOException {
        bw.writeRawUInt32(
                Format.SUBFRAME_TYPE_LPC_BYTE_ALIGNED_MASK | ((subframe.order - 1) << 1) | (wastedBits != 0 ? 1 : 0),
                Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN + Format.SUBFRAME_WASTED_BITS_FLAG_LEN);
        if (wastedBits != 0)
            bw.writeUnaryUnsigned(wastedBits - 1);
        for (int i = 0; i < subframe.order; i++)
            bw.writeRawInt64(subframe.warmup[i], subframeBps);
        bw.writeRawUInt32(subframe.qlpCoeffPrecision - 1, Format.SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN);
        bw.writeRawInt32(subframe.quantizationLevel, Format.SUBFRAME_LPC_QLP_SHIFT_LEN);
        for (int i = 0; i < subframe.order; i++)
            bw.writeRawInt32(subframe.qlpCoeff[i], subframe.qlpCoeffPrecision);
        addEntropyCodingMethod(bw, subframe.entropyCodingMethod);
        EntropyCodingMethod method = subframe.entropyCodingMethod;
        addResidualPartitionedRice(bw, subframe.residual, residualSamples, subframe.order, method.parameters,
                method.rawBits, method.order, method.type == EntropyCodingMethodType.PARTITIONED_RICE2);
    }

    static void subframeAddVerbatim(Subframe subframe, int samples, int subframeBps, int wastedBits, BitWriter bw)
            throws IOException {
        bw.writeRawUInt32(Format.SUBFRAME_TYPE_VERBATIM_BYTE_ALIGNED_MASK | (wastedBits != 0 ? 1 : 0),
                Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN + Format.SUBFRAME_WASTED_BITS_FLAG_LEN);
        if (wastedBits != 0)
            bw.writeUnaryUnsigned(wastedBits - 1);
        for (int i = 0; i < samples; i++)
            bw.writeRawInt64(subframe.data[i], subframeBps);
    }

    private static void addEntropyCodingMethod(BitWriter bw, EntropyCodingMethod method) throws IOException {
        bw.writeRawUInt32(method.type.ordinal(), Format.ENTROPY_CODING_METHOD_TYPE_LEN);
        bw.writeRawUInt32(method.order, Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN);
    }

    private static void addResidualPartitionedRice(BitWriter bw, int[] residual, int residualSamples,
            int predictorOrder, int[] riceParameters, int[] rawBits, int partitionOrder, boolean isExtended)
            throws IOException {
        int plen = isExtended ? Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN
                : Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN;
        int pesc = isExtended ? Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER
                : Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER;
        if (partitionOrder == 0) {
            if (rawBits[0] == 0) {
                bw.writeRawUInt32(riceParameters[0], plen);
                bw.writeRiceSignedBlock(residual, 0, residualSamples, riceParameters[0]);
            } else {
                bw.writeRawUInt32(pesc, plen);
                bw.writeRawUInt32(rawBits[0], Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN);
                for (int i = 0; i < residualSamples; i++)
                    bw.writeRawInt32(residual[i], rawBits[0]);
            }
        } else {
            int k = 0, kLast = 0;
            int defaultPartitionSamples = (residualSamples + predictorOrder) >> partitionOrder;
            for (int i = 0; i < (1 << partitionOrder); i++) {
                int partitionSamples = defaultPartitionSamples;
                if (i == 0)
                    partitionSamples -= predictorOrder;
                k += partitionSamples;
                if (rawBits[i] == 0) {
                    bw.writeRawUInt32(riceParameters[i], plen);
                    bw.writeRiceSignedBlock(residual, kLast, k - kLast, riceParameters[i]);
                } else {
                    bw.writeRawUInt32(pesc, plen);
                    bw.writeRawUInt32(rawBits[i], Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN);
                    for (int j = kLast; j < k; j++)
                        bw.writeRawInt32(residual[j], rawBits[i]);
                }
                kLast = k;
            }
        }
    }
}
