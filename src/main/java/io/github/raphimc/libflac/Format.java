/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2000-2009  Josh Coalson
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

/**
 * This module contains structure definitions for the representation
 *  of FLAC format components in memory.  These are the basic
 *  structures used by the rest of the interfaces.
 *
 *  First, you should be familiar with the
 *  <A HREF="https://xiph.org/flac/format.html">FLAC format</A>.  Many of the values here
 *  follow directly from the specification.  As a user of libFLAC, the
 *  interesting parts really are the structures that describe the frame
 *  header and metadata blocks.
 *
 *  The format structures here are very primitive, designed to store
 *  information in an efficient way.  Reading information from the
 *  structures is easy but creating or modifying them directly is
 *  more complex.  For the most part, as a user of a library, editing
 *  is not necessary; however, for metadata blocks it is, so there are
 *  convenience functions provided in the metadata
 *  module  to simplify the manipulation of metadata blocks.
 *
 * <p>It's not the best convention, but symbols ending in _LEN are in bits
 * and _LENGTH are in bytes.
 */
public final class Format {
    private Format() {
    }

    public static final boolean API_SUPPORTS_OGG_FLAC = true;

    public static final byte[] STREAM_SYNC_STRING = {'f', 'L', 'a', 'C'};

    /** The largest legal metadata type code. */
    public static final int MAX_METADATA_TYPE_CODE = 126;
    /** The minimum block size, in samples, permitted by the format. */
    public static final int MIN_BLOCK_SIZE = 16;
    /** The maximum block size, in samples, permitted by the format. */
    public static final int MAX_BLOCK_SIZE = 65535;
    /**
     * The maximum block size, in samples, permitted by the FLAC subset for
     *  sample rates up to 48kHz.
     */
    public static final int SUBSET_MAX_BLOCK_SIZE_48000HZ = 4608;
    /** The maximum number of channels permitted by the format. */
    public static final int MAX_CHANNELS = 8;
    /** The minimum sample resolution permitted by the format. */
    public static final int MIN_BITS_PER_SAMPLE = 4;
    /** The maximum sample resolution permitted by the format. */
    public static final int MAX_BITS_PER_SAMPLE = 32;
    /**
     * The maximum sample resolution permitted by libFLAC.
     *
     * Format.MAX_BITS_PER_SAMPLE is the limit of the FLAC format.  However,
     * the reference encoder/decoder used to be limited to 24 bits. This
     * value was used to signal that limit.
     */
    public static final int REFERENCE_CODEC_MAX_BITS_PER_SAMPLE = 32;
    /**
     * The maximum sample rate permitted by the format.  The value is
     *  ((2 ^ 20) - 1)
     */
    public static final int MAX_SAMPLE_RATE = 1048575;
    /** The maximum LPC order permitted by the format. */
    public static final int MAX_LPC_ORDER = 32;
    /**
     * The maximum LPC order permitted by the FLAC subset for sample rates
     *  up to 48kHz.
     */
    public static final int SUBSET_MAX_LPC_ORDER_48000HZ = 12;
    /**
     * The minimum quantized linear predictor coefficient precision
     *  permitted by the format.
     */
    public static final int MIN_QLP_COEFF_PRECISION = 5;
    /**
     * The maximum quantized linear predictor coefficient precision
     *  permitted by the format.
     */
    public static final int MAX_QLP_COEFF_PRECISION = 15;
    /** The maximum order of the fixed predictors permitted by the format. */
    public static final int MAX_FIXED_ORDER = 4;
    /** The maximum Rice partition order permitted by the format. */
    public static final int MAX_RICE_PARTITION_ORDER = 15;
    /** The maximum Rice partition order permitted by the FLAC Subset. */
    public static final int SUBSET_MAX_RICE_PARTITION_ORDER = 8;
    /** The length of the FLAC signature in bytes. */
    public static final int STREAM_SYNC_LENGTH = 4;
    /** The total stream length of the STREAMINFO block in bytes. */
    public static final int STREAM_METADATA_STREAMINFO_LENGTH = 34;
    /** The total stream length of a seek point in bytes. */
    public static final int STREAM_METADATA_SEEKPOINT_LENGTH = 18;
    /** The total stream length of a metadata block header in bytes. */
    public static final int STREAM_METADATA_HEADER_LENGTH = 4;
    /** The version string of the upstream release. */
    public static final String VERSION_STRING = "1.4.3";
    /** The vendor string inserted by the encoder into the VORBIS_COMMENT block. */
    public static final String VENDOR_STRING = "libflac-java 1.4.3.1";
    public static final long STREAM_METADATA_SEEKPOINT_PLACEHOLDER = -1L;
    /**
     * The 32-bit integer big-endian representation of the beginning of
     *  a FLAC stream.
     *
     * = 0x664C6143
     */
    public static final int STREAM_SYNC = 0x664C6143;
    /**
     * The length of the FLAC signature in bits.
     *
     * = 32 bits
     */
    public static final int STREAM_SYNC_LEN = 32;
    /** == 16 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN = 16;
    /** == 16 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN = 16;
    /** == 24 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN = 24;
    /** == 24 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN = 24;
    /** == 20 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN = 20;
    /** == 3 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_CHANNELS_LEN = 3;
    /** == 5 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN = 5;
    /** == 36 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN = 36;
    /** == 128 (bits) */
    public static final int STREAM_METADATA_STREAMINFO_MD5SUM_LEN = 128;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_APPLICATION_ID_LEN = 32;
    /** == 64 (bits) */
    public static final int STREAM_METADATA_SEEKPOINT_SAMPLE_NUMBER_LEN = 64;
    /** == 64 (bits) */
    public static final int STREAM_METADATA_SEEKPOINT_STREAM_OFFSET_LEN = 64;
    /** == 16 (bits) */
    public static final int STREAM_METADATA_SEEKPOINT_FRAME_SAMPLES_LEN = 16;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_VORBIS_COMMENT_ENTRY_LENGTH_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_VORBIS_COMMENT_NUM_COMMENTS_LEN = 32;
    /** == 64 (bits) */
    public static final int STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN = 64;
    /** == 8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN = 8;
    /** == 3*8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN = 3 * 8;
    /** == 64 (bits) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN = 64;
    /** == 8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN = 8;
    /** == 12*8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN = 12 * 8;
    /** == 1 (bit) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN = 1;
    /** == 1 (bit) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN = 1;
    /** == 6+13*8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN = 6 + 13 * 8;
    /** == 8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN = 8;
    /** == 128*8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN = 128 * 8;
    /** == 64 (bits) */
    public static final int STREAM_METADATA_CUESHEET_LEAD_IN_LEN = 64;
    /** == 1 (bit) */
    public static final int STREAM_METADATA_CUESHEET_IS_CD_LEN = 1;
    /** == 7+258*8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_RESERVED_LEN = 7 + 258 * 8;
    /** == 8 (bits) */
    public static final int STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN = 8;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_TYPE_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_WIDTH_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_HEIGHT_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_DEPTH_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_COLORS_LEN = 32;
    /** == 32 (bits) */
    public static final int STREAM_METADATA_PICTURE_DATA_LENGTH_LEN = 32;
    /** == 1 (bit) */
    public static final int STREAM_METADATA_IS_LAST_LEN = 1;
    /** == 7 (bits) */
    public static final int STREAM_METADATA_TYPE_LEN = 7;
    /** == 24 (bits) */
    public static final int STREAM_METADATA_LENGTH_LEN = 24;
    /** == 0x3ffe; the frame header sync code */
    public static final int FRAME_HEADER_SYNC = 0x3ffe;
    /** == 14 (bits) */
    public static final int FRAME_HEADER_SYNC_LEN = 14;
    /** == 1 (bits) */
    public static final int FRAME_HEADER_RESERVED_LEN = 1;
    /** == 1 (bits) */
    public static final int FRAME_HEADER_BLOCKING_STRATEGY_LEN = 1;
    /** == 4 (bits) */
    public static final int FRAME_HEADER_BLOCK_SIZE_LEN = 4;
    /** == 4 (bits) */
    public static final int FRAME_HEADER_SAMPLE_RATE_LEN = 4;
    /** == 4 (bits) */
    public static final int FRAME_HEADER_CHANNEL_ASSIGNMENT_LEN = 4;
    /** == 3 (bits) */
    public static final int FRAME_HEADER_BITS_PER_SAMPLE_LEN = 3;
    /** == 1 (bit) */
    public static final int FRAME_HEADER_ZERO_PAD_LEN = 1;
    /** == 8 (bits) */
    public static final int FRAME_HEADER_CRC_LEN = 8;
    /** == 16 (bits) */
    public static final int FRAME_FOOTER_CRC_LEN = 16;
    /** == 2 (bits) */
    public static final int ENTROPY_CODING_METHOD_TYPE_LEN = 2;
    /** == 4 (bits) */
    public static final int ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN = 4;
    /** == 4 (bits) */
    public static final int ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN = 4;
    /** == 5 (bits) */
    public static final int ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN = 5;
    /** == 5 (bits) */
    public static final int ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN = 5;
    /** == (1&lt;&lt;Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN)-1 */
    public static final int ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER = 15;
    /** == (1&lt;&lt;Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN)-1 */
    public static final int ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER = 31;
    /** == 4 (bits) */
    public static final int SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN = 4;
    /** == 5 (bits) */
    public static final int SUBFRAME_LPC_QLP_SHIFT_LEN = 5;
    /**
     * == 1 (bit)
     *
     * This used to be a zero-padding bit (hence the name
     * Format.SUBFRAME_ZERO_PAD_LEN) but is now a reserved bit.  It still has a
     * mandatory value of {@code 0} but in the future may take on the value {@code 0} or {@code 1}
     * to mean something else.
     */
    public static final int SUBFRAME_ZERO_PAD_LEN = 1;
    /** == 6 (bits) */
    public static final int SUBFRAME_TYPE_LEN = 6;
    /** == 1 (bit) */
    public static final int SUBFRAME_WASTED_BITS_FLAG_LEN = 1;
    /** = 0x00 */
    public static final int SUBFRAME_TYPE_CONSTANT_BYTE_ALIGNED_MASK = 0x00;
    /** = 0x02 */
    public static final int SUBFRAME_TYPE_VERBATIM_BYTE_ALIGNED_MASK = 0x02;
    /** = 0x10 */
    public static final int SUBFRAME_TYPE_FIXED_BYTE_ALIGNED_MASK = 0x10;
    /** = 0x40 */
    public static final int SUBFRAME_TYPE_LPC_BYTE_ALIGNED_MASK = 0x40;

