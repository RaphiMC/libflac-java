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

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This module contains the functions which implement the stream
 *  decoder.
 *
 * The stream decoder can decode native FLAC, and Ogg FLAC streams and files.
 *
 * The basic usage of this decoder is as follows:
 * - The program creates an instance of a decoder using
 *   new StreamDecoder().
 * - The program overrides the default settings using
 *   StreamDecoder.set*() functions.
 * - The program initializes the instance to validate the settings and
 *   prepare for decoding using
 *   - StreamDecoder.initStream() or StreamDecoder.initFile() for native FLAC,
 *   - StreamDecoder.initOggStream() or StreamDecoder.initOggFile() for Ogg FLAC
 * - The program calls the StreamDecoder.process*() functions
 *   to decode data, which subsequently calls the callbacks.
 * - The program finishes the decoding with StreamDecoder.finish(),
 *   which flushes the input and output and resets the decoder to the
 *   uninitialized state.
 * - The instance may be used again or deleted with
 *   StreamDecoder.close().
 *
 * In more detail, the program will create a new instance by calling
 * new StreamDecoder(), then call StreamDecoder.set*()
 * functions to override the default decoder options, and call
 * one of the StreamDecoder.init*() functions.
 *
 * There are three initialization functions for native FLAC, one for
 * setting up the decoder to decode FLAC data from the client via
 * callbacks, and two for decoding directly from a FLAC file.
 *
 * For decoding via callbacks, use StreamDecoder.initStream().
 * You must also supply several callbacks for handling I/O.  Some (like
 * seeking) are optional, depending on the capabilities of the input.
 *
 * For decoding directly from a file, use StreamDecoder.initFile().  Then you must only supply an open
 * {@code RandomAccessFile} or filename and fewer callbacks; the decoder will handle
 * the other callbacks internally.
 *
 * There are three similarly-named init functions for decoding from Ogg
 * FLAC streams.
 *
 * Once the decoder is initialized, your program will call one of several
 * functions to start the decoding process:
 *
 * - StreamDecoder.processSingle() - Tells the decoder to process at
 *   most one metadata block or audio frame and return, calling either the
 *   metadata callback or write callback, respectively, once.  If the decoder
 *   loses sync it will return with only the error callback being called.
 * - StreamDecoder.processUntilEndOfMetadata() - Tells the decoder
 *   to process the stream from the current location and stop upon reaching
 *   the first audio frame.  The client will get one metadata, write, or error
 *   callback per metadata block, audio frame, or sync error, respectively.
 * - StreamDecoder.processUntilEndOfStream() - Tells the decoder
 *   to process the stream from the current location until the read callback
 *   returns -1 or
 *   -2.  The client will get one metadata,
 *   write, or error callback per metadata block, audio frame, or sync error,
 *   respectively.
 *
 * When the decoder has finished decoding (normally or through an abort),
 * the instance is finished by calling StreamDecoder.finish(), which
 * ensures the decoder is in the correct state and frees memory.  Then the
 * instance may be deleted with StreamDecoder.close() or initialized
 * again to decode another stream.
 *
 * Seeking is exposed through the StreamDecoder.seekAbsolute() method.
 * At any point after the stream decoder has been initialized, the client can
 * call this function to seek to an exact sample within the stream.
 * Subsequently, the first time the write callback is called it will be
 * passed a (possibly partial) block starting at that sample.
 *
 * If the client cannot seek via the callback interface provided, but still
 * has another way of seeking, it can flush the decoder using
 * StreamDecoder.flush() and start feeding data from the new position
 * through the read callback.
 *
 * The stream decoder also provides MD5 signature checking.  If this is
 * turned on before initialization, StreamDecoder.finish() will
 * report when the decoded MD5 signature does not match the one stored
 * in the STREAMINFO block.  MD5 checking is automatically turned off
 * (until the next StreamDecoder.reset()) if there is no signature
 * in the STREAMINFO block or when a seek is attempted.
 *
 * The StreamDecoder.setMetadata*() functions deserve special
 * attention.  By default, the decoder only calls the metadataCallback for
 * the STREAMINFO block.  These functions allow you to tell the decoder
 * explicitly which blocks to parse and return via the metadataCallback
 * and/or which to skip.  Use a StreamDecoder.setMetadataRespondAll(),
 * StreamDecoder.setMetadataIgnore() ... or StreamDecoder.setMetadataIgnoreAll(),
 * StreamDecoder.setMetadataRespond() ... sequence to exactly specify
 * which blocks to return.  Remember that metadata blocks can potentially
 * be big (for example, cover art) so filtering out the ones you don't
 * use can reduce the memory requirements of the decoder.  Also note the
 * special forms StreamDecoder.setMetadataRespondApplication(id)
 * and StreamDecoder.setMetadataIgnoreApplication(id) for
 * filtering APPLICATION blocks based on the application ID.
 *
 * STREAMINFO and SEEKTABLE blocks are always parsed and used internally, but
 * they still can legally be filtered from the metadataCallback.
 *
 * <p>The "set" functions may only be called when the decoder is in the
 * state StreamDecoderState.UNINITIALIZED, i.e. after
 * new StreamDecoder() or StreamDecoder.finish(), but
 * before StreamDecoder.init*().  Otherwise, IllegalStateException is thrown.
 *
 * <p>StreamDecoder.finish() resets all settings to the constructor
 * defaults, including the callbacks.
 *
 */
public final class StreamDecoder implements AutoCloseable {
    private static final byte[] ID3V2_TAG_ = {'I', 'D', '3'};
    /*
     * We will switch to a linear search once our current sample is less
     * than this number of samples ahead of the target sample
     */
    private static final long LINEAR_SEARCH_WITHIN_SAMPLES = Format.MAX_BLOCK_SIZE * 2L;

    private StreamDecoderState state = StreamDecoderState.UNINITIALIZED;
    private FrameHeader frameHeader = new FrameHeader();
    private boolean md5Checking;
    private OggDecoderAspect oggDecoderAspect;
    private Integer oggSerialNumber;

    private boolean isOgg;
    private ReadCallback readCallback;
    private SeekCallback seekCallback;
    private TellCallback tellCallback;
    private LengthCallback lengthCallback;
    private EofCallback eofCallback;
    private WriteCallback writeCallback;
    private MetadataCallback metadataCallback;
    private ErrorCallback errorCallback;
    /** only used if StreamDecoder.init*File() called, else null */
    private RandomAccessFile file;
    private boolean standardInput;
    private BitReader input;
    private CallbackInput callbackInput;
    private int fixedBlockSize;
    private long samplesDecoded;
    private boolean hasStreamInfo;
    private final StreamInfo streamInfo = new StreamInfo();
    private SeekTable seekTable;
    /** MAGIC number 128 == total number of metadata block types == 1 &lt;&lt; 7 */
    private final boolean[] metadataFilter = new boolean[128];
    private final Set<Integer> metadataFilterIds = new HashSet<>();
    /** temp storage when we need to look ahead one byte in the stream */
    private int cachedByte = -1;
    /** contains the sync code and reserved bits */
    private final byte[] headerWarmup = new byte[2];
    /** initially gets md5Checking but is turned off after a seek or if the metadata has a zero MD5 */
    private boolean doMd5Checking;
    private boolean isSeeking;
    private final Md5Context md5 = new Md5Context();
    /** holds the info of the last frame we decoded or seeked to */
    private FrameHeader lastFrameHeader;
    /** hint to the seek routine of where in the stream the first audio frame starts */
    private long firstFrameOffset;
    /** if tell callback works, the location of the last seen frame sync code, to rewind to if needed */
    private long lastSeenFrameSync;
    private long targetSample;
    /** used to tell whether we're decoding a future version of FLAC or just got a bad sync */
    private int unparseableFrameCount;
    /** hack needed in Ogg FLAC seek routine to check when processSingle() actually writes a frame */
    private boolean gotAFrame;

    /**
     * Create a new stream decoder instance.  The instance is created with
     *  default settings; see the individual StreamDecoder.set*()
     *  functions for each setting's default.
     */
    public StreamDecoder() {
        setDefaults();
    }

