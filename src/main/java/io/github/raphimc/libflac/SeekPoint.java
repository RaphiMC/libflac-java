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
 * SeekPoint structure used in SEEKTABLE blocks.  (c.f. <A HREF="https://xiph.org/flac/format.html#seekpoint">format
 * specification</A>)
 */
public final class SeekPoint {
    /** The sample number of the target frame. */
    long sampleNumber = Format.STREAM_METADATA_SEEKPOINT_PLACEHOLDER;
    /**
     * The offset, in bytes, of the target frame with respect to
     * beginning of the first frame.
     */
    long streamOffset = 0;
    /** The number of samples in the target frame. */
    int frameSamples = 0;

    public SeekPoint() {
    }

    public SeekPoint(long sampleNumber, long streamOffset, int frameSamples) {
        this.sampleNumber = sampleNumber;
        this.streamOffset = streamOffset;
        this.frameSamples = frameSamples;
    }

    public long getSampleNumber() {
        return sampleNumber;
    }

    public void setSampleNumber(long value) {
        sampleNumber = value;
    }

    public long getStreamOffset() {
        return streamOffset;
    }

    public void setStreamOffset(long value) {
        streamOffset = value;
    }

    public int getFrameSamples() {
        return frameSamples;
    }

    public void setFrameSamples(int value) {
        frameSamples = value;
    }

    public SeekPoint copy() {
        return new SeekPoint(sampleNumber, streamOffset, frameSamples);
    }
}
