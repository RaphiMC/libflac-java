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

import com.jcraft.jogg.Page;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * This module contains the functions which implement the stream
 *  encoder.
 *
 * The stream encoder can encode to native FLAC, and Ogg FLAC streams and files.
 *
 * The basic usage of this encoder is as follows:
 * - The program creates an instance of an encoder using
 *   new StreamEncoder().
 * - The program overrides the default settings using
 *   StreamEncoder.set*() functions.  At a minimum, the following
 *   functions should be called:
 *   - StreamEncoder.setChannels()
 *   - StreamEncoder.setBitsPerSample()
 *   - StreamEncoder.setSampleRate()
 *   - StreamEncoder.setOggSerialNumber() (if encoding to Ogg FLAC)
 *   - StreamEncoder.setTotalSamplesEstimate() (if known)
 * - If the application wants to control the compression level or set its own
 *   metadata, then the following should also be called:
 *   - StreamEncoder.setCompressionLevel()
 *   - StreamEncoder.setVerify()
 *   - StreamEncoder.setMetadata()
 * - The rest of the set functions should only be called if the client needs
 *   exact control over how the audio is compressed; thorough understanding
 *   of the FLAC format is necessary to achieve good results.
 * - The program initializes the instance to validate the settings and
 *   prepare for encoding using
 *   - StreamEncoder.initStream() or StreamEncoder.initFile() for native FLAC
 *   - StreamEncoder.initOggStream() or StreamEncoder.initOggFile() for Ogg FLAC
 * - The program calls StreamEncoder.process() or
 *   StreamEncoder.processInterleaved() to encode data, which
 *   subsequently calls the callbacks when there is encoder data ready
 *   to be written.
 * - The program finishes the encoding with StreamEncoder.finish(),
 *   which causes the encoder to encode any data still in its input pipe,
 *   update the metadata with the final encoding statistics if output
 *   seeking is possible, and finally reset the encoder to the
 *   uninitialized state.
 * - The instance may be used again or deleted with
 *   StreamEncoder.close().
 *
 * In more detail, the stream encoder functions similarly to the
 * stream decoder , but has fewer
 * callbacks and more options.  Typically the client will create a new
 * instance by calling new StreamEncoder(), then set the necessary
 * parameters with StreamEncoder.set*(), and initialize it by
 * calling one of the StreamEncoder.init*() functions.
 *
 * Unlike the decoders, the stream encoder has many options that can
 * affect the speed and compression ratio.  When setting these parameters
 * you should have some basic knowledge of the format (see the
 * <A HREF="https://xiph.org/flac/documentation_format_overview.html">user-level documentation</A>
 * or the <A HREF="https://xiph.org/flac/format.html">formal description</A>).  The
 * StreamEncoder.set*() functions themselves do not validate the
 * values as many are interdependent.  The StreamEncoder.init*()
 * functions will do this, so make sure to pay attention to the state
 * returned by StreamEncoder.init*() to make sure that it is
 * StreamEncoderInitStatus.OK.  Any parameters that are not set
 * before StreamEncoder.init*() will take on the defaults from
 * the constructor.
 *
 * There are three initialization functions for native FLAC, one for
 * setting up the encoder to encode FLAC data to the client via
 * callbacks, and two for encoding directly to a file.
 *
 * For encoding via callbacks, use StreamEncoder.initStream().
 * You must also supply a write callback which will be called anytime
 * there is raw encoded data to write.  If the client can seek the output
 * it is best to also supply seek and tell callbacks, as this allows the
 * encoder to go back after encoding is finished to write back
 * information that was collected while encoding, like seek point offsets,
 * frame sizes, etc.
 *
 * For encoding directly to a file, use StreamEncoder.initFile().  Then you must only supply a
 * filename or open {@code RandomAccessFile}; the encoder will handle all the callbacks
 * internally.  You may also supply a progress callback for periodic
 * notification of the encoding progress.
 *
 * There are three similarly-named init functions for encoding to Ogg
 * FLAC streams.
 *
 * The call to StreamEncoder.init*() currently will also immediately
 * call the write callback several times, once with the {@code fLaC} signature,
 * and once for each encoded metadata block.  Note that for Ogg FLAC
 * encoding you will usually get at least twice the number of callbacks than
 * with native FLAC, one for the Ogg page header and one for the page body.
 *
 * After initializing the instance, the client may feed audio data to the
 * encoder in one of two ways:
 *
 * - Channel separate, through StreamEncoder.process() - The client
 *   will pass an array of buffers, one for each channel, to
 *   the encoder, each of the same length.  The samples need not be
 *   block-aligned, but each channel should have the same number of samples.
 * - Channel interleaved, through
 *   StreamEncoder.processInterleaved() - The client will pass a single array of data that is channel-interleaved (i.e.
 * channel0_sample0,
 *   channel1_sample0, ... , channelN_sample0, channel0_sample1, ...).
 *   Again, the samples need not be block-aligned but they must be
 *   sample-aligned, i.e. the first value should be channel0_sample0 and
 *   the last value channelN_sampleM.
 *
 * Note that for either process call, each sample in the buffers should be a
 * signed integer, right-justified to the resolution set by
 * StreamEncoder.setBitsPerSample().  For example, if the resolution
 * is 16 bits per sample, the samples should all be in the range [-32768,32767].
 *
 * When the client is finished encoding data, it calls
 * StreamEncoder.finish(), which causes the encoder to encode any
 * data still in its input pipe, and call the metadata callback with the
 * final encoding statistics.  Then the instance may be deleted with
 * StreamEncoder.close() or initialized again to encode another
 * stream.
 *
 * For programs that write their own metadata, but that do not know the
 * actual metadata until after encoding, it is advantageous to instruct
 * the encoder to write a PADDING block of the correct size, so that
 * instead of rewriting the whole stream after encoding, the program can
 * just overwrite the PADDING block.  If only the maximum size of the
 * metadata is known, the program can write a slightly larger padding
 * block, then split it after encoding.
 *
 * Make sure you understand how lengths are calculated.  All FLAC metadata
 * blocks have a 4 byte header which contains the type and length.  This
 * length does not include the 4 bytes of the header.  See the format page
 * for the specification of metadata blocks and their lengths.
 *
 * <p>If you are writing the FLAC data to a file via callbacks, make sure it
 * is open for update.  This is because
 * after the first encoding pass, the encoder will try to seek back to the
 * beginning of the stream, to the STREAMINFO block, to write some data
 * there.  (If using StreamEncoder.init*File(), the file is managed internally.)
 *
 * <p>The "set" functions may only be called when the encoder is in the
 * state StreamEncoderState.UNINITIALIZED, i.e. after
 * new StreamEncoder() or StreamEncoder.finish(), but
 * before StreamEncoder.init*().  Otherwise, IllegalStateException is thrown.
 *
 * <p>StreamEncoder.finish() resets all settings to the constructor
 * defaults.
 *
 */
public final class StreamEncoder implements AutoCloseable {
    static final int MAX_APODIZATION_FUNCTIONS = 32;
    private static final int MAX_EXTRA_RESIDUAL_BPS = 4;
    /**
     * Number of samples that will be overread to watch for end of stream.  By
     * 'overread', we mean that the StreamEncoder.process*() calls will
     * always try to read blocksize+1 samples before encoding a block, so that
     * even if the stream has a total sample count that is an integral multiple
     * of the blocksize, we will still notice when we are encoding the last
     * block.  This is needed, for example, to correctly set the end-of-stream
     * marker in Ogg FLAC.
     */
    private static final int OVERREAD_ = 1;
    private static final int md5_offset = Format.STREAM_METADATA_HEADER_LENGTH
            + (Format.STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN + Format.STREAM_METADATA_STREAMINFO_CHANNELS_LEN
                    + Format.STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN) / Byte.SIZE;
    private static final int total_samples_byte_offset = Format.STREAM_METADATA_HEADER_LENGTH
            + (Format.STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN + Format.STREAM_METADATA_STREAMINFO_CHANNELS_LEN
                    + Format.STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN - 4) / Byte.SIZE;
    private static final int min_framesize_offset = Format.STREAM_METADATA_HEADER_LENGTH
            + (Format.STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN
                    + Format.STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN) / Byte.SIZE;

    /** the # of bytes in the 1st packet that precede the STREAMINFO */
    static final int FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH = OggMapping.OGG_MAPPING_PACKET_TYPE_LENGTH
            + OggMapping.OGG_MAPPING_MAGIC_LENGTH + OggMapping.OGG_MAPPING_VERSION_MAJOR_LENGTH
            + OggMapping.OGG_MAPPING_VERSION_MINOR_LENGTH + OggMapping.OGG_MAPPING_NUM_HEADERS_LENGTH
            + Format.STREAM_SYNC_LENGTH;

    private static final class ApplyApodizationState {
        int a, b = 1, c;
        ApodizationSpecification currentApodization;
        final double[] autocRoot = new double[Format.MAX_LPC_ORDER + 1];
        double[] autoc;
        int maxLpcOrder, guessLpcOrder;
    }

    private enum EncoderStateHint {
        ENCODER_IN_MAGIC,
        ENCODER_IN_METADATA,
        ENCODER_IN_AUDIO
    }

    private record CompressionLevels(boolean doMidSideStereo, boolean looseMidSideStereo, int maxLpcOrder,
            int qlpCoeffPrecision, boolean doQlpCoeffPrecSearch, boolean doEscapeCoding,
            boolean doExhaustiveModelSearch, int minResidualPartitionOrder, int maxResidualPartitionOrder,
            int riceParameterSearchDist, String apodization) {
    }

    private static final CompressionLevels[] compression_levels_ = {
            new CompressionLevels(false, false, 0, 0, false, false, false, 0, 3, 0, "tukey(5e-1)"),
            new CompressionLevels(true, true, 0, 0, false, false, false, 0, 3, 0, "tukey(5e-1)"),
            new CompressionLevels(true, false, 0, 0, false, false, false, 0, 3, 0, "tukey(5e-1)"),
            new CompressionLevels(false, false, 6, 0, false, false, false, 0, 4, 0, "tukey(5e-1)"),
            new CompressionLevels(true, true, 8, 0, false, false, false, 0, 4, 0, "tukey(5e-1)"),
            new CompressionLevels(true, false, 8, 0, false, false, false, 0, 5, 0, "tukey(5e-1)"),
            new CompressionLevels(true, false, 8, 0, false, false, false, 0, 6, 0, "subdivide_tukey(2)"),
            new CompressionLevels(true, false, 12, 0, false, false, false, 0, 6, 0, "subdivide_tukey(2)"),
            new CompressionLevels(true, false, 12, 0, false, false, false, 0, 6, 0, "subdivide_tukey(3)")};

    StreamEncoderState state = StreamEncoderState.UNINITIALIZED;
    private boolean verify;
    private boolean streamableSubset;
    private boolean doMidSideStereo;
    private boolean looseMidSideStereo;
    private int channels;
    private int bitsPerSample;
    private int sampleRate;
    private int blocksize;
    private List<ApodizationSpecification> apodizations;
    private int maxLpcOrder;
    private int qlpCoeffPrecision;
    private boolean doQlpCoeffPrecSearch;
    private boolean doExhaustiveModelSearch;
    private int minResidualPartitionOrder;
    private int maxResidualPartitionOrder;
    private long totalSamplesEstimate;
    private boolean limitMinBitrate;
    private StreamMetadata[] metadataBlocks;
    private long streamInfoOffset, seekTableOffset, audioOffset;
    private OggEncoderAspect oggEncoderAspect;
    private int oggSerialNumber;
    private int oggNumMetadata;

    private int[][] pending;
    /** the pre-computed floating-point window for each apodization function */
    private float[][] windows;
    /** the integerSignal[] * current window[] */
    private float[] windowedSignal;
    /** the current frame being worked on */
    private BitWriter frame;
    /** rounded number of frames the encoder will use before trying both independent and mid/side frames again */
    private int looseMidSideStereoFrames;
    /** number of frames using the current channel assignment */
    private int looseMidSideStereoFrameCount;
    private ChannelAssignment lastChannelAssignment = ChannelAssignment.INDEPENDENT;
    /** scratchpad for STREAMINFO as it is built */
    private final StreamInfo streamInfo = new StreamInfo();
    /** reference to the seek table in metadataBlocks */
    private SeekTable seekTable;
    private int currentSampleNumber;
    private int currentFrameNumber;
    private final Md5Context md5 = new Md5Context();
    private boolean isOgg;
    /** currently only needed for Ogg FLAC */
    private ReadCallback readCallback;
    private SeekCallback seekCallback;
    private TellCallback tellCallback;
    private WriteCallback writeCallback;
    private MetadataCallback metadataCallback;
    private ProgressCallback progressCallback;
    private int firstSeekpointToCheck;
    /** only used when encoding to a file */
    private RandomAccessFile file;
    private boolean standardOutput;
    private long bytesWritten;
    private long samplesWritten;
    private int framesWritten;
    private int totalFramesEstimate;
    private StreamDecoder verifyDecoder;
    private EncoderStateHint verifyStateHint;
    private boolean verifyNeedsMagicHack;
    private byte[] verifyOutput;
    private int verifyOutputOffset;
    private VerifyErrorStats verifyErrorStats = new VerifyErrorStats(0, 0, 0, 0, 0, 0);

    public record VerifyErrorStats(long absoluteSample, int frameNumber, int channel, int sample, int expected,
            int got) {
    }

    /**
     * Create a new stream encoder instance.  The instance is created with
     *  default settings; see the individual StreamEncoder.set*()
     *  functions for each setting's default.
     */
    public StreamEncoder() {
        setDefaults();
    }