    /**
     * Set the serial number for the FLAC stream within the Ogg container.
     *  The default behavior is to use the serial number of the first Ogg
     *  page.  Setting a serial number here will explicitly specify which
     *  stream is to be decoded.
     *
     * <p>This does not need to be set for native FLAC decoding.
     *
     * <p>Default: {@code use} serial number of first page
     *
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setOggSerialNumber(long value) {
        requireUninitialized();
        /* can't check isOgg since that's not set until init time */
        oggSerialNumber = (int) value;
    }

    /**
     * Set the "MD5 signature checking" flag.  If {@code true}, the decoder will
     *  compute the MD5 signature of the unencoded audio data while decoding
     *  and compare it to the signature from the STREAMINFO block, if it
     *  exists, during StreamDecoder.finish().
     *
     *  MD5 signature checking will be turned off (until the next
     *  StreamDecoder.reset()) if there is no signature in the
     *  STREAMINFO block or when a seek is attempted.
     *
     *  Clients that do not use the MD5 check should leave this off to speed
     *  up decoding.
     *
     * <p>Default: {@code false}
     *
     * @param value Flag value (see above).
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMd5Checking(boolean value) {
        requireUninitialized();
        md5Checking = value;
    }

    /**
     * Direct the decoder to pass on all metadata blocks of type {@code type}.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @param type See above.
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataRespond(MetadataType type) {
        requireUninitialized();
        metadataFilter[type.ordinal()] = true;
        if (type == MetadataType.APPLICATION)
            metadataFilterIds.clear();
    }

    /**
     * Direct the decoder to pass on all metadata blocks of type {@code typeCode}.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataRespond(int typeCode) {
        requireUninitialized();
        if (typeCode < 0 || typeCode > Format.MAX_METADATA_TYPE_CODE)
            throw new IllegalArgumentException("metadata type");
        metadataFilter[typeCode] = true;
        if (typeCode == MetadataType.APPLICATION.ordinal())
            metadataFilterIds.clear();
    }

    /**
     * Direct the decoder to pass on all APPLICATION metadata blocks of the
     *  given {@code id}.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @param id See above.
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataRespondApplication(byte[] id) {
        requireUninitialized();
        if (metadataFilter[MetadataType.APPLICATION.ordinal()])
            return;
        metadataFilterIds.add(applicationId(id));
    }

    /**
     * Direct the decoder to pass on all metadata blocks of any type.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataRespondAll() {
        requireUninitialized();
        Arrays.fill(metadataFilter, true);
        metadataFilterIds.clear();
    }

    /**
     * Direct the decoder to filter out all metadata blocks of type {@code type}.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @param type See above.
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataIgnore(MetadataType type) {
        requireUninitialized();
        metadataFilter[type.ordinal()] = false;
        if (type == MetadataType.APPLICATION)
            metadataFilterIds.clear();
    }

    /**
     * Direct the decoder to filter out all metadata blocks of type {@code typeCode}.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataIgnore(int typeCode) {
        requireUninitialized();
        if (typeCode < 0 || typeCode > Format.MAX_METADATA_TYPE_CODE)
            throw new IllegalArgumentException("metadata type");
        metadataFilter[typeCode] = false;
        if (typeCode == MetadataType.APPLICATION.ordinal())
            metadataFilterIds.clear();
    }

    /**
     * Direct the decoder to filter out all APPLICATION metadata blocks of
     *  the given {@code id}.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @param id See above.
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataIgnoreApplication(byte[] id) {
        requireUninitialized();
        if (!metadataFilter[MetadataType.APPLICATION.ordinal()])
            return;
        metadataFilterIds.add(applicationId(id));
    }

    /**
     * Direct the decoder to filter out all metadata blocks of any type.
     *
     * <p>Default: By default, only the {@code STREAMINFO} block is returned via the
     *          metadata callback.
     *
     * @throws IllegalStateException if the decoder is already initialized.
     */
    public void setMetadataIgnoreAll() {
        requireUninitialized();
        Arrays.fill(metadataFilter, false);
        metadataFilterIds.clear();
    }

    /**
     * Get the current decoder state.
     *
     * @return The current decoder state.
     */
    public StreamDecoderState getState() {
        return state;
    }

    /**
     * Get the current decoder state as a String.
     *
     * @return
     *    The decoder state as a String.
     */
    public String getResolvedStateString() {
        return "FLAC__STREAM_DECODER_" + state.name();
    }

    /**
     * Get the "MD5 signature checking" flag.
     *  This is the value of the setting, not whether or not the decoder is
     *  currently checking the MD5 (remember, it can be turned off automatically
     *  by a seek).  When the decoder is reset the flag will be restored to the
     *  value returned by this function.
     *
     * @return See above.
     */
    public boolean getMd5Checking() {
        return md5Checking;
    }

    /**
     * Get the total number of samples in the stream being decoded.
     *  Will only be valid after decoding has started and will contain the
     *  value from the {@code STREAMINFO} block.  A value of {@code 0} means "unknown".
     *
     * @return See above.
     */
    public long getTotalSamples() {
        return hasStreamInfo ? streamInfo.totalSamples : 0;
    }

    /**
     * Get the current number of channels in the stream being decoded.
     *  Will only be valid after decoding has started and will contain the
     *  value from the most recently decoded frame header.
     *
     * @return See above.
     */
    public int getChannels() {
        return frameHeader.channels;
    }

    /**
     * Get the current channel assignment in the stream being decoded.
     *  Will only be valid after decoding has started and will contain the
     *  value from the most recently decoded frame header.
     *
     * @return See above.
     */
    public ChannelAssignment getChannelAssignment() {
        return frameHeader.channelAssignment;
    }

    /**
     * Get the current sample resolution in the stream being decoded.
     *  Will only be valid after decoding has started and will contain the
     *  value from the most recently decoded frame header.
     *
     * @return See above.
     */
    public int getBitsPerSample() {
        return frameHeader.bitsPerSample;
    }

    /**
     * Get the current sample rate in Hz of the stream being decoded.
     *  Will only be valid after decoding has started and will contain the
     *  value from the most recently decoded frame header.
     *
     * @return See above.
     */
    public int getSampleRate() {
        return frameHeader.sampleRate;
    }

    /**
     * Get the current blocksize of the stream being decoded.
     *  Will only be valid after decoding has started and will contain the
     *  value from the most recently decoded frame header.
     *
     * @return See above.
     */
    public int getBlocksize() {
        return frameHeader.blocksize;
    }

    /**
     * Returns the decoder's current read position within the stream.
     *  The position is the byte offset from the start of the stream.
     *  Bytes before this position have been fully decoded.  Note that
     *  there may still be undecoded bytes in the decoder's read FIFO.
     *  The returned position is correct even after a seek.
     *
     * <p>This function currently only works for native FLAC,
     *           not Ogg FLAC streams.
     *
     * @return The decode position if successful, or -1 if the stream is not native FLAC, or there was an error from the
     * tell callback or it returned -1.
     */
    public long getDecodePosition() throws IOException {
        if (isOgg || tellCallback == null || callbackInput == null)
            return -1;
        long p = tellCallback.tell(this);
        /* should never happen since all FLAC frames and metadata blocks are byte aligned, but check just in case */
        return p < 0 || !input.isConsumedByteAligned() ? -1 : p - getInputBytesUnconsumed();
    }

    /**
     * Initialize the decoder instance to decode native FLAC streams.
     *
     *  This flavor of initialization sets up the decoder to decode from a
     *  native FLAC stream. I/O is performed via callbacks to the client.
     *  For decoding from a plain file via filename or open RandomAccessFile,
     *  StreamDecoder.initFile() provides a simpler interface.
     *
     *  This function should be called after new StreamDecoder() and
     *  StreamDecoder.set*() but before any of the
     *  StreamDecoder.process*() functions.  Will set and return the
     *  decoder state, which will be {@code StreamDecoderState.SEARCH_FOR_METADATA}
     *  if initialization succeeded.
     *
     * @param readCallback See StreamDecoder.ReadCallback.  This
     *                            callback must not be {@code null}.
     * @param seekCallback See StreamDecoder.SeekCallback.  This
     *                            callback may be {@code null} if seeking is not
     *                            supported.  If {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback}, {@code lengthCallback}, and {@code eofCallback} must also be
     * supplied.
     *                            Alternatively, a dummy seek callback that just
     *                            returns {@code StreamDecoderSeekStatus.UNSUPPORTED}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param tellCallback See StreamDecoder.TellCallback.  This
     *                            callback may be {@code null} if not supported by the client.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback} must also be supplied.
     *                            Alternatively, a dummy tell callback that just
     *                            returns {@code -1}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param lengthCallback See StreamDecoder.LengthCallback.  This
     *                            callback may be {@code null} if not supported by the client.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code lengthCallback} must also be supplied.
     *                            Alternatively, a dummy length callback that just
     *                            returns {@code -1}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param eofCallback See StreamDecoder.EofCallback.  This
     *                            callback may be {@code null} if not supported by the client.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code eofCallback} must also be supplied.
     *                            Alternatively, a dummy length callback that just
     *                            returns {@code false}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param writeCallback See StreamDecoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param metadataCallback See StreamDecoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @param errorCallback See StreamDecoder.ErrorCallback.  This
     *                            callback must not be {@code null}.
     * @return {@code StreamDecoderInitStatus.OK} if initialization was successful;
     *    see StreamDecoderInitStatus for the meanings of other return values.
     */
    public StreamDecoderInitStatus initStream(ReadCallback readCallback, SeekCallback seekCallback,
            TellCallback tellCallback, LengthCallback lengthCallback, EofCallback eofCallback,
            WriteCallback writeCallback, MetadataCallback metadataCallback, ErrorCallback errorCallback)
            throws IOException {
        return initStreamInternal(readCallback, seekCallback, tellCallback, lengthCallback, eofCallback, writeCallback,
                metadataCallback, errorCallback, false);
    }

    /**
     * Initialize the decoder instance to decode Ogg FLAC streams.
     *
     *  This flavor of initialization sets up the decoder to decode from a
     *  FLAC stream in an Ogg container. I/O is performed via callbacks to the
     *  client.  For decoding from a plain file via filename or open RandomAccessFile,
     *  StreamDecoder.initOggFile() provides a simpler interface.
     *
     *  This function should be called after new StreamDecoder() and
     *  StreamDecoder.set*() but before any of the
     *  StreamDecoder.process*() functions.  Will set and return the
     *  decoder state, which will be {@code StreamDecoderState.SEARCH_FOR_METADATA}
     *  if initialization succeeded.
     *
     * @param readCallback See StreamDecoder.ReadCallback.  This
     *                            callback must not be {@code null}.
     * @param seekCallback See StreamDecoder.SeekCallback.  This
     *                            callback may be {@code null} if seeking is not
     *                            supported.  If {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback}, {@code lengthCallback}, and {@code eofCallback} must also be
     * supplied.
     *                            Alternatively, a dummy seek callback that just
     *                            returns {@code StreamDecoderSeekStatus.UNSUPPORTED}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param tellCallback See StreamDecoder.TellCallback.  This
     *                            callback may be {@code null} if not supported by the client.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code tellCallback} must also be supplied.
     *                            Alternatively, a dummy tell callback that just
     *                            returns {@code -1}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param lengthCallback See StreamDecoder.LengthCallback.  This
     *                            callback may be {@code null} if not supported by the client.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code lengthCallback} must also be supplied.
     *                            Alternatively, a dummy length callback that just
     *                            returns {@code -1}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param eofCallback See StreamDecoder.EofCallback.  This
     *                            callback may be {@code null} if not supported by the client.  If
     *                            {@code seekCallback} is not {@code null} then a
     *                            {@code eofCallback} must also be supplied.
     *                            Alternatively, a dummy length callback that just
     *                            returns {@code false}
     *                            may also be supplied, all though this is slightly
     *                            less efficient for the decoder.
     * @param writeCallback See StreamDecoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param metadataCallback See StreamDecoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @param errorCallback See StreamDecoder.ErrorCallback.  This
     *                            callback must not be {@code null}.
     * @return {@code StreamDecoderInitStatus.OK} if initialization was successful;
     *    see StreamDecoderInitStatus for the meanings of other return values.
     */
    public StreamDecoderInitStatus initOggStream(ReadCallback readCallback, SeekCallback seekCallback,
            TellCallback tellCallback, LengthCallback lengthCallback, EofCallback eofCallback,
            WriteCallback writeCallback, MetadataCallback metadataCallback, ErrorCallback errorCallback)
            throws IOException {
        return initStreamInternal(readCallback, seekCallback, tellCallback, lengthCallback, eofCallback, writeCallback,
                metadataCallback, errorCallback, true);
    }

    /**
     * Initialize the decoder instance to decode native FLAC files.
     *
     *  This flavor of initialization sets up the decoder to decode from a
     *  plain native FLAC file.  For other streams, you must use
     *  StreamDecoder.initStream() and provide callbacks for the I/O.
     *
     *  This function should be called after new StreamDecoder() and
     *  StreamDecoder.set*() but before any of the
     *  StreamDecoder.process*() functions.  Will set and return the
     *  decoder state, which will be {@code StreamDecoderState.SEARCH_FOR_METADATA}
     *  if initialization succeeded.
     *
     * @param file An open FLAC file.  The file should have been
     *                            opened with mode {@code "r"} and rewound.  The file
     *                            becomes owned by the decoder and should not be
     *                            manipulated by the client while decoding.
     *                            It will be closed
     *                            when StreamDecoder.finish() is called.
     * @param writeCallback See StreamDecoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param metadataCallback See StreamDecoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @param errorCallback See StreamDecoder.ErrorCallback.  This
     *                            callback must not be {@code null}.
     * @return {@code StreamDecoderInitStatus.OK} if initialization was successful;
     *    see StreamDecoderInitStatus for the meanings of other return values.
     */
    public StreamDecoderInitStatus initFile(RandomAccessFile file, WriteCallback writeCallback,
            MetadataCallback metadataCallback, ErrorCallback errorCallback) throws IOException {
        return initFileInternal(file, writeCallback, metadataCallback, errorCallback, false);
    }

    /**
     * Initialize the decoder instance to decode Ogg FLAC files.
     *
     *  This flavor of initialization sets up the decoder to decode from a
     *  plain Ogg FLAC file.  For other streams, you must use
     *  StreamDecoder.initOggStream() and provide callbacks for the I/O.
     *
     *  This function should be called after new StreamDecoder() and
     *  StreamDecoder.set*() but before any of the
     *  StreamDecoder.process*() functions.  Will set and return the
     *  decoder state, which will be {@code StreamDecoderState.SEARCH_FOR_METADATA}
     *  if initialization succeeded.
     *
     * @param file An open FLAC file.  The file should have been
     *                            opened with mode {@code "r"} and rewound.  The file
     *                            becomes owned by the decoder and should not be
     *                            manipulated by the client while decoding.
     *                            It will be closed
     *                            when StreamDecoder.finish() is called.
     * @param writeCallback See StreamDecoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param metadataCallback See StreamDecoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @param errorCallback See StreamDecoder.ErrorCallback.  This
     *                            callback must not be {@code null}.
     * @return {@code StreamDecoderInitStatus.OK} if initialization was successful;
     *    see StreamDecoderInitStatus for the meanings of other return values.
     */
    public StreamDecoderInitStatus initOggFile(RandomAccessFile file, WriteCallback writeCallback,
            MetadataCallback metadataCallback, ErrorCallback errorCallback) throws IOException {
        return initFileInternal(file, writeCallback, metadataCallback, errorCallback, true);
    }

    /**
     * Initialize the decoder instance to decode native FLAC files.
     *
     *  This flavor of initialization sets up the decoder to decode from a plain
     *  native FLAC file.
     *
     *  This function should be called after new StreamDecoder() and
     *  StreamDecoder.set*() but before any of the
     *  StreamDecoder.process*() functions.  Will set and return the
     *  decoder state, which will be {@code StreamDecoderState.SEARCH_FOR_METADATA}
     *  if initialization succeeded.
     *
     * @param filename The name of the file to decode from.  The file will
     *                            be opened with RandomAccessFile.  Use {@code null} to decode from
     *                            {@code stdin}.  Note that {@code stdin} is not seekable.
     * @param writeCallback See StreamDecoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param metadataCallback See StreamDecoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @param errorCallback See StreamDecoder.ErrorCallback.  This
     *                            callback must not be {@code null}.
     * @return {@code StreamDecoderInitStatus.OK} if initialization was successful;
     *    see StreamDecoderInitStatus for the meanings of other return values.
     */
    public StreamDecoderInitStatus initFile(String filename, WriteCallback writeCallback,
            MetadataCallback metadataCallback, ErrorCallback errorCallback) throws IOException {
        return initFileInternal(filename, writeCallback, metadataCallback, errorCallback, false);
    }

    /**
     * Initialize the decoder instance to decode Ogg FLAC files.
     *
     *  This flavor of initialization sets up the decoder to decode from a plain
     *  Ogg FLAC file.
     *
     *  This function should be called after new StreamDecoder() and
     *  StreamDecoder.set*() but before any of the
     *  StreamDecoder.process*() functions.  Will set and return the
     *  decoder state, which will be {@code StreamDecoderState.SEARCH_FOR_METADATA}
     *  if initialization succeeded.
     *
     * @param filename The name of the file to decode from.  The file will
     *                            be opened with RandomAccessFile.  Use {@code null} to decode from
     *                            {@code stdin}.  Note that {@code stdin} is not seekable.
     * @param writeCallback See StreamDecoder.WriteCallback.  This
     *                            callback must not be {@code null}.
     * @param metadataCallback See StreamDecoder.MetadataCallback.  This
     *                            callback may be {@code null} if the callback is not
     *                            desired.
     * @param errorCallback See StreamDecoder.ErrorCallback.  This
     *                            callback must not be {@code null}.
     * @return {@code StreamDecoderInitStatus.OK} if initialization was successful;
     *    see StreamDecoderInitStatus for the meanings of other return values.
     */
    public StreamDecoderInitStatus initOggFile(String filename, WriteCallback writeCallback,
            MetadataCallback metadataCallback, ErrorCallback errorCallback) throws IOException {
        return initFileInternal(filename, writeCallback, metadataCallback, errorCallback, true);
    }

    /**
     * Finish the decoding process.
     *  Flushes the decoding buffer, releases resources, resets the decoder
     *  settings to their defaults, and returns the decoder state to
     *  StreamDecoderState.UNINITIALIZED.
     *
     *  In the event of a prematurely-terminated decode, it is not strictly
     *  necessary to call this immediately before StreamDecoder.close()
     *  but it is good practice to match every StreamDecoder.init*()
     *  with a StreamDecoder.finish().
     *
     * @return {@code false} if MD5 checking is on AND a STREAMINFO block was available
     *    AND the MD5 signature in the STREAMINFO block was non-zero AND the
     *    signature does not match the one computed by the decoder; else
     *    {@code true}.
     */
    public boolean finish() throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED) {
            return true;
        }
        /* this is the sum we computed from the decoded data */
        byte[] computedMd5sum = md5.finish();
        try {
            if (file != null)
                file.close();
            return !doMd5Checking || Arrays.equals(computedMd5sum, streamInfo.md5sum);
        } finally {
            file = null;
            callbackInput = null;
            oggDecoderAspect = null;
            input = null;
            lastFrameHeader = null;
            readCallback = null;
            seekCallback = null;
            tellCallback = null;
            lengthCallback = null;
            eofCallback = null;
            writeCallback = null;
            metadataCallback = null;
            errorCallback = null;
            seekTable = null;
            isSeeking = false;
            state = StreamDecoderState.UNINITIALIZED;
            setDefaults();
        }
    }

    /**
     * Flush the stream input.
     *  The decoder's input buffer will be cleared and the state set to
     *  {@code StreamDecoderState.SEARCH_FOR_FRAME_SYNC}.  This will also turn
     *  off MD5 checking.
     */
    public void flush() {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        flushInternal();
    }

    /**
     * Reset the decoding process.
     *  The decoder's input buffer will be cleared and the state set to
     *  {@code StreamDecoderState.SEARCH_FOR_METADATA}.  This is similar to
     *  StreamDecoder.finish() except that the settings are
     *  preserved; there is no need to call StreamDecoder.init*()
     *  before decoding again.  MD5 checking will be restored to its original
     *  setting.
     *
     *  If the decoder is seekable, or was initialized with
     *  StreamDecoder.init*File(),
     *  the decoder will also attempt to seek to the beginning of the file.
     *  If this rewind fails, this function will return {@code false}.  It follows
     *  that StreamDecoder.reset() cannot be used when decoding from
     *  {@code stdin}.
     *
     *  If the decoder was initialized with StreamEncoder.init*Stream()
     *  and is not seekable (i.e. no seek callback was provided or the seek
     *  callback returns {@code StreamDecoderSeekStatus.UNSUPPORTED}), it
     *  is the duty of the client to start feeding data from the beginning of
     *  the stream on the next StreamDecoder.process*() call.
     *
     * @return {@code true} if successful, else {@code false} if a seek error occurs
     *    (the state will be unchanged).
     */
    public boolean reset() throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        return resetInternal(false);
    }

    /**
     * Decode one metadata block or audio frame.
     *  This version instructs the decoder to decode a either a single metadata
     *  block or a single frame and stop, unless the callbacks return a fatal
     *  error or the read callback returns
     *  {@code -1}.
     *
     *  As the decoder needs more input it will call the read callback.
     *  Depending on what was decoded, the metadata or write callback will be
     *  called with the decoded metadata block or audio frame.
     *
     *  Unless there is a fatal read error or end of stream, this function
     *  will return once one whole frame is decoded.  In other words, if the
     *  stream is not synchronized or points to a corrupt frame header, the
     *  decoder will continue to try and resync until it gets to a valid
     *  frame, then decode one frame, then return.  If the decoder points to
     *  a frame whose frame CRC in the frame footer does not match the
     *  computed frame CRC, this function will issue a
     *  StreamDecoderErrorStatus.FRAME_CRC_MISMATCH error to the
     *  error callback, and return, having decoded one complete, although
     *  corrupt, frame.  (Such corrupted frames are sent as silence of the
     *  correct length to the write callback.)
     *
     * @return {@code false} if any fatal read or write error
     *    occurred (meaning decoding must stop), else {@code true}; for more
     *    information about the decoder, check the decoder state with
     *    StreamDecoder.getState().
     */
    public boolean processSingle() throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        try {
            while (true) {
                switch (state) {
                    case SEARCH_FOR_METADATA -> {
                        if (!findMetadata())
                            return false;
                    }
                    case READ_METADATA -> {
                        if (!readMetadata())
                            return false;
                        return true;
                    }
                    case SEARCH_FOR_FRAME_SYNC -> frameSync();
                    case READ_FRAME -> {
                        if (!readFrame(true))
                            return false;
                        if (gotAFrame)
                            return true;
                    }
                    case END_OF_STREAM, ABORTED -> {
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        } catch (InterruptedInput | OggDecoderAspect.Aborted e) {
            boolean searching = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.ABORTED;
            return searching;
        } catch (BitReader.EndOfInput e) {
            boolean complete = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.END_OF_STREAM;
            return complete;
        } catch (Exception e) {
            if (state != StreamDecoderState.OGG_ERROR && state != StreamDecoderState.SEEK_ERROR)
                state = StreamDecoderState.ABORTED;
            throw e;
        }
    }

    /**
     * Decode until the end of the metadata.
     *  This version instructs the decoder to decode from the current position
     *  and continue until all the metadata has been read, or until the
     *  callbacks return a fatal error or the read callback returns
     *  {@code -1}.
     *
     *  As the decoder needs more input it will call the read callback.
     *  As each metadata block is decoded, the metadata callback will be called
     *  with the decoded metadata.
     *
     * @return {@code false} if any fatal read or write error
     *    occurred (meaning decoding must stop), else {@code true}; for more
     *    information about the decoder, check the decoder state with
     *    StreamDecoder.getState().
     */
    public boolean processUntilEndOfMetadata() throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        try {
            while (true) {
                switch (state) {
                    case SEARCH_FOR_METADATA -> {
                        if (!findMetadata())
                            return false;
                    }
                    case READ_METADATA -> {
                        if (!readMetadata())
                            return false;
                    }
                    case SEARCH_FOR_FRAME_SYNC, READ_FRAME, END_OF_STREAM, ABORTED -> {
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        } catch (InterruptedInput | OggDecoderAspect.Aborted e) {
            boolean searching = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.ABORTED;
            return searching;
        } catch (BitReader.EndOfInput e) {
            boolean complete = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.END_OF_STREAM;
            return complete;
        } catch (Exception e) {
            if (state != StreamDecoderState.OGG_ERROR && state != StreamDecoderState.SEEK_ERROR)
                state = StreamDecoderState.ABORTED;
            throw e;
        }
    }

    /**
     * Decode until the end of the stream.
     *  This version instructs the decoder to decode from the current position
     *  and continue until the end of stream (the read callback returns
     *  {@code -1}), or until the
     *  callbacks return a fatal error.
     *
     *  As the decoder needs more input it will call the read callback.
     *  As each metadata block and frame is decoded, the metadata or write
     *  callback will be called with the decoded metadata or frame.
     *
     * @return {@code false} if any fatal read or write error
     *    occurred (meaning decoding must stop), else {@code true}; for more
     *    information about the decoder, check the decoder state with
     *    StreamDecoder.getState().
     */
    public boolean processUntilEndOfStream() throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        try {
            while (true) {
                switch (state) {
                    case SEARCH_FOR_METADATA -> {
                        if (!findMetadata())
                            return false;
                    }
                    case READ_METADATA -> {
                        if (!readMetadata())
                            return false;
                    }
                    case SEARCH_FOR_FRAME_SYNC -> frameSync();
                    case READ_FRAME -> {
                        if (!readFrame(true))
                            return false;
                    }
                    case END_OF_STREAM, ABORTED -> {
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        } catch (InterruptedInput | OggDecoderAspect.Aborted e) {
            boolean searching = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.ABORTED;
            return searching;
        } catch (BitReader.EndOfInput e) {
            boolean complete = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.END_OF_STREAM;
            return complete;
        } catch (Exception e) {
            if (state != StreamDecoderState.OGG_ERROR && state != StreamDecoderState.SEEK_ERROR)
                state = StreamDecoderState.ABORTED;
            throw e;
        }
    }

    /**
     * Skip one audio frame.
     *  This version instructs the decoder to 'skip' a single frame and stop,
     *  unless the callbacks return a fatal error or the read callback returns
     *  {@code -1}.
     *
     *  The decoding flow is the same as what occurs when
     *  StreamDecoder.processSingle() is called to process an audio
     *  frame, except that this function does not decode the parsed data into
     *  PCM or call the write callback.  The integrity of the frame is still
     *  checked the same way as in the other process functions.
     *
     *  This function will return once one whole frame is skipped, in the
     *  same way that StreamDecoder.processSingle() will return once
     *  one whole frame is decoded.
     *
     *  This function can be used in more quickly determining FLAC frame
     *  boundaries when decoding of the actual data is not needed, for
     *  example when an application is separating a FLAC stream into frames
     *  for editing or storing in a container.  To do this, the application
     *  can use StreamDecoder.skipSingleFrame() to quickly advance
     *  to the next frame, then use
     *  StreamDecoder.getDecodePosition() to find the new frame
     *  boundary.
     *
     *  This function should only be called when the stream has advanced
     *  past all the metadata, otherwise it will return {@code false}.
     *
     * @return {@code false} if any fatal read or write error
     *    occurred (meaning decoding must stop), or if the decoder
     *    is in the StreamDecoderState.SEARCH_FOR_METADATA or
     *    StreamDecoderState.READ_METADATA state, else {@code true}; for more
     *    information about the decoder, check the decoder state with
     *    StreamDecoder.getState().
     */
    public boolean skipSingleFrame() throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        try {
            while (true) {
                switch (state) {
                    case SEARCH_FOR_METADATA, READ_METADATA -> {
                        return false;
                    }
                    case SEARCH_FOR_FRAME_SYNC -> frameSync();
                    case READ_FRAME -> {
                        if (!readFrame(false))
                            return false;
                        if (gotAFrame)
                            return true;
                    }
                    case END_OF_STREAM, ABORTED -> {
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        } catch (InterruptedInput | OggDecoderAspect.Aborted e) {
            boolean searching = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.ABORTED;
            return searching;
        } catch (BitReader.EndOfInput e) {
            boolean complete = state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            state = StreamDecoderState.END_OF_STREAM;
            return complete;
        } catch (Exception e) {
            if (state != StreamDecoderState.OGG_ERROR && state != StreamDecoderState.SEEK_ERROR)
                state = StreamDecoderState.ABORTED;
            throw e;
        }
    }

    /**
     * Flush the input and seek to an absolute sample.
     *  Decoding will resume at the given sample.  Note that because of
     *  this, the next write callback may contain a partial block.  The
     *  client must support seeking the input or this function will fail
     *  and return {@code false}.  Furthermore, if the decoder state is
     *  {@code StreamDecoderState.SEEK_ERROR}, then the decoder must be flushed
     *  with StreamDecoder.flush() or reset with
     *  StreamDecoder.reset() before decoding can continue.
     *
     * @param sample The target sample number to seek to.
     * @return {@code true} if successful, else {@code false}.
     */
    public boolean seekAbsolute(long sample) throws IOException {
        if (state == StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder uninitialized");
        if (state != StreamDecoderState.SEARCH_FOR_METADATA && state != StreamDecoderState.READ_METADATA
                && state != StreamDecoderState.SEARCH_FOR_FRAME_SYNC && state != StreamDecoderState.READ_FRAME
                && state != StreamDecoderState.END_OF_STREAM)
            return false;
        if (seekCallback == null)
            return false;
        if (getTotalSamples() > 0 && Long.compareUnsigned(sample, getTotalSamples()) >= 0)
            return false;
        isSeeking = true;
        /* turn off md5 checking if a seek is attempted */
        doMd5Checking = false;
        try {
            /* get the file length (currently our algorithm needs to know the length so it's also an error to get -1) */
            long streamLength = lengthCallback.length(this);
            if (streamLength < 0)
                return false;
            /* if we haven't finished processing the metadata yet, do that so we have the STREAMINFO, SEEK_TABLE, and firstFrameOffset */
            if (state == StreamDecoderState.SEARCH_FOR_METADATA || state == StreamDecoderState.READ_METADATA) {
                if (!processUntilEndOfMetadata())
                    /* above call sets the state for us */
                    return false;
                /* check this again in case we didn't know totalSamples the first time */
                if (getTotalSamples() > 0 && Long.compareUnsigned(sample, getTotalSamples()) >= 0)
                    return false;
            }
            return isOgg ? seekToAbsoluteSampleOgg(streamLength, sample) : seekToAbsoluteSample(streamLength, sample);
        } catch (Exception e) {
            state = StreamDecoderState.SEEK_ERROR;
            throw e;
        } finally {
            isSeeking = false;
        }
    }

    /** Close a decoder instance. */
    @Override
    public void close() throws IOException {
        finish();
    }

    private StreamDecoderInitStatus initStreamInternal(ReadCallback readCallback, SeekCallback seekCallback,
            TellCallback tellCallback, LengthCallback lengthCallback, EofCallback eofCallback,
            WriteCallback writeCallback, MetadataCallback metadataCallback, ErrorCallback errorCallback, boolean isOgg)
            throws IOException {
        if (state != StreamDecoderState.UNINITIALIZED)
            return StreamDecoderInitStatus.ALREADY_INITIALIZED;
        if (readCallback == null || writeCallback == null || errorCallback == null
                || (seekCallback != null && (tellCallback == null || lengthCallback == null || eofCallback == null)))
            return StreamDecoderInitStatus.INVALID_CALLBACKS;
        /* from here on, errors are fatal */
        this.isOgg = isOgg;
        this.readCallback = readCallback;
        this.seekCallback = seekCallback;
        this.tellCallback = tellCallback;
        this.lengthCallback = lengthCallback;
        this.eofCallback = eofCallback;
        this.writeCallback = writeCallback;
        this.metadataCallback = metadataCallback;
        this.errorCallback = errorCallback;
        fixedBlockSize = 0;
        samplesDecoded = 0;
        hasStreamInfo = false;
        cachedByte = -1;
        doMd5Checking = md5Checking;
        isSeeking = false;
        resetInput();
        /* so the following reset does not try to rewind the input */
        resetInternal(true);
        return StreamDecoderInitStatus.OK;
    }

    private void resetInput() {
        callbackInput = new CallbackInput();
        oggDecoderAspect = isOgg ? new OggDecoderAspect(callbackInput, oggSerialNumber) : null;
        input = new BitReader(new DecoderInput(isOgg ? oggDecoderAspect : callbackInput));
        lastSeenFrameSync = 0;
    }

    private StreamDecoderInitStatus initFileInternal(RandomAccessFile file, WriteCallback writeCallback,
            MetadataCallback metadataCallback, ErrorCallback errorCallback, boolean isOgg) throws IOException {
        Objects.requireNonNull(file);
        if (state != StreamDecoderState.UNINITIALIZED)
            return StreamDecoderInitStatus.ALREADY_INITIALIZED;
        if (writeCallback == null || errorCallback == null)
            return StreamDecoderInitStatus.INVALID_CALLBACKS;
        /*
         * To make sure that our file does not go unclosed after an error, we
         * must assign the file reference before any further error can occur in
         * this routine.
         */
        this.file = file;
        return initStreamInternal(this::fileReadCallback, this::fileSeekCallback, this::fileTellCallback,
                this::fileLengthCallback, this::fileEofCallback, writeCallback, metadataCallback, errorCallback, isOgg);
    }

    private StreamDecoderInitStatus initFileInternal(String filename, WriteCallback writeCallback,
            MetadataCallback metadataCallback, ErrorCallback errorCallback, boolean isOgg) throws IOException {
        /*
         * To make sure that our file does not go unclosed after an error, we
         * have to do the same entrance checks here that are later performed
         * in StreamDecoder.initFile() before the RandomAccessFile is assigned.
         */
        if (state != StreamDecoderState.UNINITIALIZED)
            return StreamDecoderInitStatus.ALREADY_INITIALIZED;
        if (writeCallback == null || errorCallback == null)
            return StreamDecoderInitStatus.INVALID_CALLBACKS;
        if (filename == null) {
            StreamDecoderInitStatus status = initStreamInternal((d, b, o, n) -> System.in.read(b, o, n), null, null,
                    null, null, writeCallback, metadataCallback, errorCallback, isOgg);
            standardInput = status == StreamDecoderInitStatus.OK;
            return status;
        }
        RandomAccessFile f = new RandomAccessFile(filename, "r");
        boolean initialized = false;
        try {
            StreamDecoderInitStatus status = initFileInternal(f, writeCallback, metadataCallback, errorCallback, isOgg);
            initialized = status == StreamDecoderInitStatus.OK;
            return status;
        } finally {
            if (!initialized)
                f.close();
        }
    }

    private void requireUninitialized() {
        if (state != StreamDecoderState.UNINITIALIZED)
            throw new IllegalStateException("decoder already initialized");
    }

    private void flushInternal() {
        samplesDecoded = 0;
        doMd5Checking = false;
        lastSeenFrameSync = 0;
        lastFrameHeader = null;
        if (oggDecoderAspect != null)
            oggDecoderAspect.flush();
        input.clear();
        state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
    }

    private boolean resetInternal(boolean internalReset) throws IOException {
        flushInternal();
        if (oggDecoderAspect != null)
            /* @@@ could go in !internalReset block below */
            oggDecoderAspect.resetAspect();
        /* internalReset is true only during init() so we can call reset to set up the decoder without rewinding the input */

        /*
         * Rewind if necessary.  If initStreamInternal() is calling us,
         * (internalReset) don't try to rewind since we are already at
         * the beginning of the stream and don't want to fail if the input is
         * not seekable.
         */
        if (!internalReset) {
            /* can't rewind stdin, reset fails */
            if (standardInput)
                return false;
            /* seekable and seek fails, reset fails */
            if (seekCallback != null && seekCallback.seek(this, 0) == StreamDecoderSeekStatus.ERROR)
                return false;
        }
        state = StreamDecoderState.SEARCH_FOR_METADATA;
        hasStreamInfo = false;
        seekTable = null;
        doMd5Checking = md5Checking;
        /*
         * This goes in reset() and not flush() because according to the spec, a
         * fixed-blocksize stream must stay that way through the whole stream.
         */
        fixedBlockSize = 0;
        md5.reset();
        firstFrameOffset = 0;
        unparseableFrameCount = 0;
        lastSeenFrameSync = 0;
        lastFrameHeader = null;
        return true;
    }

    /** Return the number of input bytes consumed */
    private int getInputBytesUnconsumed() {
        return input.getInputBitsUnconsumed() / Byte.SIZE;
    }

    private void setDefaults() {
        isOgg = false;
        md5Checking = false;
        standardInput = false;
        Arrays.fill(metadataFilter, false);
        metadataFilter[MetadataType.STREAMINFO.ordinal()] = true;
        metadataFilterIds.clear();
        oggSerialNumber = null;
    }

    private static int applicationId(byte[] id) {
        if (id.length != Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE)
            throw new IllegalArgumentException("four-byte id");
        return (id[0] << 24) | ((id[1] & 0xff) << 16) | ((id[2] & 0xff) << 8) | (id[3] & 0xff);
    }

    private boolean findMetadata() throws IOException {
        boolean first = true;
        for (int i = 0, id = 0; i < Format.STREAM_SYNC_STRING.length;) {
            int x = cachedByte >= 0 ? cachedByte : input.readRawUInt32(8);
            cachedByte = -1;
            if (x == Format.STREAM_SYNC_STRING[i]) {
                first = true;
                i++;
                id = 0;
                continue;
            }
            if (id >= ID3V2_TAG_.length)
                return false;
            if (x == ID3V2_TAG_[id]) {
                id++;
                i = 0;
                if (id == ID3V2_TAG_.length) {
                    skipId3v2Tag();
                }
                continue;
            }
            id = 0;
            /* MAGIC NUMBER for the first 8 frame sync bits */
            if (x == 0xff) {
                headerWarmup[0] = (byte) x;
                x = input.readRawUInt32(8);
                /* we have to check if we just read two 0xff's in a row; the second may actually be the beginning of the sync code */
                if (x == 0xff)
                    cachedByte = x;
                /* else we have to check if the second byte is the end of a sync code */

                /* MAGIC NUMBER for the last 6 sync bits and reserved 7th bit */
                else if ((x >>> 1) == 0x7c) {
                    headerWarmup[1] = (byte) x;
                    state = StreamDecoderState.READ_FRAME;
                    return true;
                }
            }
            i = 0;
            if (first) {
                sendErrorToClient(StreamDecoderErrorStatus.LOST_SYNC);
                first = false;
            }
        }
        state = StreamDecoderState.READ_METADATA;
        return true;
    }

    private boolean readMetadata() throws IOException {
        boolean last = input.readRawUInt32(Format.STREAM_METADATA_IS_LAST_LEN) != 0;
        int type = input.readRawUInt32(Format.STREAM_METADATA_TYPE_LEN),
                length = input.readRawUInt32(Format.STREAM_METADATA_LENGTH_LEN);
        boolean emit = metadataFilter[type];
        StreamMetadata block = null;
        if (type == MetadataType.STREAMINFO.ordinal()) {
            block = readMetadataStreamInfo(last, length);
            if (block == null)
                return false;
            hasStreamInfo = true;
            if (Arrays.equals(streamInfo.md5sum, new byte[Format.STREAM_METADATA_STREAMINFO_MD5SUM_LEN / Byte.SIZE]))
                doMd5Checking = false;
        } else if (type == MetadataType.SEEKTABLE.ordinal()) {
            SeekTable previous = seekTable;
            /* just in case we already have a seek table, and reading the next one fails: */
            seekTable = null;
            if (length > 0) {
                block = readMetadataSeekTable(previous, last, length);
                if (block == null)
                    return false;
                seekTable = (SeekTable) block;
            }
        } else {
            Application application = null;
            int realLength = length;
            if (type == MetadataType.APPLICATION.ordinal()) {
                application = new Application();
                application.id = input.readByteBlockAlignedNoCrc(Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE);
                /* underflow check */
                if (realLength < Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE) {
                    /* @@@@@@ maybe wrong error? need to resync? */
                    state = StreamDecoderState.MEMORY_ALLOCATION_ERROR;
                    return false;
                }
                realLength -= Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE;
                if (metadataFilterIds.contains(applicationId(application.id)))
                    emit = !emit;
            }
            if (!emit)
                input.skipByteBlockAlignedNoCrc(realLength);
            else {
                boolean ok = true;
                input.setLimit(realLength * 8L);
                Exception failure = null;
                try {
                    switch (type) {
                        case 1 -> {
                            block = new Padding(length);
                            /* skip the padding bytes */
                            input.skipByteBlockAlignedNoCrc(realLength);
                        }
                        case 2 -> {
                            /* remember, we read the ID already */
                            block = application;
                            application.data = input.readByteBlockAlignedNoCrc(realLength);
                        }
                        case 4 -> {
                            VorbisComment comment = new VorbisComment();
                            block = comment;
                            ok = readMetadataVorbisComment(comment, realLength);
                        }
                        case 5 -> {
                            CueSheet cueSheet = new CueSheet();
                            block = cueSheet;
                            ok = readMetadataCueSheet(cueSheet);
                        }
                        case 6 -> {
                            Picture picture = new Picture();
                            block = picture;
                            ok = readMetadataPicture(picture);
                        }
                        default -> {
                            UnknownMetadata unknown = new UnknownMetadata(type, true);
                            block = unknown;
                            unknown.data = input.readByteBlockAlignedNoCrc(realLength);
                        }
                    }
                } catch (BitReader.LimitExceeded e) {
                    ok = false;
                } catch (BitReader.EndOfInput | InterruptedInput | OggDecoderAspect.Aborted e) {
                    metadataReadFailure(e);
                    ok = false;
                } catch (Exception e) {
                    failure = e;
                    throw e;
                } finally {
                    try {
                        if (input.limitRemaining() > 0) {
                            /*
                             * Content in metadata block didn't fit in block length
                             * We cannot know whether the length or the content was
                             * corrupt, so stop parsing metadata
                             */
                            sendErrorToClient(StreamDecoderErrorStatus.BAD_METADATA);
                            if (state == StreamDecoderState.READ_METADATA) {
                                state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
                            }
                            ok = false;
                        }
                    } catch (RuntimeException e) {
                        if (failure == null)
                            throw e;
                        if (failure != e)
                            failure.addSuppressed(e);
                    } finally {
                        input.removeLimit();
                    }
                }
                /* anything that unsets "ok" should also make sure state is updated */
                if (!ok)
                    return false;
            }
        }
        if (block != null) {
            block.isLast = last;
            if (emit && metadataCallback != null && !isSeeking)
                metadataCallback.metadata(this, block);
        }
        if (last) {
            /* if this fails, it's OK, it's just a hint for the seek routine */
            firstFrameOffset = getDecodePosition();
            if (firstFrameOffset < 0)
                firstFrameOffset = 0;
            state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
        }
        return true;
    }

    private StreamInfo readMetadataStreamInfo(boolean isLast, int length) throws IOException {
        StreamInfo info = streamInfo;
        info.isLast = isLast;
        info.minBlocksize = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_MIN_BLOCK_SIZE_LEN);
        info.maxBlocksize = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_MAX_BLOCK_SIZE_LEN);
        info.minFramesize = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_MIN_FRAME_SIZE_LEN);
        info.maxFramesize = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_MAX_FRAME_SIZE_LEN);
        info.sampleRate = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_SAMPLE_RATE_LEN);
        info.channels = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_CHANNELS_LEN) + 1;
        info.bitsPerSample = input.readRawUInt32(Format.STREAM_METADATA_STREAMINFO_BITS_PER_SAMPLE_LEN) + 1;
        info.totalSamples = input.readRawUInt64(Format.STREAM_METADATA_STREAMINFO_TOTAL_SAMPLES_LEN);
        info.md5sum = input.readByteBlockAlignedNoCrc(Format.STREAM_METADATA_STREAMINFO_MD5SUM_LEN / Byte.SIZE);
        if (length < Format.STREAM_METADATA_STREAMINFO_LENGTH)
            return null;
        /* skip the rest of the block */
        input.skipByteBlockAlignedNoCrc(length - Format.STREAM_METADATA_STREAMINFO_LENGTH);
        return info;
    }

    private SeekTable readMetadataSeekTable(SeekTable previous, boolean isLast, int length) throws IOException {
        if (length % Format.STREAM_METADATA_SEEKPOINT_LENGTH != 0) {
            input.invalidateLimit();
            return null;
        }
        SeekTable table = previous == null ? new SeekTable() : previous;
        table.isLast = isLast;
        table.resizePoints(length / Format.STREAM_METADATA_SEEKPOINT_LENGTH);
        for (SeekPoint point : table.points) {
            point.sampleNumber = input.readRawUInt64(Format.STREAM_METADATA_SEEKPOINT_SAMPLE_NUMBER_LEN);
            point.streamOffset = input.readRawUInt64(Format.STREAM_METADATA_SEEKPOINT_STREAM_OFFSET_LEN);
            point.frameSamples = input.readRawUInt32(Format.STREAM_METADATA_SEEKPOINT_FRAME_SAMPLES_LEN);
        }
        return table;
    }

    private boolean readMetadataVorbisComment(VorbisComment comment, int length) throws IOException {
        comment.vendorString = "";
        skip: {
            /* read vendor string */
            if (length >= 8) {
                length -= 8; /* vendor string length + num comments entries alone take 8 bytes */
                int vendorLength = input.readUInt32LittleEndian();
                if (Integer.compareUnsigned(vendorLength, length) > 0)
                    break skip;
                length -= vendorLength;
                comment.vendorString = new String(input.readByteBlockAlignedNoCrc(vendorLength),
                        StandardCharsets.UTF_8);
                /* read num comments */
                int numComments = input.readUInt32LittleEndian();
                /* read comments */
                if (Integer.compareUnsigned(numComments, 100000) > 0)
                    /* Possibly malicious file. */
                    return false;
                comment.comments = new String[numComments];
                for (int i = 0; i < numComments; i++) {
                    if (length < 4) {
                        comment.comments = Arrays.copyOf(comment.comments, i);
                        break skip;
                    }
                    length -= 4;
                    int entryLength = input.readUInt32LittleEndian();
                    if (Integer.compareUnsigned(entryLength, length) > 0) {
                        comment.comments = Arrays.copyOf(comment.comments, i);
                        input.invalidateLimit();
                        return false;
                    }
                    length -= entryLength;
                    try {
                        comment.comments[i] = new String(input.readByteBlockAlignedNoCrc(entryLength),
                                StandardCharsets.UTF_8);
                    } catch (BitReader.EndOfInput | InterruptedInput | OggDecoderAspect.Aborted
                            | BitReader.LimitExceeded e) {
                        metadataReadFailure(e);
                        /* Current i-th entry is bad, so we delete it. */
                        comment.comments = Arrays.copyOf(comment.comments, i);
                        break skip;
                    }
                }
            } else {
                input.invalidateLimit();
                return false;
            }
        }
        if (length > 0) {
            /* length > 0 can only happen on files with invalid data in comments */
            input.invalidateLimit();
            return false;
        }
        return true;
    }

    private String readMetadataFixedText(int length) throws IOException {
        byte[] data = input.readByteBlockAlignedNoCrc(length);
        int end = length;
        while (end > 0 && data[end - 1] == 0)
            end--;
        return new String(data, 0, end, StandardCharsets.UTF_8);
    }

    private boolean readMetadataCueSheet(CueSheet cueSheet) throws IOException {
        cueSheet.mediaCatalogNumber = readMetadataFixedText(
                Format.STREAM_METADATA_CUESHEET_MEDIA_CATALOG_NUMBER_LEN / Byte.SIZE);
        cueSheet.leadIn = input.readRawUInt64(Format.STREAM_METADATA_CUESHEET_LEAD_IN_LEN);
        cueSheet.isCd = input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_IS_CD_LEN) != 0;
        input.skipBitsNoCrc(Format.STREAM_METADATA_CUESHEET_RESERVED_LEN);
        int count = input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_NUM_TRACKS_LEN);
        if (count == 0) {
            input.invalidateLimit();
            return false;
        }
        cueSheet.resizeTracks(count);
        for (CueSheetTrack track : cueSheet.tracks) {
            track.offset = input.readRawUInt64(Format.STREAM_METADATA_CUESHEET_TRACK_OFFSET_LEN);
            track.number = input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_TRACK_NUMBER_LEN);
            track.isrc = readMetadataFixedText(Format.STREAM_METADATA_CUESHEET_TRACK_ISRC_LEN / Byte.SIZE);
            track.type = input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_TRACK_TYPE_LEN);
            track.preEmphasis = input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_TRACK_PRE_EMPHASIS_LEN) != 0;
            input.skipBitsNoCrc(Format.STREAM_METADATA_CUESHEET_TRACK_RESERVED_LEN);
            track.resizeIndices(input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_TRACK_NUM_INDICES_LEN));
            for (CueSheetIndex index : track.indices) {
                index.offset = input.readRawUInt64(Format.STREAM_METADATA_CUESHEET_INDEX_OFFSET_LEN);
                index.number = input.readRawUInt32(Format.STREAM_METADATA_CUESHEET_INDEX_NUMBER_LEN);
                input.skipBitsNoCrc(Format.STREAM_METADATA_CUESHEET_INDEX_RESERVED_LEN);
            }
        }
        return true;
    }

    private boolean readMetadataPicture(Picture picture) throws IOException {
        /* read type */
        int type = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_TYPE_LEN);
        picture.pictureType = Integer.compareUnsigned(type, PictureType.UNDEFINED.ordinal()) < 0 ? type
                : PictureType.OTHER.ordinal();
        /* read MIME type */
        int length = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_MIME_TYPE_LENGTH_LEN);

        if (input.limitRemaining() < Integer.toUnsignedLong(length)) {
            input.invalidateLimit();
            return false;
        }
        picture.mimeType = new String(input.readByteBlockAlignedNoCrc(length), StandardCharsets.UTF_8);
        /* read description */
        length = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_DESCRIPTION_LENGTH_LEN);
        if (input.limitRemaining() < Integer.toUnsignedLong(length)) {
            input.invalidateLimit();
            return false;
        }
        picture.description = new String(input.readByteBlockAlignedNoCrc(length), StandardCharsets.UTF_8);
        /* read width */
        picture.width = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_WIDTH_LEN);
        /* read height */
        picture.height = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_HEIGHT_LEN);
        /* read depth */
        picture.depth = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_DEPTH_LEN);
        /* read colors */
        picture.colors = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_COLORS_LEN);
        /* read data */
        length = input.readRawUInt32(Format.STREAM_METADATA_PICTURE_DATA_LENGTH_LEN);
        if (input.limitRemaining() < Integer.toUnsignedLong(length)) {
            input.invalidateLimit();
            return false;
        }
        picture.data = input.readByteBlockAlignedNoCrc(length);
        return true;
    }

    private void skipId3v2Tag() throws IOException {
        /* skip the version and flags bytes */
        input.readRawUInt32(24);
        int skip = 0;
        /* get the size (in bytes) to skip */
        for (int i = 0; i < 4; i++)
            skip = (skip << 7) | (input.readRawUInt32(8) & 0x7f);
        /* skip the rest of the tag */
        input.skipByteBlockAlignedNoCrc(skip);
    }

    private void frameSync() throws IOException {
        state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
        /* make sure we're byte aligned */
        if (!input.isConsumedByteAligned())
            input.readRawUInt32(input.getBitsLeftForByteAlignment());
        boolean first = true;
        while (true) {
            int x = cachedByte >= 0 ? cachedByte : input.readRawUInt32(8);
            cachedByte = -1;
            if (x == 0xff) { /* MAGIC NUMBER for the first 8 frame sync bits */
                headerWarmup[0] = (byte) x;
                x = input.readRawUInt32(8);
                /* we have to check if we just read two 0xff's in a row; the second may actually be the beginning of the sync code */
                /* else we have to check if the second byte is the end of a sync code */
                if (x == 0xff) /* MAGIC NUMBER for the first 8 frame sync bits */
                    cachedByte = x;
                else if ((x >>> 1) == 0x7c) { /* MAGIC NUMBER for the last 6 sync bits and reserved 7th bit */
                    headerWarmup[1] = (byte) x;
                    state = StreamDecoderState.READ_FRAME;
                    /*
                     * Save location so we can rewind in case the frame turns
                     * out to be invalid after the header
                     */
                    input.setFrameSyncLocation();
                    long position = getDecodePosition();
                    lastSeenFrameSync = position < 0 ? 0 : position;
                    return;
                }
            }
            if (first) {
                sendErrorToClient(StreamDecoderErrorStatus.LOST_SYNC);
                first = false;
            }
        }
    }

    private void invalid(StreamDecoderErrorStatus status) throws IOException {
        sendErrorToClient(status);
        state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
        throw new InvalidFrame();
    }

    private boolean recoverFrame() throws IOException {
        /* Got corruption, rewind if possible. Return value of seek
         * isn't checked, if the seek fails the decoder will continue anyway */
        if (!input.rewindToAfterLastSeenFrameSync() && seekCallback != null && lastSeenFrameSync != 0) {
            /* Last framesync isn't in bitreader anymore, rewind with seek if possible */
            try {
                if (seekCallback.seek(this, lastSeenFrameSync) == StreamDecoderSeekStatus.ERROR)
                    return seekError();
                input.clear();
            } catch (Exception e) {
                state = StreamDecoderState.SEEK_ERROR;
                throw e;
            }
        }
        return true;
    }

    private boolean readFrame(boolean doFullDecode) throws IOException {
        gotAFrame = false;
        boolean frameHeaderRead = false;
        try {
            /* the one we calculate from the input stream */
            int frameCrc = 0;
            /* init the CRC */
            frameCrc = Crc.crc16Update(headerWarmup[0], frameCrc);
            frameCrc = Crc.crc16Update(headerWarmup[1], frameCrc);
            input.resetReadCrc16(frameCrc);
            state = StreamDecoderState.READ_FRAME;
            Frame frame = new Frame();
            FrameHeader header = frame.header;
            int nextFixedBlockSize = readFrameHeader(header);
            frameHeaderRead = true;
            long[][] samples = new long[header.channels][];
            frame.subframes = new Subframe[header.channels];
            try {
                for (int channel = 0; channel < header.channels; channel++) {
                    /* first figure the correct bits-per-sample of the subframe */
                    int bps = header.bitsPerSample;
                    switch (header.channelAssignment) {
                        case INDEPENDENT -> {
                            /* no adjustment needed */
                        }
                        case LEFT_SIDE -> {
                            if (channel == 1)
                                bps++;
                        }
                        case RIGHT_SIDE -> {
                            if (channel == 0)
                                bps++;
                        }
                        case MID_SIDE -> {
                            if (channel == 1)
                                bps++;
                        }
                    }
                    /* now read it */
                    DecodedSubframe decoded = readSubframe(header.blocksize, bps, doFullDecode);
                    frame.subframes[channel] = decoded.subframe;
                    samples[channel] = decoded.output;
                }
            } catch (BitReader.EndOfInput e) {
                state = StreamDecoderState.END_OF_STREAM;
                if (!recoverFrame())
                    return false;
                state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
                return true;
            }
            readZeroPadding();
            frameCrc = input.getReadCrc16();
            int crc;
            try {
                /* Read the frame CRC-16 from the footer and check */
                crc = input.readRawUInt32(Format.FRAME_FOOTER_CRC_LEN);
            } catch (BitReader.EndOfInput e) {
                state = StreamDecoderState.END_OF_STREAM;
                if (!recoverFrame())
                    return false;
                state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
                return true;
            }
            if (frameCrc != crc)
                invalid(StreamDecoderErrorStatus.FRAME_CRC_MISMATCH);
            int[][] pcm = null;
            if (doFullDecode) {
                /* Undo any special channel coding */
                undoChannelCoding(header, samples);
                pcm = new int[header.channels][header.blocksize];
                for (int channel = 0; channel < header.channels; channel++)
                    for (int i = 0; i < header.blocksize; i++)
                        pcm[channel][i] = (int) samples[channel][i];
                /* Check whether decoded data actually fits bps */
                boolean invalidPcm = false;
                for (int channel = 0; channel < header.channels; channel++)
                    for (int i = 0; i < header.blocksize; i++) {
                        int shiftBits = Integer.SIZE - header.bitsPerSample;
                        /* Check whether shiftBits MSBs are 'empty' by shifting up and down */
                        if (pcm[channel][i] < (Integer.MIN_VALUE >> shiftBits)
                                || pcm[channel][i] > (Integer.MAX_VALUE >> shiftBits)) {
                            /* Bad frame, emit error */
                            sendErrorToClient(StreamDecoderErrorStatus.FRAME_CRC_MISMATCH);
                            state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
                            invalidPcm = true;
                            break;
                        }
                    }
                if (invalidPcm)
                    throw new InvalidFrame();
            }
            /* Check whether frames are missing, if so, add silence to compensate */
            if (doFullDecode && !isSeeking && lastFrameHeader != null
                    && header.number > lastFrameHeader.number + lastFrameHeader.blocksize
                    /*
                     * Do some extra validation to assure last frame an current frame
                     * header are both valid before adding silence inbetween
                     * Technically both frames could be valid with differing sample_rates,
                     * channels and bitsPerSample, but it is quite rare
                     */
                    && header.channels == lastFrameHeader.channels && header.sampleRate == lastFrameHeader.sampleRate
                    && header.bitsPerSample == lastFrameHeader.bitsPerSample && lastFrameHeader.blocksize >= 16) {
                /*
                 * No repairs larger than 5 seconds or 50 frames are made, to not
                 * unexpectedly create enormous files when one of the headers was
                 * corrupt after all
                 */
                long paddingSamplesNeeded = Math.min(
                        Integer.toUnsignedLong(
                                (int) (header.number - lastFrameHeader.number - lastFrameHeader.blocksize)),
                        Math.min(5L * header.sampleRate, 50L * lastFrameHeader.blocksize));
                long silenceNumber = lastFrameHeader.number + lastFrameHeader.blocksize;
                while (paddingSamplesNeeded > 0) {
                    Frame emptyFrame = new Frame();
                    FrameHeader emptyHeader = emptyFrame.header = copyHeader(lastFrameHeader);
                    emptyHeader.blocksize = (int) Math.min(lastFrameHeader.blocksize, paddingSamplesNeeded);
                    emptyHeader.number = silenceNumber;
                    emptyFrame.subframes = new Subframe[header.channels];
                    for (int channel = 0; channel < header.channels; channel++) {
                        emptyFrame.subframes[channel] = new Subframe();
                        emptyFrame.subframes[channel].type = SubframeType.CONSTANT;
                    }
                    int[][] emptyBuffer = new int[header.channels][emptyHeader.blocksize];
                    frameHeader = emptyHeader;
                    samplesDecoded = silenceNumber + emptyHeader.blocksize;
                    if (!writeAudioFrameToClient(emptyFrame, emptyBuffer)) {
                        state = StreamDecoderState.ABORTED;
                        return false;
                    }
                    silenceNumber += emptyHeader.blocksize;
                    paddingSamplesNeeded -= emptyHeader.blocksize;
                }
            }
            gotAFrame = true;
            /* we wait to update fixedBlockSize until here, when we're sure we've got a proper frame and hence a correct blocksize */
            if (nextFixedBlockSize != 0)
                fixedBlockSize = nextFixedBlockSize;
            /* put the latest values into the decoder instance */
            frameHeader = header;
            samplesDecoded = header.number + header.blocksize;
            /* write it */
            if (doFullDecode && !writeAudioFrameToClient(frame, pcm)) {
                state = StreamDecoderState.ABORTED;
                return false;
            }
            state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            return true;
        } catch (InvalidFrame e) {
            if (frameHeaderRead) {
                if (!input.isConsumedByteAligned() && input.readRawUInt32(input.getBitsLeftForByteAlignment()) != 0)
                    sendErrorToClient(StreamDecoderErrorStatus.LOST_SYNC);
                if (!recoverFrame())
                    return false;
            }
            state = StreamDecoderState.SEARCH_FOR_FRAME_SYNC;
            return true;
        }
    }

    private int readFrameHeader(FrameHeader frameHeader) throws IOException {
        /* MAGIC NUMBER based on the maximum frame header size, including CRC */
        byte[] rawHeader = new byte[16];
        int[] rawHeaderLen = {0};
        /* init the raw header with the saved bits from synchronization */
        rawHeader[rawHeaderLen[0]++] = headerWarmup[0];
        rawHeader[rawHeaderLen[0]++] = headerWarmup[1];
        /* check to make sure that reserved bit is 0 */

        /* MAGIC NUMBER */
        boolean isUnparseable = (headerWarmup[1] & 0x02) != 0;
        /*
         * Note that along the way as we read the header, we look for a sync
         * code inside.  If we find one it would indicate that our original
         * sync was bad since there cannot be a sync code in a valid header.
         *
         * Three kinds of things can go wrong when reading the frame header:
         *  1) We may have sync'ed incorrectly and not landed on a frame header.
         *     If we don't find a sync code, it can end up looking like we read
         *     a valid but unparseable header, until getting to the frame header
         *     CRC.  Even then we could get a false positive on the CRC.
         *  2) We may have sync'ed correctly but on an unparseable frame (from a
         *     future encoder).
         *  3) We may be on a damaged frame which appears valid but unparseable.
         *
         * For all these reasons, we try and read a complete frame header as
         * long as it seems valid, even if unparseable, up until the frame
         * header CRC.
         */

        /* read in the raw header as bytes so we can CRC it, and parse it on the way */
        for (int i = 0; i < 2; i++) {
            int x = input.readRawUInt32(8);
            /* MAGIC NUMBER for the first 8 frame sync bits */
            if (x == 0xff) {
                /* if we get here it means our original sync was erroneous since the sync code cannot appear in the header */
                cachedByte = x;
                invalid(StreamDecoderErrorStatus.BAD_HEADER);
            }
            rawHeader[rawHeaderLen[0]++] = (byte) x;
        }

        int blocksizeHint = 0, sampleRateHint = 0;
        int x = (rawHeader[2] & 255) >>> 4;
        switch (x) {
            case 0 -> isUnparseable = true;
            case 1 -> frameHeader.blocksize = 192;
            case 2, 3, 4, 5 -> frameHeader.blocksize = 576 << (x - 2);
            case 6, 7 -> blocksizeHint = x;
            default -> frameHeader.blocksize = 256 << (x - 8);
        }
        x = rawHeader[2] & 0x0f;
        switch (x) {
            case 0 -> {
                if (hasStreamInfo)
                    frameHeader.sampleRate = streamInfo.sampleRate;
                else
                    isUnparseable = true;
            }
            case 1 -> frameHeader.sampleRate = 88200;
            case 2 -> frameHeader.sampleRate = 176400;
            case 3 -> frameHeader.sampleRate = 192000;
            case 4 -> frameHeader.sampleRate = 8000;
            case 5 -> frameHeader.sampleRate = 16000;
            case 6 -> frameHeader.sampleRate = 22050;
            case 7 -> frameHeader.sampleRate = 24000;
            case 8 -> frameHeader.sampleRate = 32000;
            case 9 -> frameHeader.sampleRate = 44100;
            case 10 -> frameHeader.sampleRate = 48000;
            case 11 -> frameHeader.sampleRate = 96000;
            case 12, 13, 14 -> sampleRateHint = x;
            case 15 -> invalid(StreamDecoderErrorStatus.BAD_HEADER);
        }
        x = (rawHeader[3] & 255) >>> 4;
        if ((x & 8) != 0) {
            frameHeader.channels = 2;
            switch (x & 7) {
                case 0 -> frameHeader.channelAssignment = ChannelAssignment.LEFT_SIDE;
                case 1 -> frameHeader.channelAssignment = ChannelAssignment.RIGHT_SIDE;
                case 2 -> frameHeader.channelAssignment = ChannelAssignment.MID_SIDE;
                default -> isUnparseable = true;
            }
        } else {
            frameHeader.channels = x + 1;
            frameHeader.channelAssignment = ChannelAssignment.INDEPENDENT;
        }
        x = (rawHeader[3] & 0x0e) >>> 1;
        switch (x) {
            case 0 -> {
                if (hasStreamInfo)
                    frameHeader.bitsPerSample = streamInfo.bitsPerSample;
                else
                    isUnparseable = true;
            }
            case 1 -> frameHeader.bitsPerSample = 8;
            case 2 -> frameHeader.bitsPerSample = 12;
            case 3 -> isUnparseable = true;
            case 4 -> frameHeader.bitsPerSample = 16;
            case 5 -> frameHeader.bitsPerSample = 20;
            case 6 -> frameHeader.bitsPerSample = 24;
            case 7 -> frameHeader.bitsPerSample = 32;
        }
        if ((rawHeader[3] & 0x01) != 0)
            isUnparseable = true;

        /* read the frame's starting sample number (or frame number as the case may be) */

        /* @@@ this clause is a concession to the old way of doing variable blocksize; the only known implementation is flake and can probably be removed without inconveniencing anyone */
        if ((rawHeader[1] & 0x01) != 0 || hasStreamInfo && streamInfo.minBlocksize != streamInfo.maxBlocksize) {
            /* variable blocksize */
            long xx = input.readUtf8UInt64(rawHeader, rawHeaderLen);
            /* i.e. non-UTF8 code... */
            if (xx == -1) {
                cachedByte = rawHeader[rawHeaderLen[0] - 1] & 255;
                invalid(StreamDecoderErrorStatus.BAD_HEADER);
            }
            frameHeader.numberType = FrameNumberType.SAMPLE_NUMBER;
            frameHeader.number = xx;
        } else {
            /* fixed blocksize */
            x = (int) input.readUtf8UInt32(rawHeader, rawHeaderLen);
            if (x == -1) {
                cachedByte = rawHeader[rawHeaderLen[0] - 1] & 255;
                invalid(StreamDecoderErrorStatus.BAD_HEADER);
            }
            frameHeader.numberType = FrameNumberType.FRAME_NUMBER;
            frameHeader.number = Integer.toUnsignedLong(x);
        }
        if (blocksizeHint != 0) {
            x = input.readRawUInt32(8);
            rawHeader[rawHeaderLen[0]++] = (byte) x;
            if (blocksizeHint == 7) {
                int low = input.readRawUInt32(8);
                rawHeader[rawHeaderLen[0]++] = (byte) low;
                x = (x << 8) | low;
            }
            frameHeader.blocksize = x + 1;
            /* invalid blocksize (65536) specified */
            if (frameHeader.blocksize > Format.MAX_BLOCK_SIZE) {
                /* back up as much as we can */
                cachedByte = rawHeader[rawHeaderLen[0] - 1] & 255;
                invalid(StreamDecoderErrorStatus.BAD_HEADER);
            }
        }
        if (sampleRateHint != 0) {
            x = input.readRawUInt32(8);
            rawHeader[rawHeaderLen[0]++] = (byte) x;
            if (sampleRateHint != 12) {
                int low = input.readRawUInt32(8);
                rawHeader[rawHeaderLen[0]++] = (byte) low;
                x = (x << 8) | low;
            }
            frameHeader.sampleRate = sampleRateHint == 12 ? x * 1000 : sampleRateHint == 13 ? x : x * 10;
        }
        /* read the CRC-8 byte */
        int crc8 = input.readRawUInt32(Format.FRAME_HEADER_CRC_LEN);
        if (Crc.crc8(rawHeader, 0, rawHeaderLen[0]) != crc8)
            invalid(StreamDecoderErrorStatus.BAD_HEADER);

        /* calculate the sample number from the frame number if needed */
        int nextFixedBlockSize = 0;
        if (frameHeader.numberType == FrameNumberType.FRAME_NUMBER) {
            long number = frameHeader.number;
            frameHeader.numberType = FrameNumberType.SAMPLE_NUMBER;
            if (fixedBlockSize != 0)
                frameHeader.number = fixedBlockSize * number;
            else if (hasStreamInfo) {
                if (streamInfo.minBlocksize == streamInfo.maxBlocksize) {
                    frameHeader.number = streamInfo.minBlocksize * number;
                    nextFixedBlockSize = streamInfo.maxBlocksize;
                } else
                    isUnparseable = true;
            } else if (number == 0) {
                frameHeader.number = 0;
                nextFixedBlockSize = frameHeader.blocksize;
            } else
                /* can only get here if the stream has invalid frame numbering and no STREAMINFO, so assume it's not the last (possibly short) frame */
                frameHeader.number = frameHeader.blocksize * number;
        }
        if (isUnparseable)
            invalid(StreamDecoderErrorStatus.UNPARSEABLE_STREAM);
        return nextFixedBlockSize;
    }

    private DecodedSubframe readSubframe(int blocksize, int bps, boolean doFullDecode) throws IOException {
        Subframe subframe = new Subframe();
        /* MAGIC NUMBER */
        int x = input.readRawUInt32(8);
        boolean wastedBits = (x & 1) != 0;
        x &= 0xfe;
        if (wastedBits) {
            subframe.wastedBits = (int) input.readUnaryUnsigned() + 1;
            if (Integer.compareUnsigned(subframe.wastedBits, bps) >= 0)
                invalid(StreamDecoderErrorStatus.LOST_SYNC);
            bps -= subframe.wastedBits;
        } else
            subframe.wastedBits = 0;
        subframe.data = new long[blocksize];
        /* Lots of magic numbers here */
        if ((x & 0x80) != 0)
            invalid(StreamDecoderErrorStatus.LOST_SYNC);
        else if (x == 0)
            readSubframeConstant(subframe, bps, doFullDecode);
        else if (x == 2)
            readSubframeVerbatim(subframe, bps);
        else if (x < 16)
            invalid(StreamDecoderErrorStatus.UNPARSEABLE_STREAM);
        else if (x <= 24) {
            int predictorOrder = (x >>> 1) & 7;
            if (blocksize <= predictorOrder)
                invalid(StreamDecoderErrorStatus.LOST_SYNC);
            readSubframeFixed(subframe, bps, predictorOrder, doFullDecode);
        } else if (x < 64)
            invalid(StreamDecoderErrorStatus.UNPARSEABLE_STREAM);
        else {
            int predictorOrder = ((x >>> 1) & 31) + 1;
            if (blocksize <= predictorOrder)
                invalid(StreamDecoderErrorStatus.LOST_SYNC);
            readSubframeLpc(subframe, bps, predictorOrder, doFullDecode);
        }
        /* decode the subframe */
        long[] output = doFullDecode ? subframe.data.clone() : null;
        if (wastedBits && doFullDecode) {
            x = subframe.wastedBits;
            if (bps + x < 33) {
                for (int i = 0; i < blocksize; i++) {
                    int val = (int) output[i];
                    output[i] = val << x;
                }
            } else {
                for (int i = 0; i < blocksize; i++) {
                    long val = output[i];
                    output[i] = val << x;
                }
            }
        }
        return new DecodedSubframe(subframe, output);
    }

    private void readSubframeConstant(Subframe subframe, int bps, boolean doFullDecode) throws IOException {
        subframe.type = SubframeType.CONSTANT;
        subframe.value = input.readRawInt64(bps);
        /* decode the subframe */
        if (doFullDecode)
            Arrays.fill(subframe.data, subframe.value);
    }

    private void readSubframeFixed(Subframe subframe, int bps, int order, boolean doFullDecode) throws IOException {
        subframe.type = SubframeType.FIXED;
        subframe.residual = new int[subframe.data.length - order];
        subframe.order = order;
        subframe.warmup = new long[order];
        /* read warm-up samples */
        for (int u = 0; u < order; u++)
            subframe.warmup[u] = input.readRawInt64(bps);
        /* read entropy coding method info */
        int u32 = input.readRawUInt32(Format.ENTROPY_CODING_METHOD_TYPE_LEN);
        if (u32 != EntropyCodingMethodType.PARTITIONED_RICE.ordinal()
                && u32 != EntropyCodingMethodType.PARTITIONED_RICE2.ordinal())
            invalid(StreamDecoderErrorStatus.UNPARSEABLE_STREAM);
        subframe.entropyCodingMethod.type = u32 == EntropyCodingMethodType.PARTITIONED_RICE.ordinal()
                ? EntropyCodingMethodType.PARTITIONED_RICE : EntropyCodingMethodType.PARTITIONED_RICE2;
        u32 = input.readRawUInt32(Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN);
        if ((subframe.data.length >> u32) < order || subframe.data.length % (1 << u32) > 0)
            invalid(StreamDecoderErrorStatus.LOST_SYNC);
        subframe.entropyCodingMethod.order = u32;
        /* read residual */
        readResidualPartitionedRice(order, subframe.entropyCodingMethod.order, subframe.entropyCodingMethod,
                subframe.residual, subframe.entropyCodingMethod.type == EntropyCodingMethodType.PARTITIONED_RICE2);
        /* decode the subframe */
        if (doFullDecode) {
            System.arraycopy(subframe.warmup, 0, subframe.data, 0, order);
            if (bps < 33)
                Fixed.restoreSignal(subframe.residual, subframe.residual.length, order, subframe.data, order);
            else
                Fixed.restoreSignalWide33Bit(subframe.residual, subframe.residual.length, order, subframe.data, order);
        }
    }

    private void readSubframeLpc(Subframe subframe, int bps, int order, boolean doFullDecode) throws IOException {
        subframe.type = SubframeType.LPC;
        subframe.residual = new int[subframe.data.length - order];
        subframe.order = order;
        subframe.warmup = new long[order];
        /* read warm-up samples */
        for (int u = 0; u < order; u++)
            subframe.warmup[u] = input.readRawInt64(bps);
        /* read qlp coeff precision */
        int u32 = input.readRawUInt32(Format.SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN);
        if (u32 == (1 << Format.SUBFRAME_LPC_QLP_COEFF_PRECISION_LEN) - 1)
            invalid(StreamDecoderErrorStatus.LOST_SYNC);
        subframe.qlpCoeffPrecision = u32 + 1;
        /* read qlp shift */
        int i32 = input.readRawInt32(Format.SUBFRAME_LPC_QLP_SHIFT_LEN);
        if (i32 < 0)
            invalid(StreamDecoderErrorStatus.LOST_SYNC);
        subframe.quantizationLevel = i32;
        subframe.qlpCoeff = new int[order];
        /* read quantized lp coefficiencts */
        for (int u = 0; u < order; u++)
            subframe.qlpCoeff[u] = input.readRawInt32(subframe.qlpCoeffPrecision);
        /* read entropy coding method info */
        u32 = input.readRawUInt32(Format.ENTROPY_CODING_METHOD_TYPE_LEN);
        if (u32 != EntropyCodingMethodType.PARTITIONED_RICE.ordinal()
                && u32 != EntropyCodingMethodType.PARTITIONED_RICE2.ordinal())
            invalid(StreamDecoderErrorStatus.UNPARSEABLE_STREAM);
        subframe.entropyCodingMethod.type = u32 == EntropyCodingMethodType.PARTITIONED_RICE.ordinal()
                ? EntropyCodingMethodType.PARTITIONED_RICE : EntropyCodingMethodType.PARTITIONED_RICE2;
        u32 = input.readRawUInt32(Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ORDER_LEN);
        if ((subframe.data.length >> u32) < order || subframe.data.length % (1 << u32) > 0)
            invalid(StreamDecoderErrorStatus.LOST_SYNC);
        subframe.entropyCodingMethod.order = u32;
        /* read residual */
        readResidualPartitionedRice(order, subframe.entropyCodingMethod.order, subframe.entropyCodingMethod,
                subframe.residual, subframe.entropyCodingMethod.type == EntropyCodingMethodType.PARTITIONED_RICE2);
        /* decode the subframe */
        if (doFullDecode) {
            System.arraycopy(subframe.warmup, 0, subframe.data, 0, order);
            if (bps <= Integer.SIZE) {
                if (Lpc.maxResidualBps(bps, subframe.qlpCoeff, order, subframe.quantizationLevel) <= Integer.SIZE
                        && Lpc.maxPredictionBeforeShiftBps(bps, subframe.qlpCoeff, order) <= Integer.SIZE)
                    Lpc.restoreSignal(subframe.residual, subframe.residual.length, subframe.qlpCoeff, order,
                            subframe.quantizationLevel, subframe.data, order);
                else
                    Lpc.restoreSignalWide(subframe.residual, subframe.residual.length, subframe.qlpCoeff, order,
                            subframe.quantizationLevel, subframe.data, order);
            } else
                Lpc.restoreSignalWide33Bit(subframe.residual, subframe.residual.length, subframe.qlpCoeff, order,
                        subframe.quantizationLevel, subframe.data, order);
        }
    }

    private void readSubframeVerbatim(Subframe subframe, int bps) throws IOException {
        subframe.type = SubframeType.VERBATIM;
        subframe.dataType = bps < 33 ? VerbatimSubframeDataType.INT32 : VerbatimSubframeDataType.INT64;
        for (int i = 0; i < subframe.data.length; i++)
            subframe.data[i] = input.readRawInt64(bps);
    }

    private void readResidualPartitionedRice(int predictorOrder, int partitionOrder,
            EntropyCodingMethod partitionedRiceContents, int[] residual, boolean isExtended) throws IOException {
        int partitions = 1 << partitionOrder;
        int partitionSamples = (residual.length + predictorOrder) >> partitionOrder;
        int plen = isExtended ? Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_PARAMETER_LEN
                : Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_PARAMETER_LEN;
        int pesc = isExtended ? Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE2_ESCAPE_PARAMETER
                : Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_ESCAPE_PARAMETER;
        /* invalid predictor and partition orders mush be handled in the callers */
        partitionedRiceContents.parameters = new int[partitions];
        partitionedRiceContents.rawBits = new int[partitions];
        int sample = 0;
        for (int partition = 0; partition < partitions; partition++) {
            int riceParameter = input.readRawUInt32(plen);
            partitionedRiceContents.parameters[partition] = riceParameter;
            int u = partitionSamples - (partition == 0 ? predictorOrder : 0);
            if (riceParameter < pesc) {
                try {
                    input.readRiceSignedBlock(residual, sample, u, riceParameter);
                } catch (BitReader.InvalidRice e) {
                    /* invalid rice symbol was found */
                    invalid(StreamDecoderErrorStatus.LOST_SYNC);
                }
                sample += u;
            } else {
                riceParameter = input.readRawUInt32(Format.ENTROPY_CODING_METHOD_PARTITIONED_RICE_RAW_LEN);
                partitionedRiceContents.rawBits[partition] = riceParameter;
                if (riceParameter == 0) {
                    Arrays.fill(residual, sample, sample + u, 0);
                    sample += u;
                } else
                    for (int i = 0; i < u; i++)
                        residual[sample++] = input.readRawInt32(riceParameter);
            }
        }
    }

    private void readZeroPadding() throws IOException {
        if (!input.isConsumedByteAligned() && input.readRawUInt32(input.getBitsLeftForByteAlignment()) != 0)
            invalid(StreamDecoderErrorStatus.LOST_SYNC);
    }

    private void metadataReadFailure(IOException failure) {
        if (failure instanceof BitReader.EndOfInput)
            state = StreamDecoderState.END_OF_STREAM;
        else if (failure instanceof InterruptedInput || failure instanceof OggDecoderAspect.Aborted)
            state = StreamDecoderState.ABORTED;
    }

    private static void undoChannelCoding(FrameHeader header, long[][] samples) {
        switch (header.channelAssignment) {
            case INDEPENDENT -> {
                /* do nothing */
            }
            case LEFT_SIDE -> {
                for (int i = 0; i < header.blocksize; i++)
                    samples[1][i] = samples[0][i] - samples[1][i];
            }
            case RIGHT_SIDE -> {
                for (int i = 0; i < header.blocksize; i++)
                    samples[0][i] += samples[1][i];
            }
            case MID_SIDE -> {
                for (int i = 0; i < header.blocksize; i++) {
                    if (header.bitsPerSample < Integer.SIZE) {
                        int mid = (int) samples[0][i];
                        int side = (int) samples[1][i];
                        mid <<= 1;
                        /* i.e. if 'side' is odd... */
                        mid |= side & 1;
                        samples[0][i] = (mid + side) >> 1;
                        samples[1][i] = (mid - side) >> 1;
                    } else {
                        /* bps == 32 */
                        long mid = samples[0][i] << 1;
                        /* i.e. if 'side' is odd... */
                        mid |= samples[1][i] & 1;
                        samples[0][i] = (mid + samples[1][i]) >> 1;
                        samples[1][i] = (mid - samples[1][i]) >> 1;
                    }
                }
            }
        }
    }

    private boolean writeAudioFrameToClient(Frame frame, int[][] buffer) throws IOException {
        FrameHeader header = frame.header;
        /* save the frame */
        lastFrameHeader = copyHeader(header);
        if (isSeeking) {
            if (Long.compareUnsigned(header.number, targetSample) <= 0
                    && Long.compareUnsigned(targetSample, header.number + header.blocksize) < 0) {
                /* we hit our target frame */
                int delta = (int) (targetSample - header.number);
                /* kick out of seek mode */
                isSeeking = false;
                /* shift out the samples before targetSample */
                if (delta > 0) {
                    Frame delivered = new Frame();
                    delivered.header = copyHeader(header);
                    delivered.footer = frame.footer;
                    delivered.header.number += delta;
                    delivered.header.blocksize -= delta;
                    delivered.subframes = new Subframe[header.channels];
                    for (int c = 0; c < header.channels; c++) {
                        buffer[c] = Arrays.copyOfRange(buffer[c], delta, header.blocksize);
                        Subframe sub = delivered.subframes[c] = new Subframe();
                        sub.type = SubframeType.VERBATIM;
                        sub.wastedBits = frame.subframes[c].wastedBits;
                        sub.data = new long[buffer[c].length];
                        for (int i = 0; i < buffer[c].length; i++)
                            sub.data[i] = buffer[c][i];
                    }
                    frame = delivered;
                    lastFrameHeader = copyHeader(delivered.header);
                }
                /* write the relevant samples */
                return writeCallback.write(this, frame, buffer);
            }
            return true;
        }
        /*
         * If we never got STREAMINFO, turn off MD5 checking to save
         * cycles since we don't have a sum to compare to anyway
         */
        if (!hasStreamInfo)
            doMd5Checking = false;
        if (doMd5Checking)
            md5.accumulate(buffer, 0, header.channels, header.blocksize, (header.bitsPerSample + 7) / 8);
        return writeCallback.write(this, frame, buffer);
    }

    private static FrameHeader copyHeader(FrameHeader h) {
        FrameHeader c = new FrameHeader();
        c.blocksize = h.blocksize;
        c.sampleRate = h.sampleRate;
        c.channels = h.channels;
        c.channelAssignment = h.channelAssignment;
        c.bitsPerSample = h.bitsPerSample;
        c.numberType = h.numberType;
        c.number = h.number;
        c.crc = h.crc;
        return c;
    }

    private void sendErrorToClient(StreamDecoderErrorStatus status) {
        if (!isSeeking)
            errorCallback.error(this, status);
        else if (status == StreamDecoderErrorStatus.UNPARSEABLE_STREAM)
            unparseableFrameCount++;
    }

    private boolean seekError() {
        state = StreamDecoderState.SEEK_ERROR;
        return false;
    }

    /* physical seek */
    private boolean physicalSeek(long position) throws IOException {
        if (seekCallback.seek(this, position) != StreamDecoderSeekStatus.OK)
            return seekError();
        flush();
        return true;
    }

    private static double unsignedDouble(long value) {

        return value >= 0 ? (double) value : (double) ((value >>> 1) | (value & 1)) * 2.0;
    }

    private boolean seekToAbsoluteSample(long streamLength, long targetSample) throws IOException {
        long totalSamples = getTotalSamples();
        /* use values from stream info if we didn't decode a frame */
        StreamInfo info = streamInfo;
        /* take these from the current frame in case they've changed mid-stream */
        int channels = frameHeader.channels != 0 ? frameHeader.channels : info.channels,
                bps = frameHeader.bitsPerSample != 0 ? frameHeader.bitsPerSample : info.bitsPerSample;
        long approxBytesPerFrame;
        if (info.maxFramesize > 0)
            /* we are just guessing here */
            approxBytesPerFrame = (info.maxFramesize + info.minFramesize) / 2L + 1;
        /*
         * Check if it's a known fixed-blocksize stream.  Note that though
         * the spec doesn't allow zeroes in the STREAMINFO block, we may
         * never get a STREAMINFO block when decoding so the value of
         * minBlocksize might be zero.
         */
        else if (info.minBlocksize == info.maxBlocksize && info.minBlocksize > 0)
            /* note there are no () around 'bps/8' to keep precision up since it's an integer calculation */
            approxBytesPerFrame = (long) info.minBlocksize * channels * bps / 8 + 64;
        else
            approxBytesPerFrame = 4096L * channels * bps / 8 + 64;
        /*
         * First, we set an upper and lower bound on where in the
         * stream we will search.  For now we take the current position
         * as one bound and, depending on where the target position lies,
         * the beginning of the first frame or the end of the stream as
         * the other bound.
         */
        long lowerBound = firstFrameOffset, lowerBoundSample = 0, upperBound = streamLength,
                /* estimate it */
                upperBoundSample = totalSamples > 0 ? totalSamples : targetSample;
        if (state == StreamDecoderState.SEARCH_FOR_FRAME_SYNC && samplesDecoded != 0) {
            long pos = getDecodePosition();
            if (pos >= 0) {
                if (Long.compareUnsigned(targetSample, samplesDecoded) < 0) {
                    upperBound = pos;
                    upperBoundSample = samplesDecoded;
                } else {
                    lowerBound = pos;
                    lowerBoundSample = samplesDecoded;
                }
            }
        }
        /*
         * Now we refine the bounds if we have a seektable with
         * suitable points.  Note that according to the spec they
         * must be ordered by ascending sample number.
         *
         * Note: to protect against invalid seek tables we will ignore points
         * that have frameSamples==0 or sampleNumber>=totalSamples. Also,
         * because math is limited to 64-bit ints, seekpoints with an offset
         * larger than 2^63 (8 exbibyte) are rejected.
         */
        if (seekTable != null) {
            long newLowerBound = lowerBound, newUpperBound = upperBound, newLowerBoundSample = lowerBoundSample,
                    newUpperBoundSample = upperBoundSample;
            /* find the closest seek point <= targetSample, if it exists */

            /* defense against bad seekpoints */
            for (int i = seekTable.points.length - 1; i >= 0; i--) {
                SeekPoint p = seekTable.points[i];
                if (p.sampleNumber != Format.STREAM_METADATA_SEEKPOINT_PLACEHOLDER
                        /* defense against bad seekpoints */
                        && Integer.compareUnsigned(p.frameSamples, 0) > 0
                        && (totalSamples == 0 || Long.compareUnsigned(p.sampleNumber, totalSamples) < 0)
                        && Long.compareUnsigned(p.sampleNumber, targetSample) <= 0) {
                    /* i.e. we found a suitable seek point... */
                    newLowerBound = firstFrameOffset + p.streamOffset;
                    newLowerBoundSample = p.sampleNumber;
                    break;
                }
            }
            /* find the closest seek point > targetSample, if it exists */
            for (SeekPoint p : seekTable.points)
                if (p.sampleNumber != Format.STREAM_METADATA_SEEKPOINT_PLACEHOLDER
                        /* defense against bad seekpoints */
                        && Integer.compareUnsigned(p.frameSamples, 0) > 0
                        && (totalSamples == 0 || Long.compareUnsigned(p.sampleNumber, totalSamples) < 0)
                        && Long.compareUnsigned(p.sampleNumber, targetSample) > 0) {
                    /* i.e. we found a suitable seek point... */
                    newUpperBound = firstFrameOffset + p.streamOffset;
                    newUpperBoundSample = p.sampleNumber;
                    break;
                }
            /* final protection against unsorted seek tables; keep original values if bogus */
            if (Long.compareUnsigned(newUpperBound, newLowerBound) >= 0) {
                lowerBound = newLowerBound;
                upperBound = newUpperBound;
                lowerBoundSample = newLowerBoundSample;
                upperBoundSample = newUpperBoundSample;
            }
        }
        /*
         * there are 2 insidious ways that the following equality occurs, which
         * we need to fix:
         *  1) totalSamples is 0 (unknown) and targetSample is 0
         *  2) totalSamples is 0 (unknown) and targetSample happens to be
         *     exactly equal to the last seek point in the seek table; this
         *     means there is no seek point above it, and upperBoundSample
         *     remains equal to the estimate (of targetSample) we made above
         * in either case it does not hurt to move upperBoundSample up by 1
         */
        if (upperBoundSample == lowerBoundSample)
            upperBoundSample++;
        this.targetSample = targetSample;
        boolean firstSeek = true, seekFromLowerBound = false;
        while (true) {
            /*
             * check whether decoder is still valid so bad state isn't overwritten
             * with seek error
             */
            if (state == StreamDecoderState.MEMORY_ALLOCATION_ERROR || state == StreamDecoderState.ABORTED)
                return false;
            /* check if the bounds are still ok */
            if (Long.compareUnsigned(lowerBoundSample, upperBoundSample) >= 0
                    || Long.compareUnsigned(lowerBound, upperBound) > 0
                    || Long.compareUnsigned(upperBound, Long.MAX_VALUE) >= 0)
                return seekError();
            long pos = seekFromLowerBound ? lowerBound
                    : lowerBound + (long) (unsignedDouble(targetSample - lowerBoundSample)
                            / unsignedDouble(upperBoundSample - lowerBoundSample)
                            * unsignedDouble(upperBound - lowerBound)) - approxBytesPerFrame;
            if (pos >= upperBound)
                pos = upperBound - 1;
            if (pos < lowerBound)
                pos = lowerBound;
            if (!physicalSeek(pos))
                return false;
            /*
             * Now we need to get a frame.  First we need to reset our
             * unparseableFrameCount; if we get too many unparseable
             * frames in a row, the read callback will return
             * -2, causing
             * StreamDecoder.processSingle() to return false.
             */
            unparseableFrameCount = 0;
            boolean ok = processSingle();
            if (!ok || state == StreamDecoderState.ABORTED || samplesDecoded == 0) {
                /* No frame could be decoded */
                if (state != StreamDecoderState.ABORTED && eofCallback.eof(this) && !seekFromLowerBound) {
                    /*
                     * decoder has hit end of stream while processing corrupt
                     * frame. To remedy this, try decoding a frame at the lower
                     * bound so the seek after that hopefully ends up somewhere
                     * else
                     */
                    seekFromLowerBound = true;
                    continue;
                }
                return seekError();
            }
            seekFromLowerBound = false;
            /* our write callback will change the state when it gets to the target frame */
            /* actually, we could have gotAFrame if our decoder is at StreamDecoderState.END_OF_STREAM so we need to check for that also */
            if (!isSeeking)
                return true;
            long thisFrameSample = lastFrameHeader.number;
            /* allow one seek over upper bound, so we can get a correct upperBoundSample for streams with unknown totalSamples */
            if (Long.compareUnsigned(thisFrameSample + lastFrameHeader.blocksize, upperBoundSample) >= 0
                    && !firstSeek) {
                /* can't move back any more than the first frame, something is fatally wrong */
                if (pos == lowerBound)
                    return seekError();
                /* our last move backwards wasn't big enough, try again */
                approxBytesPerFrame = approxBytesPerFrame != 0 ? (approxBytesPerFrame * 2) & 0xffffffffL : 16;
                continue;
            }
            firstSeek = false;
            /* make sure we are not seeking in corrupted stream */
            if (Long.compareUnsigned(thisFrameSample, lowerBoundSample) < 0)
                return seekError();
            /* @@@@@@ what will decode position be if at end of stream? */
            long decodedPosition = getDecodePosition();
            if (decodedPosition < 0)
                return seekError();
            /* we need to narrow the search */
            if (Long.compareUnsigned(targetSample, thisFrameSample) < 0) {
                upperBoundSample = thisFrameSample + lastFrameHeader.blocksize;
                upperBound = decodedPosition;
                approxBytesPerFrame = (Long.divideUnsigned(2 * (upperBound - pos), 3) + 16) & 0xffffffffL;
            } else {
                /* targetSample >= thisFrameSample + this frame's blocksize */
                lowerBoundSample = thisFrameSample + lastFrameHeader.blocksize;
                lowerBound = decodedPosition;
                approxBytesPerFrame = (Long.divideUnsigned(2 * (lowerBound - pos), 3) + 16) & 0xffffffffL;
            }
        }
    }

    private boolean seekToAbsoluteSampleOgg(long streamLength, long targetSample) throws IOException {
        long leftPos = 0, rightPos = streamLength, leftSample = 0, rightSample = getTotalSamples(),
                thisFrameSample = -1, pos = 0;
        /*
         * In the first iterations, we will calculate the target byte position
         * by the distance from the target sample to leftSample and
         * rightSample (let's call it "proportional search").  After that, we
         * will switch to binary search.
         */
        int binarySearchAfterIteration = 2;
        /*
         * If the total number of samples is unknown, use a large value, and
         * force binary search immediately.
         */
        if (rightSample == 0) {
            rightSample = -1L;
            binarySearchAfterIteration = 0;
        }
        this.targetSample = targetSample;
        for (int iteration = 0;; iteration++) {
            /* Do sanity checks on bounds */
            /* FLAC frame is at least 9 byte in size */
            if (rightPos <= leftPos || rightPos - leftPos < 9)
                return seekError();
            boolean didASeek;
            if (iteration == 0 || Long.compareUnsigned(thisFrameSample, targetSample) > 0
                    || Long.compareUnsigned(targetSample - thisFrameSample, LINEAR_SEARCH_WITHIN_SAMPLES) > 0) {
                pos = iteration >= binarySearchAfterIteration ? leftPos + (rightPos - leftPos) / 2
                        : (long) (unsignedDouble(targetSample - leftSample) / unsignedDouble(rightSample - leftSample)
                                * unsignedDouble(rightPos - leftPos));
                /*
                 * @@@ TODO: might want to limit pos to some distance
                 * before EOF, to make sure we land before the last frame,
                 * thereby getting a thisFrameSample and so having a better
                 * estimate.
                 */
                if (!physicalSeek(pos))
                    return false;
                didASeek = true;
            } else
                didASeek = false;
            gotAFrame = false;
            if (!processSingle() || state == StreamDecoderState.ABORTED)
                return seekError();
            /*
             * this can probably only happen if totalSamples is unknown and the
             * targetSample is past the end of the stream
             */
            if (!gotAFrame) {
                if (didASeek) {
                    /*
                     * this can happen if we seek to a point after the last frame; we drop
                     * to binary search right away in this case to avoid any wasted
                     * iterations of proportional search.
                     */
                    rightPos = pos;
                    binarySearchAfterIteration = 0;
                } else
                    return seekError();
            } else if (!isSeeking)
                /* our write callback will change the state when it gets to the target frame */
                return true;
            else {
                thisFrameSample = lastFrameHeader.number;
                if (didASeek) {
                    /*
                     * The 'equal' case should not happen, since
                     * StreamDecoder.processSingle()
                     * should recognize that it has hit the
                     * target sample and we would exit through
                     * the return above.
                     */
                    if (Long.compareUnsigned(thisFrameSample, targetSample) <= 0) {
                        leftSample = thisFrameSample;
                        /* sanity check to avoid infinite loop */
                        if (leftPos == pos)
                            return seekError();
                        leftPos = pos;
                    } else {
                        rightSample = thisFrameSample;
                        if (rightPos == pos)
                            return seekError();
                        rightPos = pos;
                    }
                }
            }
        }
    }

    private int fileReadCallback(StreamDecoder decoder, byte[] buffer, int offset, int bytes) throws IOException {
        return file.read(buffer, offset, bytes);
    }

    private StreamDecoderSeekStatus fileSeekCallback(StreamDecoder decoder, long absoluteByteOffset)
            throws IOException {
        file.seek(absoluteByteOffset);
        return StreamDecoderSeekStatus.OK;
    }

    private long fileTellCallback(StreamDecoder decoder) throws IOException {
        return file.getFilePointer();
    }

    private long fileLengthCallback(StreamDecoder decoder) throws IOException {
        return file.length();
    }

    private boolean fileEofCallback(StreamDecoder decoder) throws IOException {
        return file.getFilePointer() >= file.length();
    }

    @FunctionalInterface
    public interface ReadCallback {
        /**
         * Signature for the read callback.
         *
         *  A callback matching this signature must be passed to
         *  StreamDecoder.init*Stream(). The supplied function will be
         *  called when the decoder needs more input data.  The buffer to be filled is supplied, along with the number
         * of bytes the
         *  buffer can hold.  The callback may choose to supply less data and return the byte count but must be careful
         * not to overflow the buffer.
         *  The callback then returns the number of bytes read.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @param buffer A buffer for the callee to store
         *                  data to be decoded.
         * @param bytes The maximum number of bytes that may be stored in the buffer.
         * @return The number of bytes read, -1 at end-of-stream, or -2 to abort.
         */
        int read(StreamDecoder decoder, byte[] buffer, int offset, int bytes) throws IOException;
    }

    @FunctionalInterface
    public interface SeekCallback {
        /**
         * Signature for the seek callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamDecoder.init*Stream().  The supplied function will be
         *  called when the decoder needs to seek the input stream.  The decoder
         *  will pass the absolute byte offset to seek to, 0 meaning the
         *  beginning of the stream.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @param absoluteByteOffset The offset from the beginning of the stream
         *                               to seek to.
         * @return The callee's return status.
         */
        StreamDecoderSeekStatus seek(StreamDecoder decoder, long absoluteByteOffset) throws IOException;
    }

    @FunctionalInterface
    public interface TellCallback {
        /**
         * Signature for the tell callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamDecoder.init*Stream().  The supplied function will be
         *  called when the decoder wants to know the current position of the
         *  stream.  The callback should return the byte offset from the
         *  beginning of the stream.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @return The current position, -1 if seeking is unsupported, or -2 on error.
         */
        long tell(StreamDecoder decoder) throws IOException;
    }

    @FunctionalInterface
    public interface LengthCallback {
        /**
         * Signature for the length callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamDecoder.init*Stream().  The supplied function will be
         *  called when the decoder wants to know the total length of the stream
         *  in bytes.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @return The stream length, -1 if unsupported, or -2 on error.
         */
        long length(StreamDecoder decoder) throws IOException;
    }

    @FunctionalInterface
    public interface EofCallback {
        /**
         * Signature for the EOF callback.
         *
         *  A callback matching this signature may be passed to
         *  StreamDecoder.init*Stream().  The supplied function will be
         *  called when the decoder needs to know if the end of the stream has
         *  been reached.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @return {@code true} if the currently at the end of the stream, else {@code false}.
         */
        boolean eof(StreamDecoder decoder) throws IOException;
    }

    @FunctionalInterface
    public interface WriteCallback {
        /**
         * Signature for the write callback.
         *
         *  A callback matching this signature must be passed to one of
         *  the StreamDecoder.init*() functions.
         *  The supplied function will be called when the decoder has decoded a
         *  single audio frame.  The decoder will pass the frame metadata as well
         *  as an array of arrays (one for each channel) containing the
         *  decoded audio.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @param frame The description of the decoded frame.  See
         *                  Frame.
         * @param buffer An array of decoded channels of data.
         *                  Each channel is an array of signed
         *                  samples of length {@code frame.getHeader().getBlocksize()}.
         *                  Channels will be ordered according to the FLAC
         *                  specification; see the documentation for the
         *                  <A HREF="https://xiph.org/flac/format.html#frame_header">frame header</A>.
         * @return true if the data was accepted, or false if a fatal error occurred.
         */
        boolean write(StreamDecoder decoder, Frame frame, int[][] buffer) throws IOException;
    }

    @FunctionalInterface
    public interface MetadataCallback {
        /**
         * Signature for the metadata callback.
         *
         *  A callback matching this signature must be passed to one of
         *  the StreamDecoder.init*() functions.
         *  The supplied function will be called when the decoder has decoded a
         *  metadata block.  In a valid FLAC file there will always be one
         *  {@code STREAMINFO} block, followed by zero or more other metadata blocks.
         *  These will be supplied by the decoder in the same order as they
         *  appear in the stream and always before the first audio frame (i.e.
         *  write callback).  The metadata block that is passed in must not be modified.  Since metadata blocks can
         * potentially be large, by
         *  default the decoder only calls the metadata callback for the
         *  {@code STREAMINFO} block; you can instruct the decoder to pass or filter
         *  other blocks with StreamDecoder.setMetadata*() calls.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @param metadata The decoded metadata block.
         */
        void metadata(StreamDecoder decoder, StreamMetadata metadata);
    }

    @FunctionalInterface
    public interface ErrorCallback {
        /**
         * Signature for the error callback.
         *
         *  A callback matching this signature must be passed to one of
         *  the StreamDecoder.init*() functions.
         *  The supplied function will be called whenever an error occurs during
         *  decoding.
         *
         * <p>In general, StreamDecoder functions which change the
         * state should not be called on the {@code decoder} while in the callback.
         *
         * @param decoder The decoder instance calling the callback.
         * @param status The error encountered by the decoder.
         */
        void error(StreamDecoder decoder, StreamDecoderErrorStatus status);
    }

    private final class CallbackInput extends InputStream {
        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n;
            do {
                n = read(one, 0, 1);
            } while (n == 0);
            return n < 0 ? -1 : one[0] & 255;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            Objects.checkFromIndexSize(offset, count, buffer.length);
            if (count == 0)
                return 0;
            int n = readCallback.read(StreamDecoder.this, buffer, offset, count);
            if (n == -2)
                throw new InterruptedInput();
            if (n == -1)
                return -1;
            if (n < 0 || n > count)
                throw new IOException("invalid decoder read callback count");
            return n;
        }
    }

    private final class DecoderInput extends InputStream {
        private final InputStream decoded;

        DecoderInput(InputStream decoded) {
            this.decoded = decoded;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n;
            do {
                n = read(one, 0, 1);
            } while (n == 0);
            return n < 0 ? -1 : one[0] & 255;
        }

        /*
         * [1] @@@ HACK NOTE: The end-of-stream checking has to be hacked around
         * for Ogg FLAC.  This is because the ogg decoder aspect can lose sync
         * and at the same time hit the end of the stream (for example, seeking
         * to a point that is after the beginning of the last Ogg page).  There
         * is no way to report an Ogg sync loss through the callbacks  so it returns a byte count of 0.
         * So to keep the decoder from stopping at this point we gate the call
         * to the eofCallback and let the Ogg decoder aspect set the
         * end-of-stream state when it is needed.
         */
        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            try {
                /* see [1] HACK NOTE above for why we don't call the eofCallback when decoding Ogg FLAC */
                if (!isOgg && eofCallback != null && eofCallback.eof(StreamDecoder.this))
                    return -1;
                /*
                 * While seeking, it is possible for our seek to land in the
                 * middle of audio data that looks exactly like a frame header
                 * from a future version of an encoder.  When that happens, our
                 * error callback will get an
                 * StreamDecoderErrorStatus.UNPARSEABLE_STREAM and increment its
                 * unparseableFrameCount.  But there is a remote possibility
                 * that it is properly synced at such a "future-codec frame",
                 * so to make sure, we wait to see many "unparseable" errors in
                 * a row before bailing out.
                 */
                if (isSeeking && unparseableFrameCount > 20)
                    throw new InterruptedInput();
                int bytes = decoded.read(buffer, offset, count);
                if (bytes == 0 && !isOgg && eofCallback != null && eofCallback.eof(StreamDecoder.this))
                    return -1;
                return bytes;
            } catch (Exception e) {
                state = StreamDecoderState.ABORTED;
                throw e;
            }
        }
    }

    private record DecodedSubframe(Subframe subframe, long[] output) {
    }

    private static final class InterruptedInput extends IOException {
    }

    private static final class InvalidFrame extends IOException {
    }
}