    /**
     * Tests that a sample rate is valid for FLAC.
     *
     * @param sampleRate The sample rate to test for compliance.
     * @return {@code true} if the given sample rate conforms to the specification, else
     *    {@code false}.
     */
    public static boolean sampleRateIsValid(int sampleRate) {
        return Integer.compareUnsigned(sampleRate, MAX_SAMPLE_RATE) <= 0;
    }

    /**
     * Tests that a blocksize at the given sample rate is valid for the FLAC
     *  subset.
     *
     * @param blocksize The blocksize to test for compliance.
     * @param sampleRate The sample rate is needed, since the valid subset
     *                     blocksize depends on the sample rate.
     * @return {@code true} if the given blocksize conforms to the specification for the
     *    subset at the given sample rate, else {@code false}.
     */
    public static boolean blocksizeIsSubset(int blocksize, int sampleRate) {
        return Integer.compareUnsigned(blocksize, 16384) <= 0
                && (Integer.compareUnsigned(sampleRate, 48000) > 0 || blocksize <= SUBSET_MAX_BLOCK_SIZE_48000HZ);
    }

    /**
     * Tests that a sample rate is valid for the FLAC subset.  The subset rules
     *  for valid sample rates are slightly more complex since the rate has to
     *  be expressible completely in the frame header.
     *
     * @param sampleRate The sample rate to test for compliance.
     * @return {@code true} if the given sample rate conforms to the specification for the
     *    subset, else {@code false}.
     */
    public static boolean sampleRateIsSubset(int sampleRate) {
        /*
         * sample rate is not subset if
         * sample rate is invalid or
         * sample rate is larger then or equal to 655360 or
         * sample rate is >= 65536 and not divisible by 10
         */
        return sampleRateIsValid(sampleRate) && sampleRate < (1 << 16) * 10
                && (sampleRate < (1 << 16) || sampleRate % 10 == 0);
    }

