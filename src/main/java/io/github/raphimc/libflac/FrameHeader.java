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
 * FLAC frame header structure.  (c.f. <A HREF="https://xiph.org/flac/format.html#frame_header">format
 * specification</A>)
 */
public final class FrameHeader {
    /** The number of samples per subframe. */
    int blocksize = 0;
    /** The sample rate in Hz. */
    int sampleRate = 0;
    /** The number of channels (== number of subframes). */
    int channels = 0;
    /** The channel assignment for the frame. */
    ChannelAssignment channelAssignment = ChannelAssignment.INDEPENDENT;
    /** The sample resolution. */
    int bitsPerSample = 0;
    /**
     * The numbering scheme used for the frame.  As a convenience, the
     * decoder will always convert a frame number to a sample number because
     * the rules are complex.
     */
    FrameNumberType numberType = FrameNumberType.SAMPLE_NUMBER;
    /**
     * The frame number or sample number of first sample in frame;
     * use the {@code numberType} value to determine which to use.
     */
    long number = 0;
    /**
     * CRC-8 (polynomial = x^8 + x^2 + x^1 + x^0, initialized with 0)
     * of the raw frame header bytes, meaning everything before the CRC byte
     * including the sync code.
     */
    int crc = 0;

    public int getBlocksize() {
        return blocksize;
    }

    public void setBlocksize(int value) {
        blocksize = value;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int value) {
        sampleRate = value;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int value) {
        channels = value;
    }

    public ChannelAssignment getChannelAssignment() {
        return channelAssignment;
    }

    public void setChannelAssignment(ChannelAssignment value) {
        channelAssignment = value;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }

    public void setBitsPerSample(int value) {
        bitsPerSample = value;
    }

    public FrameNumberType getNumberType() {
        return numberType;
    }

    public void setNumberType(FrameNumberType value) {
        numberType = value;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long value) {
        number = value;
    }

    public int getCrc() {
        return crc;
    }

    public void setCrc(int value) {
        crc = value & 255;
    }
}