    /**
     * Set the serial number for the FLAC stream to use in the Ogg container.
     *
     * <p>This does not need to be set for native FLAC encoding.
     *
     * <p>It is recommended to set a serial number explicitly as the default of '0'
     * may collide with other streams.
     *
     * <p>Default: {@code 0}
     *
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setOggSerialNumber(long value) {
        requireUninitialized();
        /* can't check isOgg since that's not set until init time */
        oggSerialNumber = (int) value;
    }

    /**
     * Set the "verify" flag.  If {@code true}, the encoder will verify it's own
     *  encoded output by feeding it through an internal decoder and comparing
     *  the original signal against the decoded signal.  If a mismatch occurs,
     *  the process call will return {@code false}.  Note that this will slow the
     *  encoding process by the extra time required for decoding and comparison.
     *
     * <p>Default: {@code false}
     *
     * @param value Flag value (see above).
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setVerify(boolean value) {
        requireUninitialized();
        verify = value;
    }

    /**
     * Set the <A HREF="https://xiph.org/flac/format.html#subset">Subset</A> flag.  If {@code true},
     *  the encoder will comply with the Subset and will check the
     *  settings during StreamEncoder.init*() to see if all settings
     *  comply.  If {@code false}, the settings may take advantage of the full
     *  range that the format allows.
     *
     *  Make sure you know what it entails before setting this to {@code false}.
     *
     * <p>Default: {@code true}
     *
     * @param value Flag value (see above).
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setStreamableSubset(boolean value) {
        requireUninitialized();
        streamableSubset = value;
    }

    /**
     * Set the number of channels to be encoded.
     *
     * <p>Default: {@code 2}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setChannels(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("channels");
        channels = value;
    }

    /**
     * Set the sample resolution of the input to be encoded.
     *
     * <p>Do not feed the encoder data that is wider than the value you
     * set here or you will generate an invalid stream.
     *
     * <p>Default: {@code 16}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setBitsPerSample(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("bitsPerSample");
        bitsPerSample = value;
    }

    /**
     * Set the sample rate (in Hz) of the input to be encoded.
     *
     * <p>Default: {@code 44100}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setSampleRate(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("sampleRate");
        sampleRate = value;
    }

    /**
     * Set the compression level
     *
     * The compression level is roughly proportional to the amount of effort
     * the encoder expends to compress the file.  A higher level usually
     * means more computation but higher compression.  The default level is
     * suitable for most applications.
     *
     * Currently the levels range from {@code 0} (fastest, least compression) to
     * {@code 8} (slowest, most compression).  A value larger than {@code 8} will be
     * treated as {@code 8}.
     *
     * This function automatically calls the following other {@code _set_}
     * functions with appropriate values, so the client does not need to
     * unless it specifically wants to override them:
     * - StreamEncoder.setDoMidSideStereo()
     * - StreamEncoder.setLooseMidSideStereo()
     * - StreamEncoder.setApodization()
     * - StreamEncoder.setMaxLpcOrder()
     * - StreamEncoder.setQlpCoeffPrecision()
     * - StreamEncoder.setDoQlpCoeffPrecSearch()
     * - StreamEncoder.setDoEscapeCoding()
     * - StreamEncoder.setDoExhaustiveModelSearch()
     * - StreamEncoder.setMinResidualPartitionOrder()
     * - StreamEncoder.setMaxResidualPartitionOrder()
     * - StreamEncoder.setRiceParameterSearchDist()
     *
     * The actual values set for each level are:
     * <table><caption>Compression level</caption>
     * <tr>
     *  <td><b>level</b></td>
     *  <td>do mid-side stereo</td>
     *  <td>loose mid-side stereo</td>
     *  <td>apodization</td>
     *  <td>max lpc order</td>
     *  <td>qlp coeff precision</td>
     *  <td>qlp coeff prec search</td>
     *  <td>escape coding</td>
     *  <td>exhaustive model search</td>
     *  <td>min residual partition order</td>
     *  <td>max residual partition order</td>
     *  <td>rice parameter search dist</td>
     * </tr>
     * <tr>  <td><b>0</b></td> <td>false</td> <td>false</td> <td>tukey(0.5)</td>         <td>0</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>3</td> <td>0</td> </tr>
     * <tr>  <td><b>1</b></td> <td>true</td>  <td>true</td>  <td>tukey(0.5)</td>         <td>0</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>3</td> <td>0</td> </tr>
     * <tr>  <td><b>2</b></td> <td>true</td>  <td>false</td> <td>tukey(0.5)</td>         <td>0</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>3</td> <td>0</td> </tr>
     * <tr>  <td><b>3</b></td> <td>false</td> <td>false</td> <td>tukey(0.5)</td>         <td>6</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>4</td> <td>0</td> </tr>
     * <tr>  <td><b>4</b></td> <td>true</td>  <td>true</td>  <td>tukey(0.5)</td>         <td>8</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>4</td> <td>0</td> </tr>
     * <tr>  <td><b>5</b></td> <td>true</td>  <td>false</td> <td>tukey(0.5)</td>         <td>8</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>5</td> <td>0</td> </tr>
     * <tr>  <td><b>6</b></td> <td>true</td>  <td>false</td> <td>subdivide_tukey(2)</td> <td>8</td>  <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>6</td> <td>0</td> </tr>
     * <tr>  <td><b>7</b></td> <td>true</td>  <td>false</td> <td>subdivide_tukey(2)</td> <td>12</td> <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>6</td> <td>0</td> </tr>
     * <tr>  <td><b>8</b></td> <td>true</td>  <td>false</td> <td>subdivide_tukey(3)</td> <td>12</td> <td>0</td> <td>false</td> <td>false</td> <td>false</td> <td>0</td> <td>6</td> <td>0</td> </tr>
     * </table>
     *
     * <p>Default: {@code 5}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setCompressionLevel(int value) {
        requireUninitialized();
        int lastLevel = compression_levels_.length - 1;
        int level = Integer.compareUnsigned(value, lastLevel) > 0 ? lastLevel : value;
        CompressionLevels compressionLevel = compression_levels_[level];
        setDoMidSideStereo(compressionLevel.doMidSideStereo);
        setLooseMidSideStereo(compressionLevel.looseMidSideStereo);
        setApodization(compressionLevel.apodization);
        setMaxLpcOrder(compressionLevel.maxLpcOrder);
        setQlpCoeffPrecision(compressionLevel.qlpCoeffPrecision);
        setDoQlpCoeffPrecSearch(compressionLevel.doQlpCoeffPrecSearch);
        setDoEscapeCoding(compressionLevel.doEscapeCoding);
        setDoExhaustiveModelSearch(compressionLevel.doExhaustiveModelSearch);
        setMinResidualPartitionOrder(compressionLevel.minResidualPartitionOrder);
        setMaxResidualPartitionOrder(compressionLevel.maxResidualPartitionOrder);
        setRiceParameterSearchDist(compressionLevel.riceParameterSearchDist);
    }

    /**
     * Set the blocksize to use while encoding.
     *
     * The number of samples to use per frame.  Use {@code 0} to let the encoder
     * estimate a blocksize; this is usually best.
     *
     * <p>Default: {@code 0}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setBlocksize(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("blocksize");
        blocksize = value;
    }

    /**
     * Set to {@code true} to enable mid-side encoding on stereo input.  The
     *  number of channels must be 2 for this to have any effect.  Set to
     *  {@code false} to use only independent channel coding.
     *
     * <p>Default: {@code true}
     *
     * @param value Flag value (see above).
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setDoMidSideStereo(boolean value) {
        requireUninitialized();
        doMidSideStereo = value;
    }

    /**
     * Set to {@code true} to enable adaptive switching between mid-side and
     *  left-right encoding on stereo input.  Set to {@code false} to use
     *  exhaustive searching.  Setting this to {@code true} requires
     *  StreamEncoder.setDoMidSideStereo() to also be set to
     *  {@code true} in order to have any effect.
     *
     * <p>Default: {@code false}
     *
     * @param value Flag value (see above).
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setLooseMidSideStereo(boolean value) {
        requireUninitialized();
        looseMidSideStereo = value;
    }

    /**
     * Sets the apodization function(s) the encoder will use when windowing
     *  audio data for LPC analysis.
     *
     * The {@code specification} is a plain ASCII string which specifies exactly
     * which functions to use.  There may be more than one (up to 32),
     * separated by {@code ';'} characters.  Some functions take one or more
     * comma-separated arguments in parentheses.
     *
     * The available functions are {@code bartlett}, {@code bartlett_hann},
     * {@code blackman}, {@code blackman_harris_4term_92db}, {@code connes}, {@code flattop},
     * {@code gauss(STDDEV)}, {@code hamming}, {@code hann}, {@code kaiser_bessel}, {@code nuttall},
     * {@code rectangle}, {@code triangle}, {@code tukey(P)}, {@code partial_tukey(n[/ov[/P]])},
     * {@code punchout_tukey(n[/ov[/P]])}, {@code subdivide_tukey(n[/P])}, {@code welch}.
     *
     * For {@code gauss(STDDEV)}, STDDEV specifies the standard deviation
     * (0&lt;STDDEV&lt;=0.5).
     *
     * For {@code tukey(P)}, P specifies the fraction of the window that is
     * tapered (0&lt;=P&lt;=1).  P=0 corresponds to {@code rectangle} and P=1
     * corresponds to {@code hann}.
     *
     * Specifying {@code partial_tukey} or {@code punchout_tukey} works a little
     * different. These do not specify a single apodization function, but
     * a series of them with some overlap. partial_tukey specifies a series
     * of small windows (all treated separately) while punchout_tukey
     * specifies a series of windows that have a hole in them. In this way,
     * the predictor is constructed with only a part of the block, which
     * helps in case a block consists of dissimilar parts.
     *
     * The three parameters that can be specified for the functions are
     * n, ov and P. n is the number of functions to add, ov is the overlap
     * of the windows in case of partial_tukey and the overlap in the gaps
     * in case of punchout_tukey. P is the fraction of the window that is
     * tapered, like with a regular tukey window. The function can be
     * specified with only a number, a number and an overlap, or a number
     * an overlap and a P, for example, partial_tukey(3), partial_tukey(3/0.3)
     * and partial_tukey(3/0.3/0.5) are all valid. ov should be smaller than 1
     * and can be negative.
     *
     * subdivide_tukey(n) is a more efficient reimplementation of
     * partial_tukey and punchout_tukey taken together, recycling as much data
     * as possible. It combines all possible non-redundant partial_tukey(n)
     * and punchout_tukey(n) up to the n specified. Specifying
     * subdivide_tukey(3) is equivalent to specifying tukey, partial_tukey(2),
     * partial_tukey(3) and punchout_tukey(3), specifying subdivide_tukey(5)
     * equivalently adds partial_tukey(4), punchout_tukey(4), partial_tukey(5)
     * and punchout_tukey(5). To be able to reuse data as much as possible,
     * the tukey taper is taken equal for all windows, and the P specified is
     * applied for the smallest used window. In other words,
     * subdivide_tukey(2/0.5) results in a taper equal to that of tukey(0.25)
     * and subdivide_tukey(5) in a taper equal to that of tukey(0.1). The
     * default P for subdivide_tukey when none is specified is 0.5.
     *
     * Example specifications are {@code "blackman"} or
     * {@code "hann;triangle;tukey(0.5);tukey(0.25);tukey(0.125)"}
     *
     * Any function that is specified erroneously is silently dropped.  Up
     * to 32 functions are kept, the rest are dropped.  If the specification
     * is empty the encoder defaults to {@code "tukey(0.5)"}.
     *
     * When more than one function is specified, then for every subframe the
     * encoder will try each of them separately and choose the window that
     * results in the smallest compressed subframe.
     *
     * Note that each function specified causes the encoder to occupy a
     * floating point array in which to store the window.
     *
     * <p>Default: {@code "tukey(0.5)"}
     *
     * @param specification See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */

    /* @@@@add to tests */
    public void setApodization(String specification) {
        requireUninitialized();
        apodizations = ApodizationSpecification.parse(Objects.requireNonNull(specification));
    }

    /**
     * Set the maximum LPC order, or {@code 0} to use only the fixed predictors.
     *
     * <p>Default: {@code 8}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setMaxLpcOrder(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("maxLpcOrder");
        maxLpcOrder = value;
    }

    /**
     * Set the precision, in bits, of the quantized linear predictor
     *  coefficients, or {@code 0} to let the encoder select it based on the
     *  blocksize.
     *
     * <p>Default: {@code 0}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setQlpCoeffPrecision(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("qlpCoeffPrecision");
        qlpCoeffPrecision = value;
    }

    /**
     * Set to {@code false} to use only the specified quantized linear predictor
     *  coefficient precision, or {@code true} to search neighboring precision
     *  values and use the best one.
     *
     * <p>Default: {@code false}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setDoQlpCoeffPrecSearch(boolean value) {
        requireUninitialized();
        doQlpCoeffPrecSearch = value;
    }

    /**
     * Deprecated.  Setting this value has no effect.
     *
     * <p>Default: {@code false}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setDoEscapeCoding(boolean value) {
        requireUninitialized();
    }

    /**
     * Set to {@code false} to let the encoder estimate the best model order
     *  based on the residual signal energy, or {@code true} to force the
     *  encoder to evaluate all order models and select the best.
     *
     * <p>Default: {@code false}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setDoExhaustiveModelSearch(boolean value) {
        requireUninitialized();
        doExhaustiveModelSearch = value;
    }

    /**
     * Set the minimum partition order to search when coding the residual.
     *  This is used in tandem with
     *  StreamEncoder.setMaxResidualPartitionOrder().
     *
     *  The partition order determines the context size in the residual.
     *  The context size will be approximately {@code blocksize / (2 ^ order)}.
     *
     *  Set both min and max values to {@code 0} to force a single context,
     *  whose Rice parameter is based on the residual signal variance.
     *  Otherwise, set a min and max order, and the encoder will search
     *  all orders, using the mean of each context for its Rice parameter,
     *  and use the best.
     *
     * <p>Default: {@code 0}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setMinResidualPartitionOrder(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("minResidualPartitionOrder");
        minResidualPartitionOrder = value;
    }

    /**
     * Set the maximum partition order to search when coding the residual.
     *  This is used in tandem with
     *  StreamEncoder.setMinResidualPartitionOrder().
     *
     *  The partition order determines the context size in the residual.
     *  The context size will be approximately {@code blocksize / (2 ^ order)}.
     *
     *  Set both min and max values to {@code 0} to force a single context,
     *  whose Rice parameter is based on the residual signal variance.
     *  Otherwise, set a min and max order, and the encoder will search
     *  all orders, using the mean of each context for its Rice parameter,
     *  and use the best.
     *
     * <p>Default: {@code 5}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setMaxResidualPartitionOrder(int value) {
        requireUninitialized();
        if (value < 0)
            throw new IllegalArgumentException("maxResidualPartitionOrder");
        maxResidualPartitionOrder = value;
    }

    /**
     * Deprecated.  Setting this value has no effect.
     *
     * <p>Default: {@code 0}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setRiceParameterSearchDist(int value) {
        requireUninitialized();
    }

    /**
     * Set an estimate of the total samples that will be encoded.
     *  This is merely an estimate and may be set to {@code 0} if unknown.
     *  This value will be written to the STREAMINFO block before encoding,
     *  and can remove the need for the caller to rewrite the value later
     *  if the value is known before encoding.
     *
     * <p>Default: {@code 0}
     *
     * @param value See above.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setTotalSamplesEstimate(long value) {
        requireUninitialized();
        totalSamplesEstimate = Long.compareUnsigned(value,
                ((1L << Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN) - 1)) > 0
                        ? ((1L << Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN) - 1) : value;
    }

    /**
     * Set the metadata blocks to be emitted to the stream before encoding.
     *  A value of {@code null} or an empty array implies no metadata; otherwise, supply an
     *  array of metadata blocks.  The blocks are mutable since
     *  the encoder may need to change the {@code isLast} flag inside them, and
     *  in some cases update seek point offsets.  Otherwise, the encoder will
     *  not modify the blocks.
     *
     * <p>The encoder stores only copies of the references in the {@code metadata} array.
     *
     * <p>The STREAMINFO block is always written and no STREAMINFO block may
     * occur in the supplied array.
     *
     * <p>By default the encoder does not create a SEEKTABLE.  If one is supplied
     * in the {@code metadata} array, but the client has specified that it does not
     * support seeking, then the SEEKTABLE will be written verbatim.  However
     * by itself this is not very useful as the client will not know the stream
     * offsets for the seekpoints ahead of time.  In order to get a proper
     * seektable the client must support seeking.  See next note.
     *
     * <p>SEEKTABLE blocks are handled specially.  Since you will not know
     * the values for the seek point stream offsets, you should pass in
     * a SEEKTABLE 'template', that is, a SEEKTABLE object with the
     * required sample numbers (or placeholder points), with {@code 0} for the
     * {@code frameSamples} and {@code streamOffset} fields for each point.  If the
     * client has specified that it supports seeking by providing a seek
     * callback to StreamEncoder.initStream() or both seek AND read
     * callback to StreamEncoder.initOggStream() (or by using
     * StreamEncoder.init*File()),
     * then while it is encoding the encoder will fill the stream offsets in
     * for you and when encoding is finished, it will seek back and write the
     * real values into the SEEKTABLE block in the stream.  There are helper
     * routines for manipulating seektable template blocks; see SeekTable.template*().  If the client does
     * not support seeking, the SEEKTABLE will have inaccurate offsets which
     * will slow down or remove the ability to seek in the FLAC stream.
     *
     * <p>The encoder instance <b>will</b> modify the first {@code SEEKTABLE} block
     * as it transforms the template to a valid seektable while encoding.
     *
     * <p>A VORBIS_COMMENT block may be supplied.  The vendor string in it
     * will be ignored.  libFLAC will use it's own vendor string. libFLAC
     * will not modify the passed-in VORBIS_COMMENT's vendor string, it
     * will simply write it's own into the stream.  If no VORBIS_COMMENT
     * block is present in the {@code metadata} array, libFLAC will write an
     * empty one, containing only the vendor string.
     *
     * <p>The Ogg FLAC mapping requires that the VORBIS_COMMENT block be
     * the second metadata block of the stream.  The encoder already supplies
     * the STREAMINFO block automatically.  If {@code metadata} does not contain a
     * VORBIS_COMMENT block, the encoder will supply that too.  Otherwise, if
     * {@code metadata} does contain a VORBIS_COMMENT block and it is not the
     * first, the init function will reorder the stored metadata by moving the
     * VORBIS_COMMENT block to the front; the relative ordering of the other
     * blocks will remain as they were.
     *
     * <p>The Ogg FLAC mapping limits the number of metadata blocks per
     * stream to {@code 65535}.  If {@code metadata.length} exceeds this the function will
     * return {@code false}.
     *
     * <p>Default: empty array
     *
     * @param metadata See above.
     * @return false if metadata.length &gt; 65535, else true.
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public boolean setMetadata(StreamMetadata[] metadata) {
        requireUninitialized();
        metadataBlocks = metadata == null ? new StreamMetadata[0] : metadata.clone();
        if (metadataBlocks.length >= 1 << OggMapping.OGG_MAPPING_NUM_HEADERS_LEN)
            return false;
        oggNumMetadata = metadataBlocks.length;
        return true;
    }

    /**
     * Set to {@code true} to make the encoder not output frames which contain
     *  only constant subframes. This is beneficial for streaming
     *  applications: very small frames can cause problems with buffering
     *  as bitrates can drop as low 1kbit/s for CDDA audio encoded within
     *  subset. The minimum bitrate for a FLAC file encoded with this
     *  function used is raised to 1bit/sample (i.e. 48kbit/s for 48kHz
     *  material).
     *
     * <p>Default: {@code false}
     *
     * @param value Flag value (see above).
     * @throws IllegalStateException if the encoder is already initialized.
     */
    public void setLimitMinBitrate(boolean value) {
        requireUninitialized();
        limitMinBitrate = value;
    }

    /**
     * Get the current encoder state.
     *
     * @return The current encoder state.
     */
    public StreamEncoderState getState() {
        return state;
    }

    /**
     * Get the state of the verify stream decoder.
     *  Useful when the stream encoder state is
     *  {@code StreamEncoderState.VERIFY_DECODER_ERROR}.
     *
     * @return The verify stream decoder state.
     */
    public StreamDecoderState getVerifyDecoderState() {
        return verifyDecoder == null ? StreamDecoderState.UNINITIALIZED : verifyDecoder.getState();
    }

    /**
     * Get the current encoder state as a String.
     *  This version automatically resolves
     *  {@code StreamEncoderState.VERIFY_DECODER_ERROR} by getting the
     *  verify decoder's state.
     *
     * @return
     *    The encoder state as a String.
     */
    public String getResolvedStateString() {
        return state != StreamEncoderState.VERIFY_DECODER_ERROR ? "FLAC__STREAM_ENCODER_" + state.name()
                : verifyDecoder.getResolvedStateString();
    }

    /**
     * Get relevant values about the nature of a verify decoder error.
     *  Useful when the stream encoder state is
     *  {@code StreamEncoderState.VERIFY_DECODER_ERROR}.
     */
    public VerifyErrorStats getVerifyDecoderErrorStats() {
        return verifyErrorStats;
    }

    /**
     * Get the "verify" flag.
     *
     * @return See StreamEncoder.setVerify().
     */
    public boolean getVerify() {
        return verify;
    }

    /**
     * Get the <A HREF="https://xiph.org/flac/format.html#subset">Subset</A> flag.
     *
     * @return See StreamEncoder.setStreamableSubset().
     */
    public boolean getStreamableSubset() {
        return streamableSubset;
    }

    /**
     * Get the number of input channels being processed.
     *
     * @return See StreamEncoder.setChannels().
     */
    public int getChannels() {
        return channels;
    }

    /**
     * Get the input sample resolution setting.
     *
     * @return See StreamEncoder.setBitsPerSample().
     */
    public int getBitsPerSample() {
        return bitsPerSample;
    }

    /**
     * Get the input sample rate setting.
     *
     * @return See StreamEncoder.setSampleRate().
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Get the blocksize setting.
     *
     * @return See StreamEncoder.setBlocksize().
     */
    public int getBlocksize() {
        return blocksize;
    }

    /**
     * Get the "mid/side stereo coding" flag.
     *
     * @return See StreamEncoder.getDoMidSideStereo().
     */
    public boolean getDoMidSideStereo() {
        return doMidSideStereo;
    }

    /**
     * Get the "adaptive mid/side switching" flag.
     *
     * @return See StreamEncoder.setLooseMidSideStereo().
     */
    public boolean getLooseMidSideStereo() {
        return looseMidSideStereo;
    }

    /**
     * Get the maximum LPC order setting.
     *
     * @return See StreamEncoder.setMaxLpcOrder().
     */
    public int getMaxLpcOrder() {
        return maxLpcOrder;
    }

    /**
     * Get the quantized linear predictor coefficient precision setting.
     *
     * @return See StreamEncoder.setQlpCoeffPrecision().
     */
    public int getQlpCoeffPrecision() {
        return qlpCoeffPrecision;
    }

    /**
     * Get the qlp coefficient precision search flag.
     *
     * @return See StreamEncoder.setDoQlpCoeffPrecSearch().
     */
    public boolean getDoQlpCoeffPrecSearch() {
        return doQlpCoeffPrecSearch;
    }

    /**
     * Get the "escape coding" flag.
     *
     * @return See StreamEncoder.setDoEscapeCoding().
     */
    public boolean getDoEscapeCoding() {
        return false;
    }

    /**
     * Get the exhaustive model search flag.
     *
     * @return See StreamEncoder.setDoExhaustiveModelSearch().
     */
    public boolean getDoExhaustiveModelSearch() {
        return doExhaustiveModelSearch;
    }

    /**
     * Get the minimum residual partition order setting.
     *
     * @return See StreamEncoder.setMinResidualPartitionOrder().
     */
    public int getMinResidualPartitionOrder() {
        return minResidualPartitionOrder;
    }

    /**
     * Get maximum residual partition order setting.
     *
     * @return See StreamEncoder.setMaxResidualPartitionOrder().
     */
    public int getMaxResidualPartitionOrder() {
        return maxResidualPartitionOrder;
    }

    /**
     * Get the Rice parameter search distance setting.
     *
     * @return See StreamEncoder.setRiceParameterSearchDist().
     */
    public int getRiceParameterSearchDist() {
        return 0;
    }

    /**
     * Get the previously set estimate of the total samples to be encoded.
     *  The encoder merely mimics back the value given to
     *  StreamEncoder.setTotalSamplesEstimate() since it has no
     *  other way of knowing how many samples the client will encode.
     *
     * @return See StreamEncoder.getTotalSamplesEstimate().
     */
    public long getTotalSamplesEstimate() {
        return totalSamplesEstimate;
    }

    /**
     * Get the "limitMinBitrate" flag.
     *
     * @return See StreamEncoder.setLimitMinBitrate().
     */
    public boolean getLimitMinBitrate() {
        return limitMinBitrate;
    }

    /**
     * Initialize the encoder instance to encode native FLAC streams.
     *
     *  This flavor of initialization sets up the encoder to encode to a
     *  native FLAC stream. I/O is performed via callbacks to the client.
     *  For encoding to a plain file via filename or open {@code RandomAccessFile},
     *  StreamEncoder.initFile() provides a simpler interface.
     *
     *  This function should be called after new StreamEncoder() and
     *  StreamEncoder.set*() but before StreamEncoder.process()
     *  or StreamEncoder.processInterleaved().
     *  initialization succeeded.
     *   The call to StreamEncoder.initStream() currently will also
     *  immediately call the write callback several times, once with the {@code fLaC}
     *  signature, and once for each encoded metadata block.
     *
     * @param writeCallback See StreamEncoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param seekCallback See StreamEncoder.SeekCallback.  This
     *                            callback may be {@code null} if seeking is not
     *                            supported.  The encoder uses seeking to go back
     *                            and write some some stream statistics to the
     *                            STREAMINFO block; this is recommended but not
     *                            necessary to create a valid FLAC stream.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback} must also be supplied.
     *                            Alternatively, a dummy seek callback that just
     *                            returns {@code StreamEncoderSeekStatus.UNSUPPORTED}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the encoder.
     * @param tellCallback See StreamEncoder.TellCallback.  This
     *                            callback may be {@code null} if seeking is not
     *                            supported.  If {@code seekCallback} is {@code null} then
     *                            this argument will be ignored.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback} must also be supplied.
     *                            Alternatively, a dummy tell callback that just
     *                            returns {@code -1}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the encoder.
     * @param metadataCallback See StreamEncoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.  If the client provides a seek callback,
     *                            this function is not necessary as the encoder
     *                            will automatically seek back and update the
     *                            STREAMINFO block.  It may also be {@code null} if the
     *                            client does not support seeking, since it will
     *                            have no way of going back to update the
     *                            STREAMINFO.  However the client can still supply
     *                            a callback if it would like to know the details
     *                            from the STREAMINFO.
     * @return {@code StreamEncoderInitStatus.OK} if initialization was successful;
     *    see StreamEncoderInitStatus for the meanings of other return values.
     */
    public StreamEncoderInitStatus initStream(WriteCallback writeCallback, SeekCallback seekCallback,
            TellCallback tellCallback, MetadataCallback metadataCallback) throws IOException {
        return initStreamInternal(null, writeCallback, seekCallback, tellCallback, metadataCallback, false);
    }

    /**
     * Initialize the encoder instance to encode Ogg FLAC streams.
     *
     *  This flavor of initialization sets up the encoder to encode to a FLAC
     *  stream in an Ogg container.  I/O is performed via callbacks to the
     *  client.  For encoding to a plain file via filename or open {@code RandomAccessFile},
     *  StreamEncoder.initOggFile() provides a simpler interface.
     *
     *  This function should be called after new StreamEncoder() and
     *  StreamEncoder.set*() but before StreamEncoder.process()
     *  or StreamEncoder.processInterleaved().
     *  initialization succeeded.
     *   The call to StreamEncoder.initOggStream() currently will also
     *  immediately call the write callback several times to write the metadata
     *  packets.
     *
     * @param readCallback See StreamEncoder.ReadCallback.  This
     *                            callback must not be {@code null} if {@code seekCallback}
     *                            is non-null since they are both needed to be
     *                            able to write data back to the Ogg FLAC stream
     *                            in the post-encode phase.
     * @param writeCallback See StreamEncoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param seekCallback See StreamEncoder.SeekCallback.  This
     *                            callback may be {@code null} if seeking is not
     *                            supported.  The encoder uses seeking to go back
     *                            and write some some stream statistics to the
     *                            STREAMINFO block; this is recommended but not
     *                            necessary to create a valid FLAC stream.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback} must also be supplied.
     *                            Alternatively, a dummy seek callback that just
     *                            returns {@code StreamEncoderSeekStatus.UNSUPPORTED}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the encoder.
     * @param tellCallback See StreamEncoder.TellCallback.  This
     *                            callback may be {@code null} if seeking is not
     *                            supported.  If {@code seekCallback} is {@code null} then
     *                            this argument will be ignored.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback} must also be supplied.
     *                            Alternatively, a dummy tell callback that just
     *                            returns {@code -1}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the encoder.
     * @param metadataCallback See StreamEncoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.  If the client provides a seek callback,
     *                            this function is not necessary as the encoder
     *                            will automatically seek back and update the
     *                            STREAMINFO block.  It may also be {@code null} if the
     *                            client does not support seeking, since it will
     *                            have no way of going back to update the
     *                            STREAMINFO.  However the client can still supply
     *                            a callback if it would like to know the details
     *                            from the STREAMINFO.
     * @return {@code StreamEncoderInitStatus.OK} if initialization was successful;
     *    see StreamEncoderInitStatus for the meanings of other return values.
     */
    public StreamEncoderInitStatus initOggStream(ReadCallback readCallback, WriteCallback writeCallback,
            SeekCallback seekCallback, TellCallback tellCallback, MetadataCallback metadataCallback)
            throws IOException {
        return initStreamInternal(readCallback, writeCallback, seekCallback, tellCallback, metadataCallback, true);
    }

    /**
     * Initialize the encoder instance to encode native FLAC files.
     *
     *  This flavor of initialization sets up the encoder to encode to a
     *  plain native FLAC file.  For other streams, you must use
     *  StreamEncoder.initStream() and provide callbacks for the I/O.
     *
     *  This function should be called after new StreamEncoder() and
     *  StreamEncoder.set*() but before StreamEncoder.process()
     *  or StreamEncoder.processInterleaved().
     *  initialization succeeded.
     *  @param file An open file.  The file should have been opened
     *                            for reading and writing and rewound.  The file
     *                            becomes owned by the encoder and should not be
     *                            manipulated by the client while encoding.
     *                            It will be closed
     *                            when StreamEncoder.finish() is called.
     * @param progressCallback See StreamEncoder.ProgressCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @return {@code StreamEncoderInitStatus.OK} if initialization was successful;
     *    see StreamEncoderInitStatus for the meanings of other return values.
     */
    public StreamEncoderInitStatus initFile(RandomAccessFile file, ProgressCallback progressCallback)
            throws IOException {
        return initFileInternal(Objects.requireNonNull(file), progressCallback, false);
    }

    /**
     * Initialize the encoder instance to encode Ogg FLAC files.
     *
     *  This flavor of initialization sets up the encoder to encode to a
     *  plain Ogg FLAC file.  For other streams, you must use
     *  StreamEncoder.initOggStream() and provide callbacks for the I/O.
     *
     *  This function should be called after new StreamEncoder() and
     *  StreamEncoder.set*() but before StreamEncoder.process()
     *  or StreamEncoder.processInterleaved().
     *  initialization succeeded.
     *  @param file An open file.  The file should have been opened
     *                            for reading and writing and rewound.  The file
     *                            becomes owned by the encoder and should not be
     *                            manipulated by the client while encoding.
     *                            It will be closed
     *                            when StreamEncoder.finish() is called.
     * @param progressCallback See StreamEncoder.ProgressCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @return {@code StreamEncoderInitStatus.OK} if initialization was successful;
     *    see StreamEncoderInitStatus for the meanings of other return values.
     */
    public StreamEncoderInitStatus initOggFile(RandomAccessFile file, ProgressCallback progressCallback)
            throws IOException {
        return initFileInternal(Objects.requireNonNull(file), progressCallback, true);
    }

    /**
     * Initialize the encoder instance to encode native FLAC files.
     *
     *  This flavor of initialization sets up the encoder to encode to a plain
     *  FLAC file.
     *
     *  This function should be called after new StreamEncoder() and
     *  StreamEncoder.set*() but before StreamEncoder.process()
     *  or StreamEncoder.processInterleaved().
     *  initialization succeeded.
     *  @param filename The name of the file to encode to.  The file will
     *                            be opened with RandomAccessFile.  Use {@code null} to encode to
     *                            {@code stdout}.  Note however that a proper SEEKTABLE
     *                            cannot be created when encoding to {@code stdout} since
     *                            it is not seekable.
     * @param progressCallback See StreamEncoder.ProgressCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @return {@code StreamEncoderInitStatus.OK} if initialization was successful;
     *    see StreamEncoderInitStatus for the meanings of other return values.
     */
    public StreamEncoderInitStatus initFile(String filename, ProgressCallback progressCallback) throws IOException {
        return initFileInternal(filename, progressCallback, false);
    }

    /**
     * Initialize the encoder instance to encode Ogg FLAC files.
     *
     *  This flavor of initialization sets up the encoder to encode to a plain
     *  Ogg FLAC file.
     *
     *  This function should be called after new StreamEncoder() and
     *  StreamEncoder.set*() but before StreamEncoder.process()
     *  or StreamEncoder.processInterleaved().
     *  initialization succeeded.
     *  @param filename The name of the file to encode to.  The file will
     *                            be opened with RandomAccessFile.  Use {@code null} to encode to
     *                            {@code stdout}.  Note however that a proper SEEKTABLE
     *                            cannot be created when encoding to {@code stdout} since
     *                            it is not seekable.
     * @param progressCallback See StreamEncoder.ProgressCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @return {@code StreamEncoderInitStatus.OK} if initialization was successful;
     *    see StreamEncoderInitStatus for the meanings of other return values.
     */
    public StreamEncoderInitStatus initOggFile(String filename, ProgressCallback progressCallback) throws IOException {
        return initFileInternal(filename, progressCallback, true);
    }

    /**
     * Finish the encoding process.
     *  Flushes the encoding buffer, releases resources, resets the encoder
     *  settings to their defaults, and returns the encoder state to
     *  StreamEncoderState.UNINITIALIZED.  Note that this can generate
     *  one or more write callbacks before returning, and will generate
     *  a metadata callback.
     *
     *  Note that in the course of processing the last frame, errors can
     *  occur, so the caller should handle IOException to ensure the file
     *  was encoded properly.
     *
     *  In the event of a prematurely-terminated encode, it is not strictly
     *  necessary to call this immediately before StreamEncoder.close()
     *  but it is good practice to match every StreamEncoder.init*()
     *  with a StreamEncoder.finish().
     */
    public void finish() throws IOException {
        finish(false);
    }

    /**
     * Submit data for encoding.
     *  This version allows you to supply the input data via an array of
     *  arrays, each containing an array of {@code samples} samples
     *  representing one channel.  The samples need not be block-aligned,
     *  but each channel should have the same number of samples.  Each sample
     *  should be a signed integer, right-justified to the resolution set by
     *  StreamEncoder.setBitsPerSample().  For example, if the
     *  resolution is 16 bits per sample, the samples should all be in the
     *  range [-32768,32767].
     *
     *  For applications where channel order is important, channels must
     *  follow the order as described in the
     *  <A HREF="https://xiph.org/flac/format.html#frame_header">frame header</A>.
     *
     * @param buffer An array of arrays containing each channel's signal.
     * @param samples The number of samples in one channel.
     * @return {@code true} if successful, else {@code false}; in this case, check the
     *    encoder state with StreamEncoder.getState() to see what
     *    went wrong.
     */
    public boolean process(int[][] buffer, int samples) throws IOException {
        requireInitialized();
        final int channels = this.channels, blocksize = this.blocksize;
        if (state != StreamEncoderState.OK)
            return false;
        if (samples < 0)
            throw new IllegalArgumentException("sample count");
        for (int channel = 0; channel < channels; channel++)
            Objects.checkFromIndexSize(0, samples, buffer[channel].length);
        int j = 0;
        do {
            int n = Math.min(blocksize + OVERREAD_ - currentSampleNumber, samples - j);
            for (int channel = 0; channel < channels; channel++) {
                for (int i = 0; i < n; i++)
                    validateSample(buffer[channel][j + i]);
                System.arraycopy(buffer[channel], j, pending[channel], currentSampleNumber, n);
            }
            j += n;
            currentSampleNumber += n;
            /* we only process if we have a full block + 1 extra sample; final block is always handled by StreamEncoder.finish() */
            if (currentSampleNumber > blocksize) {
                processFrame(false);
                /* move unprocessed overread samples to beginnings of arrays */
                for (int channel = 0; channel < channels; channel++)
                    pending[channel][0] = pending[channel][blocksize];
                currentSampleNumber = OVERREAD_;
            }
        } while (j < samples);
        return true;
    }

    /**
     * Submit data for encoding.
     *  This version allows you to supply the input data where the channels
     *  are interleaved into a single array (i.e. channel0_sample0,
     *  channel1_sample0, ... , channelN_sample0, channel0_sample1, ...).
     *  The samples need not be block-aligned but they must be
     *  sample-aligned, i.e. the first value should be channel0_sample0
     *  and the last value channelN_sampleM.  Each sample should be a signed
     *  integer, right-justified to the resolution set by
     *  StreamEncoder.setBitsPerSample().  For example, if the
     *  resolution is 16 bits per sample, the samples should all be in the
     *  range [-32768,32767].
     *
     *  For applications where channel order is important, channels must
     *  follow the order as described in the
     *  <A HREF="https://xiph.org/flac/format.html#frame_header">frame header</A>.
     *
     * @param buffer An array of channel-interleaved data (see above).
     * @param samples The number of samples in one channel, the same as for
     *                  StreamEncoder.process().  For example, if
     *                  encoding two channels, {@code 1000} {@code samples} corresponds
     *                  to a {@code buffer} of 2000 values.
     * @return {@code true} if successful, else {@code false}; in this case, check the
     *    encoder state with StreamEncoder.getState() to see what
     *    went wrong.
     */
    public boolean processInterleaved(int[] buffer, int samples) throws IOException {
        requireInitialized();
        final int channels = this.channels, blocksize = this.blocksize;
        if (samples < 0)
            throw new IllegalArgumentException("sample count");
        Objects.checkFromIndexSize(0, Math.multiplyExact(samples, channels), buffer.length);
        if (state != StreamEncoderState.OK)
            return false;
        int j = 0, k = 0;
        do {
            int i;
            /* "i <= blocksize" to overread 1 sample; see comment in OVERREAD_ decl */
            for (i = currentSampleNumber; i <= blocksize && j < samples; i++, j++) {
                for (int channel = 0; channel < channels; channel++) {
                    validateSample(buffer[k]);
                    pending[channel][i] = buffer[k++];
                }
            }
            currentSampleNumber = i;
            /* we only process if we have a full block + 1 extra sample; final block is always handled by StreamEncoder.finish() */
            if (i > blocksize) {
                processFrame(false);
                /* move unprocessed overread samples to beginnings of arrays */
                for (int channel = 0; channel < channels; channel++)
                    pending[channel][0] = pending[channel][blocksize];
                currentSampleNumber = OVERREAD_;
            }
        } while (j < samples);
        return true;
    }

    /** Close an encoder instance. */
    @Override
    public void close() throws IOException {
        finish(true);
    }

    private StreamEncoderInitStatus initStreamInternal(ReadCallback readCallback, WriteCallback writeCallback,
            SeekCallback seekCallback, TellCallback tellCallback, MetadataCallback metadataCallback, boolean isOgg)
            throws IOException {
        if (state != StreamEncoderState.UNINITIALIZED)
            return StreamEncoderInitStatus.ALREADY_INITIALIZED;
        if (writeCallback == null || (seekCallback != null && tellCallback == null))
            return StreamEncoderInitStatus.INVALID_CALLBACKS;
        if (channels < 1 || channels > Format.MAX_CHANNELS)
            return StreamEncoderInitStatus.INVALID_NUMBER_OF_CHANNELS;
        if (channels != 2)
            doMidSideStereo = false;
        if (!doMidSideStereo)
            looseMidSideStereo = false;
        if (bitsPerSample < Format.MIN_BITS_PER_SAMPLE || bitsPerSample > Format.MAX_BITS_PER_SAMPLE)
            return StreamEncoderInitStatus.INVALID_BITS_PER_SAMPLE;
        if (!Format.sampleRateIsValid(sampleRate))
            return StreamEncoderInitStatus.INVALID_SAMPLE_RATE;
        if (blocksize == 0)
            blocksize = maxLpcOrder == 0 ? 1152 : 4096;
        if (blocksize < Format.MIN_BLOCK_SIZE || blocksize > Format.MAX_BLOCK_SIZE)
            return StreamEncoderInitStatus.INVALID_BLOCK_SIZE;
        if (maxLpcOrder > Format.MAX_LPC_ORDER)
            return StreamEncoderInitStatus.INVALID_MAX_LPC_ORDER;
        if (blocksize < maxLpcOrder)
            return StreamEncoderInitStatus.BLOCK_SIZE_TOO_SMALL_FOR_LPC_ORDER;
        if (qlpCoeffPrecision == 0) {
            if (bitsPerSample < 16)
                /* @@@ need some data about how to set this here w.r.t. blocksize and sample rate */

                /* @@@ until then we'll make a guess */
                qlpCoeffPrecision = Math.max(Format.MIN_QLP_COEFF_PRECISION, 2 + bitsPerSample / 2);
            else if (bitsPerSample == 16) {
                if (blocksize <= 192)
                    qlpCoeffPrecision = 7;
                else if (blocksize <= 384)
                    qlpCoeffPrecision = 8;
                else if (blocksize <= 576)
                    qlpCoeffPrecision = 9;
                else if (blocksize <= 1152)
                    qlpCoeffPrecision = 10;
                else if (blocksize <= 2304)
                    qlpCoeffPrecision = 11;
                else if (blocksize <= 4608)
                    qlpCoeffPrecision = 12;
                else
                    qlpCoeffPrecision = 13;
            } else if (blocksize <= 384)
                qlpCoeffPrecision = Format.MAX_QLP_COEFF_PRECISION - 2;
            else if (blocksize <= 1152)
                qlpCoeffPrecision = Format.MAX_QLP_COEFF_PRECISION - 1;
            else
                qlpCoeffPrecision = Format.MAX_QLP_COEFF_PRECISION;
        } else if (qlpCoeffPrecision < Format.MIN_QLP_COEFF_PRECISION
                || qlpCoeffPrecision > Format.MAX_QLP_COEFF_PRECISION)
            return StreamEncoderInitStatus.INVALID_QLP_COEFF_PRECISION;
        if (streamableSubset) {
            if (!Format.blocksizeIsSubset(blocksize, sampleRate))
                return StreamEncoderInitStatus.NOT_STREAMABLE;
            if (!Format.sampleRateIsSubset(sampleRate))
                return StreamEncoderInitStatus.NOT_STREAMABLE;
            switch (bitsPerSample) {
                case 8, 12, 16, 20, 24, 32 -> {
                }
                default -> {
                    return StreamEncoderInitStatus.NOT_STREAMABLE;
                }
            }
            if (maxResidualPartitionOrder > Format.SUBSET_MAX_RICE_PARTITION_ORDER)
                return StreamEncoderInitStatus.NOT_STREAMABLE;
            if (sampleRate <= 48000 && (blocksize > Format.SUBSET_MAX_BLOCK_SIZE_48000HZ
                    || maxLpcOrder > Format.SUBSET_MAX_LPC_ORDER_48000HZ))
                return StreamEncoderInitStatus.NOT_STREAMABLE;
        }
        maxResidualPartitionOrder = Math.min((1 << Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN) - 1,
                maxResidualPartitionOrder);
        minResidualPartitionOrder = Math.min(minResidualPartitionOrder, maxResidualPartitionOrder);
        List<StreamMetadata> blocks = new ArrayList<>(Arrays.asList(metadataBlocks));
        /* drop any seektable for ogg */
        if (isOgg) {
            for (Iterator<StreamMetadata> it = blocks.iterator(); it.hasNext();)
                if (it.next() instanceof SeekTable) {
                    it.remove();
                    break;
                }
            /* reorder metadata if necessary to ensure that any VORBIS_COMMENT is the first, according to the mapping spec */
            for (int i = 1; i < blocks.size(); i++)
                if (blocks.get(i) instanceof VorbisComment) {
                    blocks.add(0, blocks.remove(i));
                    break;
                }
            metadataBlocks = blocks.toArray(StreamMetadata[]::new);
        }
        /* keep track of any SEEKTABLE block */
        for (StreamMetadata block : blocks)
            if (block instanceof SeekTable table) {
                /* take only the first one */
                seekTable = table;
                break;
            }
        boolean metadataHasSeektable = false, metadataHasVorbisComment = false, metadataPictureHasType1 = false,
                metadataPictureHasType2 = false;
        /* validate metadata */
        for (StreamMetadata b : blocks) {
            if (b == null || b instanceof StreamInfo)
                return StreamEncoderInitStatus.INVALID_METADATA;
            if (b instanceof SeekTable t) {
                /* only one is allowed */
                if (metadataHasSeektable || !t.isLegal())
                    return StreamEncoderInitStatus.INVALID_METADATA;
                metadataHasSeektable = true;
            }
            if (b instanceof VorbisComment) {
                if (metadataHasVorbisComment)
                    return StreamEncoderInitStatus.INVALID_METADATA;
                metadataHasVorbisComment = true;
            }
            if (b instanceof CueSheet c && !c.isLegal(c.isCd))
                return StreamEncoderInitStatus.INVALID_METADATA;
            if (b instanceof Picture p) {
                if (!p.isLegal())
                    return StreamEncoderInitStatus.INVALID_METADATA;
                /* standard icon must be 32x32 pixel PNG */
                if (p.pictureType == 1) {
                    /* there should only be 1 per stream */
                    if (metadataPictureHasType1 || !(p.mimeType.equals("image/png") || p.mimeType.equals("-->"))
                            || p.width != 32 || p.height != 32)
                        return StreamEncoderInitStatus.INVALID_METADATA;
                    metadataPictureHasType1 = true;
                }
                if (p.pictureType == 2) {
                    if (metadataPictureHasType2)
                        return StreamEncoderInitStatus.INVALID_METADATA;
                    metadataPictureHasType2 = true;
                }
            }
        }
        looseMidSideStereoFrames = (int) (sampleRate * 0.4 / blocksize + 0.5);
        if (looseMidSideStereoFrames == 0)
            looseMidSideStereoFrames = 1;
        looseMidSideStereoFrameCount = 0;
        currentSampleNumber = 0;
        currentFrameNumber = 0;
        /* set state to OK; from here on, errors are fatal and we'll override the state then */
        state = StreamEncoderState.OK;
        this.isOgg = isOgg;
        if (isOgg)
            /* @@@ can't figure out a way to pass a useful number for 'samples' to the writeCallback, so we'll just pass 0 */
            oggEncoderAspect = new OggEncoderAspect(oggSerialNumber, oggNumMetadata,
                    (buffer, offset, bytes) -> writeOut(buffer, offset, bytes, 0));
        this.readCallback = readCallback;
        this.writeCallback = writeCallback;
        this.seekCallback = seekCallback;
        this.tellCallback = tellCallback;
        this.metadataCallback = metadataCallback;
        resizeBuffers(blocksize);
        frame = new BitWriter();
        /* Set up the verify stuff if necessary */
        if (verify)
            initVerifyDecoder();
        verifyErrorStats = new VerifyErrorStats(0, 0, 0, 0, 0, 0);
        /*
         * These must be done before we write any metadata, because that
         * calls the write_callback, which uses these values.
         */
        firstSeekpointToCheck = 0;
        samplesWritten = 0;
        streamInfoOffset = seekTableOffset = audioOffset = 0;
        try {
            verifyStateHint = EncoderStateHint.ENCODER_IN_MAGIC;
            try {
                /* write the stream header */
                frame.writeRawUInt32(Format.STREAM_SYNC, Format.STREAM_SYNC_LEN);
            } catch (IOException e) {
                state = StreamEncoderState.FRAMING_ERROR;
                throw e;
            }
            writeBitbuffer(0, false);
            verifyStateHint = EncoderStateHint.ENCODER_IN_METADATA;
            /* write the STREAMINFO metadata block */

            /* we will have at a minimum a VORBIS_COMMENT afterwards */
            streamInfo.isLast = false;
            /* this encoder uses the same blocksize for the whole stream */
            streamInfo.minBlocksize = streamInfo.maxBlocksize = blocksize;
            /* we don't know this yet; have to fill it in later */
            streamInfo.minFramesize = streamInfo.maxFramesize = 0;
            streamInfo.sampleRate = sampleRate;
            streamInfo.channels = channels;
            streamInfo.bitsPerSample = bitsPerSample;
            /* we will replace this later with the real total */
            streamInfo.totalSamples = totalSamplesEstimate;
            Arrays.fill(streamInfo.md5sum, (byte) 0);
            md5.reset();
            emitMetadata(streamInfo);
            /*
             * Now that the STREAMINFO block is written, we can init this to an
             * absurdly-high value...
             */
            streamInfo.minFramesize = (1 << Format.STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN) - 1;
            /* ... and clear this to 0 */
            streamInfo.totalSamples = 0;
            /*
             * Check to see if the supplied metadata contains a VORBIS_COMMENT;
             * if not, we will write an empty one (StreamEncoderFraming.addMetadataBlock()
             * automatically supplies the vendor string).
             *
             * WATCHOUT: the Ogg FLAC mapping requires us to write this block after
             * the STREAMINFO.  (In the case that metadataHasVorbisComment is
             * true it will have already insured that the metadata list is properly
             * ordered.)
             */
            if (!metadataHasVorbisComment) {
                VorbisComment vorbisComment = new VorbisComment();
                vorbisComment.isLast = blocks.isEmpty();
                emitMetadata(vorbisComment);
            }
            /* write the user's metadata blocks */
            for (int i = 0; i < blocks.size(); i++) {
                StreamMetadata b = blocks.get(i);
                b.isLast = i == blocks.size() - 1;
                emitMetadata(b);
            }
            /* now that all the metadata is written, we save the stream offset */
            audioOffset = outputPosition();
            verifyStateHint = EncoderStateHint.ENCODER_IN_AUDIO;
            return StreamEncoderInitStatus.OK;
        } catch (IOException e) {
            if (state == StreamEncoderState.OK)
                state = isOgg ? StreamEncoderState.OGG_ERROR : StreamEncoderState.IO_ERROR;
            throw e;
        }
    }

    private void initVerifyDecoder() throws IOException {
        /* Now set up a stream decoder for verification */
        if (verifyDecoder == null)
            verifyDecoder = new StreamDecoder();
        StreamDecoderInitStatus result = verifyDecoder.initStream(this::verifyReadCallback, null, null, null, null,
                this::verifyWriteCallback, (decoder, block) -> {
                }, (decoder, error) -> state = StreamEncoderState.VERIFY_DECODER_ERROR);
        if (result != StreamDecoderInitStatus.OK) {
            state = StreamEncoderState.VERIFY_DECODER_ERROR;
            throw new IOException("verify decoder initialization failed");
        }
    }

    private StreamEncoderInitStatus initFileInternal(RandomAccessFile file, ProgressCallback progressCallback,
            boolean isOgg) throws IOException {
        if (state != StreamEncoderState.UNINITIALIZED)
            return StreamEncoderInitStatus.ALREADY_INITIALIZED;
        /*
         * To make sure that our file does not go unclosed after an error, we
         * must assign the file reference before any further error can occur in
         * this routine.
         */
        this.file = file;
        standardOutput = file == null;
        this.progressCallback = progressCallback;
        bytesWritten = samplesWritten = 0;
        framesWritten = 0;
        StreamEncoderInitStatus status = initStreamInternal(standardOutput || !isOgg ? null : this::fileReadCallback,
                this::fileWriteCallback, standardOutput ? null : this::fileSeekCallback,
                standardOutput ? null : this::fileTellCallback, null, isOgg);
        if (status == StreamEncoderInitStatus.OK)
            totalFramesEstimate = (int) ((totalSamplesEstimate + blocksize - 1) / blocksize);
        return status;
    }

    private StreamEncoderInitStatus initFileInternal(String filename, ProgressCallback progressCallback, boolean isOgg)
            throws IOException {
        /*
         * To make sure that our file does not go unclosed after an error, we
         * have to do the same entrance checks here that are later performed
         * in StreamEncoder.initFile() before the RandomAccessFile is assigned.
         */
        if (state != StreamEncoderState.UNINITIALIZED)
            return StreamEncoderInitStatus.ALREADY_INITIALIZED;
        if (filename == null)
            return initFileInternal((RandomAccessFile) null, progressCallback, isOgg);
        RandomAccessFile f;
        try {
            f = new RandomAccessFile(filename, "rw");
        } catch (IOException e) {
            state = StreamEncoderState.IO_ERROR;
            throw e;
        }
        boolean initialized = false;
        try {
            try {
                f.setLength(0);
            } catch (IOException e) {
                state = StreamEncoderState.IO_ERROR;
                throw e;
            }
            StreamEncoderInitStatus status = initFileInternal(f, progressCallback, isOgg);
            initialized = status == StreamEncoderInitStatus.OK;
            return status;
        } finally {
            if (!initialized)
                f.close();
        }
    }

    private void finish(boolean deleting) throws IOException {
        if (state == StreamEncoderState.UNINITIALIZED) {
            // True in case StreamEncoder.setMetadata was used but init failed
            metadataBlocks = new StreamMetadata[0];
            if (file != null) {
                file.close();
                file = null;
            }
            return;
        }
        boolean ok = true;
        Exception failure = null;
        try {
            if (state == StreamEncoderState.OK && !deleting && currentSampleNumber > 0) {
                blocksize = currentSampleNumber;
                resizeBuffers(blocksize);
                processFrame(true);
            }
            streamInfo.md5sum = md5.finish();
            if (state == StreamEncoderState.OK && ok && !deleting) {
                if (seekCallback != null) {
                    try {
                        if (isOgg)
                            updateOggMetadata();
                        else
                            updateMetadata();
                    } catch (Exception e) {
                        if (state == StreamEncoderState.OK)
                            state = StreamEncoderState.CLIENT_ERROR;
                        failure = e;
                    }
                }
                /* check if an error occurred while updating metadata */
                if (state != StreamEncoderState.OK)
                    ok = false;
                if (metadataCallback != null) {
                    try {
                        metadataCallback.metadata(this, streamInfo);
                    } catch (RuntimeException e) {
                        if (state == StreamEncoderState.OK)
                            state = StreamEncoderState.CLIENT_ERROR;
                        if (failure == null)
                            failure = e;
                        else if (failure != e)
                            failure.addSuppressed(e);
                        ok = false;
                    }
                }
                if (!ok && failure == null)
                    failure = new IOException("encoder metadata update failed");
            }
        } catch (Exception e) {
            failure = e;
            ok = false;
            if (state == StreamEncoderState.OK)
                state = StreamEncoderState.IO_ERROR;
        } finally {
            if (file != null)
                try {
                    file.close();
                } catch (Exception e) {
                    if (failure == null)
                        failure = e;
                    else if (failure != e)
                        failure.addSuppressed(e);
                    ok = false;
                    state = StreamEncoderState.IO_ERROR;
                }
            file = null;
            if (oggEncoderAspect != null)
                oggEncoderAspect.finish();
            frame = null;
            pending = null;
            windows = null;
            windowedSignal = null;
            oggEncoderAspect = null;
            seekTable = null;
            readCallback = null;
            writeCallback = null;
            seekCallback = null;
            tellCallback = null;
            metadataCallback = null;
            progressCallback = null;
            if (verifyDecoder != null)
                verifyDecoder.close();
            verifyOutput = null;
            setDefaults();
            if (ok || deleting)
                state = StreamEncoderState.UNINITIALIZED;
        }
        if (failure instanceof IOException e)
            throw e;
        if (failure instanceof RuntimeException e)
            throw e;
    }

    private void requireUninitialized() {
        if (state != StreamEncoderState.UNINITIALIZED)
            throw new IllegalStateException("encoder already initialized");
    }

    private void requireInitialized() {
        if (state == StreamEncoderState.UNINITIALIZED)
            throw new IllegalStateException("encoder uninitialized");
    }

    private void validateSample(int value) {
        if (value < (Integer.MIN_VALUE >> (Integer.SIZE - bitsPerSample))
                || value > (Integer.MAX_VALUE >> (Integer.SIZE - bitsPerSample))) {
            state = StreamEncoderState.CLIENT_ERROR;
            throw new IllegalArgumentException("sample exceeds configured bit depth");
        }
    }

    private void setDefaults() {
        verify = false;
        streamableSubset = true;
        doMidSideStereo = false;
        looseMidSideStereo = false;
        channels = 2;
        bitsPerSample = 16;
        sampleRate = 44100;
        blocksize = 0;
        apodizations = ApodizationSpecification.parse("tukey(5e-1)");
        maxLpcOrder = 0;
        qlpCoeffPrecision = 0;
        doQlpCoeffPrecSearch = false;
        doExhaustiveModelSearch = false;
        minResidualPartitionOrder = 0;
        maxResidualPartitionOrder = 0;
        totalSamplesEstimate = 0;
        limitMinBitrate = false;
        metadataBlocks = new StreamMetadata[0];
        seekTable = null;
        isOgg = false;
        readCallback = null;
        writeCallback = null;
        seekCallback = null;
        tellCallback = null;
        metadataCallback = null;
        progressCallback = null;
        oggSerialNumber = 0;
        oggNumMetadata = 0;

        if (state == StreamEncoderState.UNINITIALIZED)
            setCompressionLevel(5);
    }

    private void resizeBuffers(int newBlocksize) {
        if (pending == null || pending[0].length < newBlocksize + OVERREAD_)
            /* To avoid excessive allocation, we only grow the buffer; no shrinking. */
            pending = new int[channels][newBlocksize + OVERREAD_];
        /* now adjust the windows if the blocksize has changed */
        if (maxLpcOrder > 0 && newBlocksize > 1) {
            windows = new float[apodizations.size()][];
            for (int i = 0; i < windows.length; i++)
                windows[i] = apodizations.get(i).window(newBlocksize);
            if (windowedSignal == null || windowedSignal.length < newBlocksize)
                windowedSignal = new float[newBlocksize];
        }
    }

    private void emitMetadata(StreamMetadata block) throws IOException {
        try {
            StreamEncoderFraming.addMetadataBlock(block, frame, true);
        } catch (IllegalArgumentException | IOException e) {
            state = StreamEncoderState.FRAMING_ERROR;
            throw new IOException("metadata framing failed", e);
        }
        writeBitbuffer(0, false);
    }

    private void writeBitbuffer(int samples, boolean isLastBlock) throws IOException {
        byte[] buffer;
        try {
            buffer = frame.getBuffer();
        } catch (IOException e) {
            state = StreamEncoderState.MEMORY_ALLOCATION_ERROR;
            throw e;
        }
        try {
            if (verify) {
                verifyOutput = buffer;
                verifyOutputOffset = 0;
                if (verifyStateHint == EncoderStateHint.ENCODER_IN_MAGIC)
                    verifyNeedsMagicHack = true;
                else if (!verifyDecoder.processSingle()
                        || !isLastBlock && verifyDecoder.getState() == StreamDecoderState.END_OF_STREAM
                        || state == StreamEncoderState.VERIFY_DECODER_ERROR) {
                    if (state != StreamEncoderState.VERIFY_MISMATCH_IN_AUDIO_DATA)
                        /* Happens when error callback was used */
                        state = StreamEncoderState.VERIFY_DECODER_ERROR;
                    throw new IOException("encoder verification failed: " + state);
                }
            }
            writeFrame(buffer, samples, isLastBlock);
        } finally {
            frame.clear();
        }
        if (samples > 0) {
            streamInfo.minFramesize = Math.min(streamInfo.minFramesize, buffer.length);
            streamInfo.maxFramesize = Math.max(streamInfo.maxFramesize, buffer.length);
        }
    }

    private long outputPosition() throws IOException {
        if (tellCallback == null)
            return 0;
        try {
            long position = tellCallback.tell(this);
            if (position == -2) {
                state = StreamEncoderState.CLIENT_ERROR;
                throw new IOException("encoder tell callback failed");
            }
            /* -1 just means we didn't get the offset; no error */
            return position < 0 ? 0 : position;
        } catch (Exception e) {
            if (state == StreamEncoderState.OK)
                state = StreamEncoderState.CLIENT_ERROR;
            throw e;
        }
    }

    private void writeFrame(byte[] buffer, int samples, boolean isLastBlock) throws IOException {
        long outputPosition = outputPosition();
        if (samples == 0) {
            /* Watch for the STREAMINFO block and first SEEKTABLE block to go by and store their offsets. */
            int type = buffer[0] & 127;
            if (type == MetadataType.STREAMINFO.ordinal())
                streamInfoOffset = outputPosition;
            else if (type == MetadataType.SEEKTABLE.ordinal() && seekTableOffset == 0)
                seekTableOffset = outputPosition;
        }
        /*
         * Mark the current seek point if hit (if audioOffset == 0 that
         * means we're still writing metadata and haven't hit the first
         * frame yet)
         */
        if (seekTable != null && audioOffset > 0 && seekTable.points.length > 0) {
            int blocksize = this.blocksize;
            long frameFirstSample = samplesWritten;
            long frameLastSample = frameFirstSample + blocksize - 1;
            for (int i = firstSeekpointToCheck; i < seekTable.points.length; i++) {
                SeekPoint point = seekTable.points[i];
                long testSample = point.sampleNumber;
                if (Long.compareUnsigned(testSample, frameLastSample) > 0)
                    break;
                else if (Long.compareUnsigned(testSample, frameFirstSample) >= 0) {
                    point.sampleNumber = frameFirstSample;
                    point.streamOffset = outputPosition - audioOffset;
                    point.frameSamples = blocksize;
                    firstSeekpointToCheck++;
                    /*
                     * DO NOT: "break;" and here's why:
                     * The seektable template may contain more than one target
                     * sample for any given frame; we will keep looping, generating
                     * duplicate seekpoints for them, and we'll clean it up later,
                     * just before writing the seektable back to the metadata.
                     */
                } else
                    firstSeekpointToCheck++;
            }
        }
        if (isOgg) {
            try {
                oggEncoderAspect.writeCallbackWrapper(buffer, samples, isLastBlock);
            } catch (IOException e) {
                if (state == StreamEncoderState.OK)
                    state = StreamEncoderState.OGG_ERROR;
                throw e;
            }
        } else
            writeOut(buffer, 0, buffer.length, samples);
        bytesWritten += buffer.length;
        samplesWritten += samples;
        if (Integer.compareUnsigned(framesWritten, currentFrameNumber + 1) < 0)
            /*
             * we keep a high watermark on the number of frames written because
             * when the encoder goes back to write metadata, 'currentFrame'
             * will drop back to 0.
             */
            framesWritten = currentFrameNumber + 1;
    }

    /* Gets called when the encoding process has finished so that we can update the STREAMINFO and SEEKTABLE blocks. */
    private void updateMetadata() throws IOException {
        byte[] b = new byte[Math.max(6, Format.STREAM_METADATA_SEEKPOINT_LENGTH)];
        StreamInfo metadata = streamInfo;
        long samples = metadata.totalSamples;
        int minFramesize = metadata.minFramesize;
        int maxFramesize = metadata.maxFramesize;
        int bps = metadata.bitsPerSample;
        /*
         * All this is based on intimate knowledge of the stream header
         * layout, but a change to the header format that would break this
         * would also break all streams encoded in the previous format.
         */

        /* Write MD5 signature */
        StreamEncoderSeekStatus seekStatus = seekCallback.seek(this, streamInfoOffset + md5_offset);
        if (seekStatus != StreamEncoderSeekStatus.OK) {
            if (seekStatus == StreamEncoderSeekStatus.ERROR)
                state = StreamEncoderState.CLIENT_ERROR;
            return;
        }
        if (!writeCallback.write(this, metadata.md5sum, 0, Format.STREAM_METADATA_STREAMINFO_MD5SUM_LEN / Byte.SIZE, 0,
                0)) {
            state = StreamEncoderState.CLIENT_ERROR;
            return;
        }
        if (Long.compareUnsigned(samples, 1L << Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN) > 0)
            samples = 0;
        b[0] = (byte) (((bps - 1) << 4) | ((samples >>> 32) & 0x0f));
        b[1] = (byte) (samples >>> 24);
        b[2] = (byte) (samples >>> 16);
        b[3] = (byte) (samples >>> 8);
        b[4] = (byte) samples;
        /* Write total samples */
        seekStatus = seekCallback.seek(this, streamInfoOffset + total_samples_byte_offset);
        if (seekStatus != StreamEncoderSeekStatus.OK) {
            if (seekStatus == StreamEncoderSeekStatus.ERROR)
                state = StreamEncoderState.CLIENT_ERROR;
            return;
        }
        if (!writeCallback.write(this, b, 0, 5, 0, 0)) {
            state = StreamEncoderState.CLIENT_ERROR;
            return;
        }
        b[0] = (byte) (minFramesize >>> 16);
        b[1] = (byte) (minFramesize >>> 8);
        b[2] = (byte) minFramesize;
        b[3] = (byte) (maxFramesize >>> 16);
        b[4] = (byte) (maxFramesize >>> 8);
        b[5] = (byte) maxFramesize;
        /* Write min/max framesize */
        seekStatus = seekCallback.seek(this, streamInfoOffset + min_framesize_offset);
        if (seekStatus != StreamEncoderSeekStatus.OK) {
            if (seekStatus == StreamEncoderSeekStatus.ERROR)
                state = StreamEncoderState.CLIENT_ERROR;
            return;
        }
        if (!writeCallback.write(this, b, 0, 6, 0, 0)) {
            state = StreamEncoderState.CLIENT_ERROR;
            return;
        }
        /* Write seektable */
        if (seekTable != null && seekTable.points.length > 0 && seekTableOffset > 0) {
            seekTable.sort();
            seekStatus = seekCallback.seek(this, seekTableOffset + Format.STREAM_METADATA_HEADER_LENGTH);
            if (seekStatus != StreamEncoderSeekStatus.OK) {
                if (seekStatus == StreamEncoderSeekStatus.ERROR)
                    state = StreamEncoderState.CLIENT_ERROR;
                return;
            }
            for (SeekPoint point : seekTable.points) {
                long xx = point.sampleNumber;
                b[7] = (byte) xx;
                xx >>>= 8;
                b[6] = (byte) xx;
                xx >>>= 8;
                b[5] = (byte) xx;
                xx >>>= 8;
                b[4] = (byte) xx;
                xx >>>= 8;
                b[3] = (byte) xx;
                xx >>>= 8;
                b[2] = (byte) xx;
                xx >>>= 8;
                b[1] = (byte) xx;
                xx >>>= 8;
                b[0] = (byte) xx;
                xx >>>= 8;
                xx = point.streamOffset;
                b[15] = (byte) xx;
                xx >>>= 8;
                b[14] = (byte) xx;
                xx >>>= 8;
                b[13] = (byte) xx;
                xx >>>= 8;
                b[12] = (byte) xx;
                xx >>>= 8;
                b[11] = (byte) xx;
                xx >>>= 8;
                b[10] = (byte) xx;
                xx >>>= 8;
                b[9] = (byte) xx;
                xx >>>= 8;
                b[8] = (byte) xx;
                xx >>>= 8;
                int x = point.frameSamples;
                b[17] = (byte) x;
                x >>>= 8;
                b[16] = (byte) x;
                if (!writeCallback.write(this, b, 0, Format.STREAM_METADATA_SEEKPOINT_LENGTH, 0, 0)) {
                    state = StreamEncoderState.CLIENT_ERROR;
                    return;
                }
            }
        }
    }

    /* Gets called when the encoding process has finished so that we can update the STREAMINFO and SEEKTABLE blocks. */
    private void updateOggMetadata() throws IOException {
        byte[] b = new byte[Math.max(6, Format.STREAM_METADATA_SEEKPOINT_LENGTH)];
        StreamInfo metadata = streamInfo;
        long samples = metadata.totalSamples;
        int minFramesize = metadata.minFramesize;
        int maxFramesize = metadata.maxFramesize;
        Page page = new Page();

        /*
         * Pre-check that client supports seeking, since we don't want the
         * OggHelper code to ever have to deal with this condition.
         */
        if (seekCallback.seek(this, 0) == StreamEncoderSeekStatus.UNSUPPORTED)
            return;
        /*
         * All this is based on intimate knowledge of the stream header
         * layout, but a change to the header format that would break this
         * would also break all streams encoded in the previous format.
         */

        /* Write STREAMINFO stats */
        if (!OggHelper.simpleOggPageGetAt(this, streamInfoOffset, page, seekCallback, readCallback))
            return;

        /* Write MD5 signature */
        final int md5_offset = FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH + StreamEncoder.md5_offset;
        if (md5_offset + Format.STREAM_METADATA_STREAMINFO_MD5SUM_LEN / Byte.SIZE > page.body_len) {
            state = StreamEncoderState.OGG_ERROR;
            return;
        }
        System.arraycopy(metadata.md5sum, 0, page.body_base, page.body + md5_offset,
                Format.STREAM_METADATA_STREAMINFO_MD5SUM_LEN / Byte.SIZE);

        /* Write total samples */
        final int total_samples_byte_offset = FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH
                + StreamEncoder.total_samples_byte_offset;
        if (total_samples_byte_offset + 5 > page.body_len) {
            state = StreamEncoderState.OGG_ERROR;
            return;
        }
        b[0] = (byte) (page.body_base[page.body + total_samples_byte_offset] & 0xf0);
        b[0] |= (byte) ((samples >>> 32) & 0x0f);
        b[1] = (byte) (samples >>> 24);
        b[2] = (byte) (samples >>> 16);
        b[3] = (byte) (samples >>> 8);
        b[4] = (byte) samples;
        System.arraycopy(b, 0, page.body_base, page.body + total_samples_byte_offset, 5);

        /* Write min/max framesize */
        final int min_framesize_offset = FIRST_OGG_PACKET_STREAMINFO_PREFIX_LENGTH + StreamEncoder.min_framesize_offset;
        if (min_framesize_offset + 6 > page.body_len) {
            state = StreamEncoderState.OGG_ERROR;
            return;
        }
        b[0] = (byte) (minFramesize >>> 16);
        b[1] = (byte) (minFramesize >>> 8);
        b[2] = (byte) minFramesize;
        b[3] = (byte) (maxFramesize >>> 16);
        b[4] = (byte) (maxFramesize >>> 8);
        b[5] = (byte) maxFramesize;
        System.arraycopy(b, 0, page.body_base, page.body + min_framesize_offset, 6);
        OggHelper.simpleOggPageSetAt(this, streamInfoOffset, page, seekCallback, writeCallback);
    }

    private void processFrame(boolean isLastBlock) throws IOException {
        /* Accumulate raw signal to the MD5 signature */
        md5.accumulate(pending, 0, channels, blocksize, (bitsPerSample + 7) / 8);
        /* Process the frame header and subframes into the frame bitbuffer */
        processSubframes();
        try {
            /* Zero-pad the frame to a byte_boundary */
            frame.zeroPadToByteBoundary();
            /* CRC-16 the whole thing */
            frame.writeRawUInt32(frame.getWriteCrc16(), Format.FRAME_FOOTER_CRC_LEN);
        } catch (IOException e) {
            state = StreamEncoderState.MEMORY_ALLOCATION_ERROR;
            throw e;
        }
        /* Write it */
        writeBitbuffer(blocksize, isLastBlock);
        /* Get ready for the next frame */
        currentSampleNumber = 0;
        currentFrameNumber++;
        streamInfo.totalSamples += blocksize;
    }

    private void processSubframes() throws IOException {
        /* Calculate the min,max Rice partition orders */
        int maxPartitionOrder = Math.min(Format.getMaxRicePartitionOrderFromBlocksize(blocksize),
                maxResidualPartitionOrder);
        int minPartitionOrder = Math.min(minResidualPartitionOrder, maxPartitionOrder);
        FrameHeader frameHeader = new FrameHeader();
        /* Setup the frame */
        frameHeader.blocksize = blocksize;
        frameHeader.sampleRate = sampleRate;
        frameHeader.channels = channels;
        /* the default unless the encoder determines otherwise */
        frameHeader.channelAssignment = ChannelAssignment.INDEPENDENT;
        frameHeader.bitsPerSample = bitsPerSample;
        frameHeader.numberType = FrameNumberType.FRAME_NUMBER;
        frameHeader.number = currentFrameNumber;
        /* Figure out what channel assignments to try */
        boolean doIndependent = true, doMidSide = doMidSideStereo;
        if (looseMidSideStereo && looseMidSideStereoFrameCount != 0) {
            doIndependent = lastChannelAssignment == ChannelAssignment.INDEPENDENT;
            doMidSide = !doIndependent;
        }
        long[][] integerSignal = doIndependent ? new long[channels][blocksize] : null;
        if (doIndependent)
            for (int c = 0; c < channels; c++)
                for (int i = 0; i < blocksize; i++)
                    integerSignal[c][i] = pending[c][i];
        long[][] integerSignalMidSide = doMidSide ? new long[2][blocksize] : null;
        /* Prepare mid-side signals if applicable */
        if (doMidSide) {
            for (int i = 0; i < blocksize; i++) {
                long l = pending[0][i], r = pending[1][i];
                /* NOTE: not the same as 'mid = (l + r) / 2' ! */
                integerSignalMidSide[0][i] = (l + r) >> 1;
                integerSignalMidSide[1][i] = l - r;
            }
        }

        int[] wastedBits = doIndependent ? new int[channels] : null;
        /* Check for wasted bits; set effective bps for each subframe */
        int[] subframeBps = doIndependent ? new int[channels] : null;
        if (doIndependent)
            for (int c = 0; c < channels; c++) {
                int shift = getWastedBits(integerSignal[c], blocksize);
                shift = Math.min(shift, bitsPerSample);
                wastedBits[c] = shift;
                subframeBps[c] = bitsPerSample - shift;
            }
        int[] wastedBitsMidSide = doMidSide ? new int[2] : null;
        int[] subframeBpsMidSide = doMidSide ? new int[2] : null;
        if (doMidSide)
            for (int c = 0; c < 2; c++) {
                int shift = bitsPerSample < Integer.SIZE || c == 0 ? getWastedBits(integerSignalMidSide[c], blocksize)
                        : getWastedBitsWide(integerSignalMidSide[c], blocksize);
                shift = Math.min(shift, bitsPerSample);
                wastedBitsMidSide[c] = shift;
                subframeBpsMidSide[c] = bitsPerSample - shift + (c == 0 ? 0 : 1);
            }

        EncodedSubframe[] subframes = new EncodedSubframe[channels], subframesMidSide = new EncodedSubframe[2];
        boolean allSubframesConstant = true, disableConstantSubframes = false;
        /* First do a normal encoding pass of each independent channel */
        if (doIndependent)
            for (int c = 0; c < channels; c++) {
                if (limitMinBitrate && allSubframesConstant && c + 1 == channels)
                    /*
                     * This frame contains only constant subframes at this point.
                     * To prevent the frame from becoming too small, make sure
                     * the last subframe isn't constant
                     */
                    disableConstantSubframes = true;
                subframes[c] = processSubframe(minPartitionOrder, maxPartitionOrder, subframeBps[c], integerSignal[c],
                        wastedBits[c], disableConstantSubframes);
                allSubframesConstant &= subframes[c].subframe.type == SubframeType.CONSTANT;
            }
        /* Now do mid and side channels if requested */
        if (doMidSide)
            for (int c = 0; c < 2; c++)
                subframesMidSide[c] = processSubframe(minPartitionOrder, maxPartitionOrder, subframeBpsMidSide[c],
                        integerSignalMidSide[c], wastedBitsMidSide[c], disableConstantSubframes);
        /* Compose the frame bitbuffer */
        ChannelAssignment channelAssignment = doIndependent ? ChannelAssignment.INDEPENDENT
                : ChannelAssignment.MID_SIDE;
        if (doIndependent && doMidSide) {
            /* WATCHOUT - indexed by ChannelAssignment */

            /* We have to figure out which channel assignent results in the smallest frame */
            long[] bits = {subframes[0].bits + subframes[1].bits, subframes[0].bits + subframesMidSide[1].bits,
                    subframesMidSide[1].bits + subframes[1].bits, subframesMidSide[0].bits + subframesMidSide[1].bits};
            long minBits = bits[ChannelAssignment.INDEPENDENT.ordinal()];
            /*
             * When doing loose mid-side stereo, ignore left-side
             * and right-side options
             */
            for (int ca = looseMidSideStereo ? 3 : 1; ca <= 3; ca++)
                if (bits[ca] < minBits) {
                    minBits = bits[ca];
                    channelAssignment = switch (ca) {
                        case 1 -> ChannelAssignment.LEFT_SIDE;
                        case 2 -> ChannelAssignment.RIGHT_SIDE;
                        case 3 -> ChannelAssignment.MID_SIDE;
                        default -> ChannelAssignment.INDEPENDENT;
                    };
                }
        }
        frameHeader.channelAssignment = channelAssignment;
        try {
            if (!StreamEncoderFraming.frameAddHeader(frameHeader, frame))
                throw new IOException("frame number exceeds the FLAC fixed-block header range");
        } catch (IOException e) {
            state = StreamEncoderState.FRAMING_ERROR;
            throw e;
        }
        EncodedSubframe[] chosen = switch (channelAssignment) {
            case LEFT_SIDE -> new EncodedSubframe[] {subframes[0], subframesMidSide[1]};
            case RIGHT_SIDE -> new EncodedSubframe[] {subframesMidSide[1], subframes[1]};
            case MID_SIDE -> subframesMidSide;
            case INDEPENDENT -> subframes;
        };
        for (EncodedSubframe sub : chosen)
            /* note that addSubframe() sets the state for us in case of an error */
            addSubframe(sub, frame);
        if (looseMidSideStereo) {
            looseMidSideStereoFrameCount++;
            if (looseMidSideStereoFrameCount >= looseMidSideStereoFrames)
                looseMidSideStereoFrameCount = 0;
        }
        lastChannelAssignment = frameHeader.channelAssignment;
    }

    private EncodedSubframe processSubframe(int minPartitionOrder, int maxPartitionOrder, int subframeBps,
            long[] integerSignal, int wastedBits, boolean disableConstantSubframes) {
        int blocksize = integerSignal.length;
        /* only use RICE2 partitions if stream bps > 16 */
        int riceParameterLimit = bitsPerSample > 16 ? Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER
                : Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER;
        /* verbatim subframe is the baseline against which we measure other compressed subframes */
        EncodedSubframe best = evaluateVerbatimSubframe(integerSignal, blocksize, subframeBps, wastedBits);
        if (blocksize <= Format.MAX_FIXED_ORDER)
            return best;
        float[] fixedResidualBitsPerSample = new float[Format.MAX_FIXED_ORDER + 1];
        int guessFixedOrder;
        if (subframeBps < 28) {
            /*
             * The next formula determines when to use a 64-bit accumulator
             * for the error of a fixed predictor, and when a 32-bit one. As
             * the error of a 4th order predictor for a given sample is the
             * sum of 17 sample values (1+4+6+4+1) and there are blocksize -
             * order error values to be summed, the maximum total error is
             * maximum_sample_value * (blocksize - order) * 17. As ilog2(x)
             * calculates floor(2log(x)), the result must be 31 or lower
             */
            if (subframeBps
                    + (31 - Integer.numberOfLeadingZeros((blocksize - Format.MAX_FIXED_ORDER) * 17)) < Integer.SIZE)
                guessFixedOrder = Fixed.computeBestPredictor(integerSignal, Format.MAX_FIXED_ORDER,
                        blocksize - Format.MAX_FIXED_ORDER, fixedResidualBitsPerSample);
            else
                guessFixedOrder = Fixed.computeBestPredictorWide(integerSignal, Format.MAX_FIXED_ORDER,
                        blocksize - Format.MAX_FIXED_ORDER, fixedResidualBitsPerSample);
        } else
            guessFixedOrder = Fixed.computeBestPredictorLimitResidual(integerSignal, Format.MAX_FIXED_ORDER,
                    blocksize - Format.MAX_FIXED_ORDER, fixedResidualBitsPerSample);
        /* check for constant subframe */
        boolean signalIsConstant = false;
        if (!disableConstantSubframes && fixedResidualBitsPerSample[1] == 0) {
            /* the above means it's possible all samples are the same value; now double-check it: */
            signalIsConstant = Arrays.equals(integerSignal, 0, blocksize - 1, integerSignal, 1, blocksize);
        }
        if (signalIsConstant) {
            EncodedSubframe candidate = evaluateConstantSubframe(integerSignal[0], subframeBps, wastedBits);
            if (candidate.bits < best.bits)
                best = candidate;
        } else {
            /* encode fixed */
            int minFixedOrder = doExhaustiveModelSearch ? 0 : guessFixedOrder;
            int maxFixedOrder = doExhaustiveModelSearch ? Format.MAX_FIXED_ORDER : guessFixedOrder;
            for (int fixedOrder = minFixedOrder; fixedOrder <= maxFixedOrder; fixedOrder++) {
                /* don't even try */
                if (fixedResidualBitsPerSample[fixedOrder] >= subframeBps)
                    continue;
                EncodedSubframe candidate = evaluateFixedSubframe(integerSignal, blocksize, subframeBps, fixedOrder,
                        riceParameterLimit, minPartitionOrder, maxPartitionOrder, wastedBits);
                if (candidate.bits < best.bits)
                    best = candidate;
            }
            /* encode lpc */
            int maxLpcOrder = Math.min(this.maxLpcOrder, blocksize - 1);
            if (maxLpcOrder > 0) {
                float[][] lpCoeff = new float[Format.MAX_LPC_ORDER][Format.MAX_LPC_ORDER];
                double[] lpcError = new double[Format.MAX_LPC_ORDER];
                ApplyApodizationState applyApodizationState = new ApplyApodizationState();
                while (applyApodizationState.a < apodizations.size()) {
                    /* If applyApodization() fails, try next apodization */
                    if (!applyApodization(applyApodizationState, blocksize, lpcError, maxLpcOrder, subframeBps,
                            integerSignal, lpCoeff))
                        continue;
                    int minLpcOrder = doExhaustiveModelSearch ? 1 : applyApodizationState.guessLpcOrder;
                    int maxLpcOrderThisApodization = doExhaustiveModelSearch ? applyApodizationState.maxLpcOrder
                            : applyApodizationState.guessLpcOrder;
                    for (int lpcOrder = minLpcOrder; lpcOrder <= maxLpcOrderThisApodization; lpcOrder++) {
                        double lpcResidualBitsPerSample = Lpc
                                .computeExpectedBitsPerResidualSample(lpcError[lpcOrder - 1], blocksize - lpcOrder);
                        if (lpcResidualBitsPerSample >= subframeBps)
                            continue; /* don't even try */
                        int minQlpCoeffPrecision, maxQlpCoeffPrecision;
                        if (doQlpCoeffPrecSearch) {
                            minQlpCoeffPrecision = Format.MIN_QLP_COEFF_PRECISION;
                            /* try to keep qlp coeff precision such that only 32-bit math is required for decode of <=16bps(+1bps for side channel) streams */
                            if (subframeBps <= 17) {
                                maxQlpCoeffPrecision = Math.min(
                                        Integer.SIZE - subframeBps - (31 - Integer.numberOfLeadingZeros(lpcOrder)),
                                        Format.MAX_QLP_COEFF_PRECISION);
                                maxQlpCoeffPrecision = Math.max(maxQlpCoeffPrecision, minQlpCoeffPrecision);
                            } else
                                maxQlpCoeffPrecision = Format.MAX_QLP_COEFF_PRECISION;
                        } else
                            minQlpCoeffPrecision = maxQlpCoeffPrecision = qlpCoeffPrecision;
                        for (int qlpCoeffPrecision = minQlpCoeffPrecision; qlpCoeffPrecision <= maxQlpCoeffPrecision; qlpCoeffPrecision++) {
                            EncodedSubframe candidate = evaluateLpcSubframe(integerSignal, lpCoeff[lpcOrder - 1],
                                    blocksize, subframeBps, lpcOrder, qlpCoeffPrecision, riceParameterLimit,
                                    minPartitionOrder, maxPartitionOrder, wastedBits);
                            /* if null, there was a problem quantizing the lpcoeffs */
                            if (candidate != null && candidate.bits < best.bits)
                                best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void setNextSubdivideTukey(int parts, ApplyApodizationState applyApodizationState) {
        /* applyApodizationState.c is interleaved: even are partial, odd are punchout */
        if (applyApodizationState.b == 2) {
            /* For depth 2, we only do partial, no punchout as that is almost redundant */
            if (applyApodizationState.c == 0)
                applyApodizationState.c = 2;
            else {
                applyApodizationState.c = 0;
                applyApodizationState.b++;
            }
        } else if (applyApodizationState.c < 2 * applyApodizationState.b - 1)
            applyApodizationState.c++;
        else {
            applyApodizationState.c = 0;
            applyApodizationState.b++;
        }
        /* Now check if we are done with this SUBDIVIDE_TUKEY apodization */
        if (applyApodizationState.b > parts) {
            applyApodizationState.a++;
            applyApodizationState.b = 1;
            applyApodizationState.c = 0;
        }
    }

    private boolean applyApodization(ApplyApodizationState applyApodizationState, int blocksize, double[] lpcError,
            int maxLpcOrderThisApodization, int subframeBps, long[] integerSignal, float[][] lpCoeff) {
        applyApodizationState.currentApodization = apodizations.get(applyApodizationState.a);
        float[] window = windows[applyApodizationState.a];
        if (applyApodizationState.b == 1) {
            /* window full subblock */
            Lpc.windowData(integerSignal, window, windowedSignal, blocksize);
            applyApodizationState.autoc = Lpc.computeAutocorrelation(windowedSignal, blocksize,
                    maxLpcOrderThisApodization + 1);
            if (applyApodizationState.currentApodization.type() == ApodizationFunction.SUBDIVIDE_TUKEY) {
                System.arraycopy(applyApodizationState.autoc, 0, applyApodizationState.autocRoot, 0,
                        maxLpcOrderThisApodization);
                applyApodizationState.b++;
            } else
                applyApodizationState.a++;
        } else {
            /* window part of subblock */
            if (blocksize / applyApodizationState.b <= Format.MAX_LPC_ORDER) {
                setNextSubdivideTukey(applyApodizationState.currentApodization.parts(), applyApodizationState);
                return false;
            }
            if ((applyApodizationState.c & 1) == 0) {
                /* on even c, evaluate the (c/2)th partial window of size blocksize/b */
                Lpc.windowDataPartial(integerSignal, window, windowedSignal, blocksize,
                        blocksize / applyApodizationState.b / 2,
                        (applyApodizationState.c / 2 * blocksize) / applyApodizationState.b);
                applyApodizationState.autoc = Lpc.computeAutocorrelation(windowedSignal,
                        blocksize / applyApodizationState.b, maxLpcOrderThisApodization + 1);
            } else
                /*
                 * on uneven c, evaluate the root window (over the whole block) minus the previous partial window
                 * similar to tukey_punchout apodization but more efficient
                 */
                for (int i = 0; i < maxLpcOrderThisApodization; i++)
                    applyApodizationState.autoc[i] = applyApodizationState.autocRoot[i]
                            - applyApodizationState.autoc[i];
            /* Next function sets a, b and c appropriate for next iteration */
            setNextSubdivideTukey(applyApodizationState.currentApodization.parts(), applyApodizationState);
        }
        /* Signal seems to be constant, so we can't do lp. Constant detection is probably disabled */
        if (applyApodizationState.autoc[0] == 0)
            return false;
        applyApodizationState.maxLpcOrder = Lpc.computeLpCoefficients(applyApodizationState.autoc,
                maxLpcOrderThisApodization, lpCoeff, lpcError);
        /* have to guess; use the min possible size to avoid accidentally favoring lower orders */
        applyApodizationState.guessLpcOrder = Lpc.computeBestOrder(lpcError, applyApodizationState.maxLpcOrder,
                blocksize, subframeBps + (doQlpCoeffPrecSearch ? Format.MIN_QLP_COEFF_PRECISION : qlpCoeffPrecision));
        return true;
    }

    private void addSubframe(EncodedSubframe encoded, BitWriter frame) throws IOException {
        Subframe subframe = encoded.subframe;
        try {
            switch (subframe.type) {
                case CONSTANT -> StreamEncoderFraming.subframeAddConstant(subframe, encoded.subframeBps,
                        subframe.wastedBits, frame);
                case FIXED -> StreamEncoderFraming.subframeAddFixed(subframe, subframe.residual.length,
                        encoded.subframeBps, subframe.wastedBits, frame);
                case LPC -> StreamEncoderFraming.subframeAddLpc(subframe, subframe.residual.length, encoded.subframeBps,
                        subframe.wastedBits, frame);
                case VERBATIM -> StreamEncoderFraming.subframeAddVerbatim(subframe, subframe.data.length,
                        encoded.subframeBps, subframe.wastedBits, frame);
            }
        } catch (IOException e) {
            state = StreamEncoderState.FRAMING_ERROR;
            throw e;
        }
    }

    private static EncodedSubframe evaluateConstantSubframe(long signal, int subframeBps, int wastedBits) {
        Subframe subframe = new Subframe();
        subframe.type = SubframeType.CONSTANT;
        subframe.value = signal;
        subframe.wastedBits = wastedBits;
        long estimate = (long) Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN
                + Format.SUBFRAME_WASTED_BITS_FLAG_LEN + wastedBits + subframeBps;
        return new EncodedSubframe(subframe, subframeBps, estimate);
    }

    private EncodedSubframe evaluateFixedSubframe(long[] signal, int blocksize, int subframeBps, int order,
            int riceParameterLimit, int minPartitionOrder, int maxPartitionOrder, int wastedBits) {
        int residualSamples = blocksize - order;
        Subframe subframe = new Subframe();
        subframe.residual = new int[residualSamples];
        Fixed.computeResidual(signal, order, residualSamples, order, subframe.residual);
        subframe.type = SubframeType.FIXED;
        subframe.data = signal;
        subframe.wastedBits = wastedBits;
        long residualBits = findBestPartitionOrder(subframe.residual, residualSamples, order, riceParameterLimit,
                minPartitionOrder, maxPartitionOrder, subframeBps, subframe.entropyCodingMethod);
        subframe.order = order;
        subframe.warmup = Arrays.copyOf(signal, order);
        long estimate = (long) Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN
                + Format.SUBFRAME_WASTED_BITS_FLAG_LEN + wastedBits + (long) order * subframeBps;
        /* To make sure estimate doesn't overflow */
        if (residualBits < 0xffffffffL - estimate)
            estimate += residualBits;
        else
            estimate = 0xffffffffL;
        return new EncodedSubframe(subframe, subframeBps, estimate);
    }

    private EncodedSubframe evaluateLpcSubframe(long[] signal, float[] lpCoeff, int blocksize, int subframeBps,
            int order, int qlpCoeffPrecision, int riceParameterLimit, int minPartitionOrder, int maxPartitionOrder,
            int wastedBits) {
        int residualSamples = blocksize - order;
        /* try to keep qlp coeff precision such that only 32-bit math is required for decode of <=16bps(+1bps for side channel) streams */
        if (subframeBps <= 17)
            qlpCoeffPrecision = Math.min(qlpCoeffPrecision,
                    Integer.SIZE - subframeBps - (31 - Integer.numberOfLeadingZeros(order)));
        int[] qlpCoeff = new int[order];
        int quantization = Lpc.quantizeCoefficients(lpCoeff, order, qlpCoeffPrecision, qlpCoeff);
        if (quantization < 0)
            return null;
        Subframe subframe = new Subframe();
        subframe.residual = new int[residualSamples];
        if (Lpc.maxResidualBps(subframeBps, qlpCoeff, order, quantization) > Integer.SIZE) {
            if (subframeBps <= Integer.SIZE) {
                if (!Lpc.computeResidualFromQlpCoefficientsLimitResidual(signal, order, residualSamples, qlpCoeff,
                        order, quantization, subframe.residual))
                    return null;
            } else if (!Lpc.computeResidualFromQlpCoefficientsLimitResidual33Bit(signal, order, residualSamples,
                    qlpCoeff, order, quantization, subframe.residual))
                return null;
        } else if (Lpc.maxPredictionBeforeShiftBps(subframeBps, qlpCoeff, order) <= Integer.SIZE)
            Lpc.computeResidualFromQlpCoefficients(signal, order, residualSamples, qlpCoeff, order, quantization,
                    subframe.residual);
        else
            Lpc.computeResidualFromQlpCoefficientsWide(signal, order, residualSamples, qlpCoeff, order, quantization,
                    subframe.residual);
        subframe.type = SubframeType.LPC;
        subframe.data = signal;
        subframe.wastedBits = wastedBits;
        long residualBits = findBestPartitionOrder(subframe.residual, residualSamples, order, riceParameterLimit,
                minPartitionOrder, maxPartitionOrder, subframeBps, subframe.entropyCodingMethod);
        subframe.order = order;
        subframe.qlpCoeffPrecision = qlpCoeffPrecision;
        subframe.quantizationLevel = quantization;
        subframe.qlpCoeff = qlpCoeff;
        subframe.warmup = Arrays.copyOf(signal, order);
        long estimate = (long) Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN
                + Format.SUBFRAME_WASTED_BITS_FLAG_LEN + wastedBits + Format.SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN
                + Format.SUBFRAME_LPC_QLP_SHIFT_LEN + (long) order * (qlpCoeffPrecision + subframeBps);
        /* To make sure estimate doesn't overflow */
        if (residualBits < 0xffffffffL - estimate)
            estimate += residualBits;
        else
            estimate = 0xffffffffL;
        return new EncodedSubframe(subframe, subframeBps, estimate);
    }

    private static EncodedSubframe evaluateVerbatimSubframe(long[] signal, int blocksize, int subframeBps,
            int wastedBits) {
        Subframe subframe = new Subframe();
        subframe.type = SubframeType.VERBATIM;
        subframe.dataType = subframeBps < 33 ? VerbatimSubframeDataType.INT32 : VerbatimSubframeDataType.INT64;
        subframe.data = signal;
        subframe.wastedBits = wastedBits;
        long estimate = (long) Format.SUBFRAME_ZERO_PAD_LEN + Format.SUBFRAME_TYPE_LEN
                + Format.SUBFRAME_WASTED_BITS_FLAG_LEN + wastedBits + (long) blocksize * subframeBps;
        return new EncodedSubframe(subframe, subframeBps, estimate);
    }

    private static long findBestPartitionOrder(int[] residual, int residualSamples, int predictorOrder,
            int riceParameterLimit, int minPartitionOrder, int maxPartitionOrder, int bps,
            EntropyCodingMethod bestEcm) {
        int blocksize = residualSamples + predictorOrder;
        maxPartitionOrder = Format.getMaxRicePartitionOrderFromBlocksizeLimitedMaxAndPredictorOrder(maxPartitionOrder,
                blocksize, predictorOrder);
        minPartitionOrder = Math.min(minPartitionOrder, maxPartitionOrder);
        long[] absResidualPartitionSums = new long[(1 << (maxPartitionOrder + 1)) - (1 << minPartitionOrder)];
        precomputePartitionInfoSums(residual, absResidualPartitionSums, residualSamples, predictorOrder,
                minPartitionOrder, maxPartitionOrder, bps);
        long bestResidualBits = 0;
        EntropyCodingMethod bestPartitionedRiceContents = null;
        for (int partitionOrder = maxPartitionOrder, sum = 0; partitionOrder >= minPartitionOrder; partitionOrder--) {
            int partitions = 1 << partitionOrder;
            EntropyCodingMethod partitionedRiceContents = new EntropyCodingMethod();
            partitionedRiceContents.order = partitionOrder;
            partitionedRiceContents.parameters = new int[partitions];
            partitionedRiceContents.rawBits = new int[partitions];
            long residualBits = setPartitionedRice(absResidualPartitionSums, sum, residualSamples, predictorOrder,
                    riceParameterLimit, partitionOrder, partitionedRiceContents);
            if (residualBits < 0)
                break;
            sum += partitions;
            if (bestResidualBits == 0 || residualBits < bestResidualBits) {
                bestResidualBits = residualBits;
                bestPartitionedRiceContents = partitionedRiceContents;
            }
        }
        if (bestPartitionedRiceContents == null)
            throw new IllegalStateException("invalid residual partition");
        bestEcm.order = bestPartitionedRiceContents.order;
        /* save best parameters and rawBits */
        bestEcm.parameters = bestPartitionedRiceContents.parameters;
        bestEcm.rawBits = bestPartitionedRiceContents.rawBits;
        /*
         * Now need to check if the type should be changed to
         * EntropyCodingMethodType.PARTITIONED_RICE2 based on the
         * size of the rice parameters.
         */
        for (int k : bestEcm.parameters)
            if (k >= Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER) {
                bestEcm.type = EntropyCodingMethodType.PARTITIONED_RICE2;
                break;
            }
        return bestResidualBits;
    }

    private static void precomputePartitionInfoSums(int[] residual, long[] absResidualPartitionSums,
            int residualSamples, int predictorOrder, int minPartitionOrder, int maxPartitionOrder, int bps) {
        int defaultPartitionSamples = (residualSamples + predictorOrder) >> maxPartitionOrder;
        int partitions = 1 << maxPartitionOrder;
        /* first do maxPartitionOrder */
        int threshold = Integer.SIZE - (Integer.SIZE - 1 - Integer.numberOfLeadingZeros(defaultPartitionSamples));
        /* WATCHOUT: "bps + MAX_EXTRA_RESIDUAL_BPS" is the maximum assumed size of the average residual magnitude */
        if (bps + MAX_EXTRA_RESIDUAL_BPS < threshold) {
            for (int partition = 0, residualSample = 0, end = -predictorOrder; partition < partitions; partition++) {
                int absResidualPartitionSum = 0;
                end += defaultPartitionSamples;
                for (; residualSample < end; residualSample++)
                    absResidualPartitionSum += Math.abs((long) residual[residualSample]);
                absResidualPartitionSums[partition] = Integer.toUnsignedLong(absResidualPartitionSum);
            }
        } else {
            /* have to pessimistically use 64 bits for accumulator */
            for (int partition = 0, residualSample = 0, end = -predictorOrder; partition < partitions; partition++) {
                long absResidualPartitionSum64 = 0;
                end += defaultPartitionSamples;
                for (; residualSample < end; residualSample++)
                    absResidualPartitionSum64 += Math.abs((long) residual[residualSample]);
                absResidualPartitionSums[partition] = absResidualPartitionSum64;
            }
        }
        /* now merge partitions for lower orders */
        for (int partitionOrder = maxPartitionOrder - 1, fromPartition = 0,
                toPartition = partitions; partitionOrder >= minPartitionOrder; partitionOrder--) {
            partitions >>= 1;
            for (int i = 0; i < partitions; i++) {
                absResidualPartitionSums[toPartition++] = absResidualPartitionSums[fromPartition]
                        + absResidualPartitionSums[fromPartition + 1];
                fromPartition += 2;
            }
        }
    }

    /*
     * The simple
     * (and fast) estimation (of how many bits a residual value will be
     * encoded with) in this encoder is very good, almost always yielding
     * compression within 0.1% of exact calculation.
     */
    private static long countRiceBitsInPartition(int riceParameter, int partitionSamples,
            long absResidualPartitionSum) {
        /* actually could end up being Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN but err on side of 16bps */
        /* To make sure the return value doesn't overflow */
        return Math.min(0xffffffffL, (long) Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN
                /* 1 for unary stop bit + riceParameter for the binary portion */
                + (1L + riceParameter) * partitionSamples
                /* riceParameter-1 because the real coder sign-folds instead of using a sign bit */

                /* can't shift by negative number, so reverse */
                + (riceParameter == 0 ? absResidualPartitionSum << 1 : absResidualPartitionSum >>> (riceParameter - 1))
                /*
                 * -(partitionSamples>>1) to subtract out extra contributions to the absResidualPartitionSum.
                 * The actual number of bits used is closer to the sum(for all i in the partition) of  abs(residual[i])>>(riceParameter-1)
                 * By using the absResidualPartitionSum, we also add in bits in the LSBs that would normally be shifted out.
                 * So the subtraction term tries to guess how many extra bits were contributed.
                 * If the LSBs are randomly distributed, this should average to 0.5 extra bits per sample.
                 */
                - (partitionSamples >> 1));
    }

    private static long setPartitionedRice(long[] absResidualPartitionSums, int sumOffset, int residualSamples,
            int predictorOrder, int riceParameterLimit, int partitionOrder,
            EntropyCodingMethod partitionedRiceContents) {
        int partitionSamplesBase = (residualSamples + predictorOrder) >> partitionOrder;
        /*
         * Integer division is slow. To speed up things, precalculate a fixed point
         * divisor, as all partitions except the first are the same size. 18 bits
         * are taken because maximum block size is 65535, max partition size for
         * partitions other than 0 is 32767 (15 bit), max abs residual is 2^31,
         * which leaves 18 bit
         */
        int partitionSamplesFixedPointDivisorBase = 0x40000 / partitionSamplesBase;
        long bits = Format.ENTROPY_CODING_METHOD_TYPE_LEN + Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN;
        int bestRiceParameter = 0;
        for (int partition = 0; partition < 1 << partitionOrder; partition++) {
            int partitionSamples = partitionSamplesBase;
            int partitionSamplesFixedPointDivisor = partitionSamplesFixedPointDivisorBase;
            if (partition == 0) {
                if (partitionSamples <= predictorOrder)
                    return -1;
                partitionSamples -= predictorOrder;
                partitionSamplesFixedPointDivisor = 0x40000 / partitionSamples;
            }
            long mean = absResidualPartitionSums[sumOffset + partition];
            /*
             * 'mean' is not a good name for the variable, it is
             * actually the sum of magnitudes of all residual values
             * in the partition, so the actual mean is
             * mean/partitionSamples
             */
            long scaled = ((mean - 1) * partitionSamplesFixedPointDivisor) >>> 18;
            int riceParameter = mean < 2 || scaled == 0 ? 0 : Long.SIZE - Long.numberOfLeadingZeros(scaled);
            if (riceParameter >= riceParameterLimit)
                riceParameter = riceParameterLimit - 1;
            long bestPartitionBits = 0xffffffffL;
            long partitionBits = countRiceBitsInPartition(riceParameter, partitionSamples, mean);
            if (partitionBits < bestPartitionBits) {
                bestRiceParameter = riceParameter;
                bestPartitionBits = partitionBits;
            }
            partitionedRiceContents.parameters[partition] = bestRiceParameter;
            /* To make sure bits doesn't overflow */
            if (bestPartitionBits < 0xffffffffL - bits)
                bits += bestPartitionBits;
            else
                bits = 0xffffffffL;
        }
        return bits;
    }

    private static int getWastedBits(long[] signal, int samples) {
        int x = 0;
        for (int i = 0; i < samples && (x & 1) == 0; i++)
            x |= (int) signal[i];
        int shift = x == 0 ? 0 : Integer.numberOfTrailingZeros(x);
        if (shift > 0)
            for (int i = 0; i < samples; i++)
                signal[i] = (int) signal[i] >> shift;
        return shift;
    }

    private static int getWastedBitsWide(long[] signalWide, int samples) {
        long x = 0;
        for (int i = 0; i < samples && (x & 1) == 0; i++)
            x |= signalWide[i];
        int shift = x == 0 ? 1 : Long.numberOfTrailingZeros(x);

        if (shift > 0)
            for (int i = 0; i < samples; i++)
                signalWide[i] = (int) (signalWide[i] >> shift);
        return shift;
    }

    private int verifyReadCallback(StreamDecoder decoder, byte[] buffer, int offset, int bytes) {
        if (verifyNeedsMagicHack) {
            System.arraycopy(Format.STREAM_SYNC_STRING, 0, buffer, offset, Format.STREAM_SYNC_STRING.length);
            verifyNeedsMagicHack = false;
            return Format.STREAM_SYNC_STRING.length;
        }
        int encodedBytes = verifyOutput.length - verifyOutputOffset;
        if (encodedBytes == 0)
            /*
             * If we get here, a FIFO underflow has occurred,
             * which means there is a bug somewhere.
             */
            return -2;
        bytes = Math.min(bytes, encodedBytes);
        System.arraycopy(verifyOutput, verifyOutputOffset, buffer, offset, bytes);
        verifyOutputOffset += bytes;
        return bytes;
    }

    private boolean verifyWriteCallback(StreamDecoder decoder, Frame frame, int[][] buffer) {
        /* This is set when verify error callback was called */
        if (state == StreamEncoderState.VERIFY_DECODER_ERROR)
            return false;
        int channels = frame.header.channels, blocksize = frame.header.blocksize;
        for (int channel = 0; channel < channels; channel++) {
            int sample = Arrays.mismatch(buffer[channel], 0, blocksize, pending[channel], 0, blocksize);
            if (sample >= 0) {
                int expected = pending[channel][sample], got = buffer[channel][sample];
                verifyErrorStats = new VerifyErrorStats(frame.header.number + sample,
                        (int) (frame.header.number / blocksize), channel, sample, expected, got);
                state = StreamEncoderState.VERIFY_MISMATCH_IN_AUDIO_DATA;
                return false;
            }
        }

        return true;
    }

    private int fileReadCallback(StreamEncoder encoder, byte[] buffer, int offset, int bytes) throws IOException {
        return file.read(buffer, offset, bytes);
    }

    private StreamEncoderSeekStatus fileSeekCallback(StreamEncoder encoder, long absoluteByteOffset)
            throws IOException {
        file.seek(absoluteByteOffset);
        return StreamEncoderSeekStatus.OK;
    }

    private long fileTellCallback(StreamEncoder encoder) throws IOException {
        return file.getFilePointer();
    }

    private boolean fileWriteCallback(StreamEncoder encoder, byte[] buffer, int offset, int bytes, int samples,
            int currentFrame) throws IOException {
        if (standardOutput) {
            System.out.write(buffer, offset, bytes);
            if (System.out.checkError())
                return false;
        } else {
            try {
                file.write(buffer, offset, bytes);
            } catch (IOException error) {
                state = StreamEncoderState.CLIENT_ERROR;
                throw error;
            }
        }
        /*
         * We would like to be able to use 'samples > 0' in the
         * clause here but currently because of the nature of our
         * Ogg writing implementation, 'samples' is always 0 (see OggEncoderAspect).  The downside is extra progress
         * callbacks.
         */
        if (progressCallback != null && (isOgg || samples > 0))
            /*
             * NOTE: We have to add +bytes, +samples, and +1 to the stats
             * because at this point in the callback chain, the stats
             * have not been updated.  Only after we return and control
             * gets back to writeFrame() are the stats updated
             */
            progressCallback.progress(this, bytesWritten + bytes, samplesWritten + samples,
                    framesWritten + (samples > 0 ? 1 : 0), totalFramesEstimate);
        return true;
    }

    private void writeOut(byte[] data, int offset, int length, int samples) throws IOException {
        try {
            if (!writeCallback.write(this, data, offset, length, samples, currentFrameNumber)) {
                state = StreamEncoderState.CLIENT_ERROR;
                throw new IOException("encoder write callback failed");
            }
        } catch (Exception e) {
            if (state == StreamEncoderState.OK)
                state = StreamEncoderState.CLIENT_ERROR;
            throw e;
        }
    }

    @FunctionalInterface
    public interface WriteCallback {
        /**
         * Signature for the write callback.
         *
         *  A callback matching this signature must be passed to
         *  StreamEncoder.init*Stream().  The supplied function will be called
         *  by the encoder anytime there is raw encoded data ready to write.  It may
         *  include metadata mixed with encoded audio frames and the data is not
         *  guaranteed to be aligned on frame or metadata block boundaries.
         *
         *  The only duty of the callback is to write out the {@code bytes} worth of data
         *  in {@code buffer} to the current position in the output stream.  The arguments
         *  {@code samples} and {@code currentFrame} are purely informational.  If {@code samples}
         *  is greater than {@code 0}, then {@code currentFrame} will hold the current frame
         *  number that is being written; otherwise it indicates that the write
         *  callback is being called to write metadata.
         *
         * <p>Unlike when writing to native FLAC, when writing to Ogg FLAC the
         * write callback will be called twice when writing each audio
         * frame; once for the page header, and once for the page body.
         * When writing the page header, the {@code samples} argument to the
         * write callback will be {@code 0}.
         *
         * <p>In general, StreamEncoder functions which change the
         * state should not be called on the {@code encoder} while in the callback.
         *
         * @param encoder The encoder instance calling the callback.
         * @param buffer An array of encoded data.
         * @param bytes The number of bytes to write from the buffer.
         * @param samples The number of samples encoded by {@code buffer}.
         *                  {@code 0} has a special meaning; see above.
         * @param currentFrame The number of the current frame being encoded.
         * @return true if the data was accepted, or false if a fatal error occurred.
         */
        boolean write(StreamEncoder encoder, byte[] buffer, int offset, int bytes, int samples, int currentFrame)
                throws IOException;
    }

    @FunctionalInterface
    public interface ReadCallback {
        /**
         * Signature for the read callback.
         *
         *  A callback matching this signature must be passed to
         *  StreamEncoder.initOggStream() if seeking is supported.
         *  The supplied function will be called when the encoder needs to read back
         *  encoded data.  This happens during the metadata callback, when the encoder
         *  has to read, modify, and rewrite the metadata (e.g. seekpoints) gathered
         *  while encoding.  The buffer to be filled is supplied, along
         *  with the number of bytes the buffer can hold.  The callback may choose to
         *  supply less data and return the byte count but must be careful not to
         *  overflow the buffer.  The callback then returns the number of bytes read.
         *
         * <p>In general, StreamEncoder functions which change the
         * state should not be called on the {@code encoder} while in the callback.
         *
         * @param encoder The encoder instance calling the callback.
         * @param buffer A buffer for the callee to store data to be encoded.
         * @param bytes The maximum number of bytes that may be stored in the buffer.
         * @return The number of bytes read, -1 at end-of-stream, -2 to abort, or -3 if unsupported.
         */
        int read(StreamEncoder encoder, byte[] buffer, int offset, int bytes) throws IOException;
    }

    @FunctionalInterface
    public interface SeekCallback {
        /**
         * Signature for the seek callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamEncoder.init*Stream().  The supplied function will be called
         *  when the encoder needs to seek the output stream.  The encoder will pass
         *  the absolute byte offset to seek to, 0 meaning the beginning of the stream.
         *
         * <p>In general, StreamEncoder functions which change the
         * state should not be called on the {@code encoder} while in the callback.
         *
         * @param encoder The encoder instance calling the callback.
         * @param absoluteByteOffset The offset from the beginning of the stream
         *                               to seek to.
         * @return The callee's return status.
         */
        StreamEncoderSeekStatus seek(StreamEncoder encoder, long absoluteByteOffset) throws IOException;
    }

    @FunctionalInterface
    public interface TellCallback {
        /**
         * Signature for the tell callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamEncoder.init*Stream().  The supplied function will be called
         *  when the encoder needs to know the current position of the output stream.
         *
         * <p>The callback must return the true current byte offset of the output to
         * which the encoder is writing.  If you are buffering the output, make
         * sure and take this into account.  If you are writing directly to a
         * RandomAccessFile from your write callback, RandomAccessFile.getFilePointer() is sufficient.    The encoder
         * may later seek back to
         * these points to rewrite metadata after encoding.
         *
         * <p>In general, StreamEncoder functions which change the
         * state should not be called on the {@code encoder} while in the callback.
         *
         * @param encoder The encoder instance calling the callback.
         * @return The current position, -1 if seeking is unsupported, or -2 on error.
         */
        long tell(StreamEncoder encoder) throws IOException;
    }

    @FunctionalInterface
    public interface MetadataCallback {
        /**
         * Signature for the metadata callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamEncoder.init*Stream().  The supplied function will be called
         *  once at the end of encoding with the populated STREAMINFO structure.  This
         *  is so the client can seek back to the beginning of the file and write the
         *  STREAMINFO block with the correct statistics after encoding (like
         *  minimum/maximum frame size and total samples).
         *
         * <p>In general, StreamEncoder functions which change the
         * state should not be called on the {@code encoder} while in the callback.
         *
         * @param encoder The encoder instance calling the callback.
         */
        void metadata(StreamEncoder encoder, StreamInfo streamInfo);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Signature for the progress callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamEncoder.init*File().
         *  The supplied function will be called when the encoder has finished
         *  writing a frame.  The {@code totalFramesEstimate} argument to the
         *  callback will be based on the value from
         *  StreamEncoder.setTotalSamplesEstimate().
         *
         * <p>In general, StreamEncoder functions which change the
         * state should not be called on the {@code encoder} while in the callback.
         *
         * @param encoder The encoder instance calling the callback.
         * @param bytesWritten Bytes written so far.
         * @param samplesWritten Samples written so far.
         * @param framesWritten Frames written so far.
         * @param totalFramesEstimate The estimate of the total number of
         *                                frames to be written.
         */
        void progress(StreamEncoder encoder, long bytesWritten, long samplesWritten, int framesWritten,
                int totalFramesEstimate);
    }

    private record EncodedSubframe(Subframe subframe, int subframeBps, long bits) {
    }
}