    /**
     * Check a seek table to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  seek table.
     *
     * @param seekTable A reference to a seek table to be checked.
     * @return {@code false} if seek table is illegal, else {@code true}.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public static boolean seektableIsLegal(SeekTable seekTable) {
        return seekTable.isLegal();
    }

    /**
     * Sort a seek table's seek points according to the format specification.
     *  This includes a "unique-ification" step to remove duplicates, i.e.
     *  seek points with identical {@code sampleNumber} values.  Duplicate seek
     *  points are converted into placeholder points and sorted to the end of
     *  the table.
     *
     * @param seekTable A reference to a seek table to be sorted.
     *
     * @return The number of duplicate seek points converted into placeholders.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public static int seektableSort(SeekTable seekTable) {
        return seekTable.sort();
    }

    /**
     * Check a Vorbis comment entry name to see if it conforms to the Vorbis
     *  comment specification.
     *
     *  Vorbis comment names must be composed only of characters from
     *  [0x20-0x3C,0x3E-0x7D].
     *
     * @param name A String to be checked.
     * @return {@code false} if entry name is illegal, else {@code true}.
     */
    public static boolean vorbiscommentEntryNameIsLegal(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == '=' || c > 0x7d)
                return false;
        }
        return true;
    }

    /**
     * Check a Vorbis comment entry value to see if it conforms to the Vorbis
     *  comment specification.
     *
     *  Vorbis comment values must be valid UTF-8 sequences.
     *
     * @param value A string to be checked.
     * @return {@code false} if entry name is illegal, else {@code true}.
     */
    public static boolean vorbiscommentEntryValueIsLegal(String value) {

        return value.indexOf('\ufffe') < 0 && value.indexOf('\uffff') < 0;
    }

    /**
     * Check a Vorbis comment entry to see if it conforms to the Vorbis
     *  comment specification.
     *
     *  Vorbis comment entries must be of the form 'name=value', and 'name' and
     *  'value' must be legal according to
     *  Format.vorbiscommentEntryNameIsLegal() and
     *  Format.vorbiscommentEntryValueIsLegal() respectively.
     *
     * @param entry An entry to be checked.
     * @return {@code false} if entry name is illegal, else {@code true}.
     */
    public static boolean vorbiscommentEntryIsLegal(String entry) {
        int at = entry.indexOf('=');
        if (at < 0)
            return false;

        for (int i = 0; i < at; i++)
            if (entry.charAt(i) < 0x20 || entry.charAt(i) > 0x7d)
                return false;
        /* skip '=' */
        return vorbiscommentEntryValueIsLegal(entry.substring(at + 1));
    }

    /**
     * Check a cue sheet to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  cue sheet.
     *
     * @param cueSheet A reference to an existing cue sheet to be checked.
     * @param checkCdDaSubset If {@code true}, check CUESHEET against more
     *                   stringent requirements for a CD-DA (audio) disc.
     * @return A violation message if illegal, else null.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public static String cuesheetIsLegal(CueSheet cueSheet, boolean checkCdDaSubset) {
        return cueSheet.checkLegal(checkCdDaSubset);
    }

    /**
     * Check picture data to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  PICTURE block.
     *
     * @param picture A reference to existing picture data to be checked.
     * @return A violation message if illegal, else null.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public static String pictureIsLegal(Picture picture) {
        return picture.checkLegal();
    }

    static int getMaxRicePartitionOrderFromBlocksize(int blocksize) {
        return Math.min(MAX_RICE_PARTITION_ORDER, Integer.numberOfTrailingZeros(blocksize));
    }

    static int getMaxRicePartitionOrderFromBlocksizeLimitedMaxAndPredictorOrder(int limit, int blocksize,
            int predictorOrder) {
        int maxPartitionOrder = limit;
        while (maxPartitionOrder > 0 && (blocksize >> maxPartitionOrder) <= predictorOrder)
            maxPartitionOrder--;
        return maxPartitionOrder;
    }
}
