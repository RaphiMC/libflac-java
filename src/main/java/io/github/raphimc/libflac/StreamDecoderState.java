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
 * State values for a StreamDecoder
 *
 * The decoder's state can be obtained by calling StreamDecoder.getState().
 */
public enum StreamDecoderState {
    /** The decoder is ready to search for metadata. */
    SEARCH_FOR_METADATA,
    /** The decoder is ready to or is in the process of reading metadata. */
    READ_METADATA,
    /**
     * The decoder is ready to or is in the process of searching for the
     * frame sync code.
     */
    SEARCH_FOR_FRAME_SYNC,
    /** The decoder is ready to or is in the process of reading a frame. */
    READ_FRAME,
    /** The decoder has reached the end of the stream. */
    END_OF_STREAM,
    /** An error occurred in the underlying Ogg layer. */
    OGG_ERROR,
    /**
     * An error occurred while seeking.  The decoder must be flushed
     * with StreamDecoder.flush() or reset with
     * StreamDecoder.reset() before decoding can continue.
     */
    SEEK_ERROR,
    /** The decoder was aborted by the read or write callback. */
    ABORTED,
    /**
     * An error occurred allocating memory.  The decoder is in an invalid
     * state and can no longer be used.
     */
    MEMORY_ALLOCATION_ERROR,
    /**
     * The decoder is in the uninitialized state; one of the
     * StreamDecoder.init*() functions must be called before samples
     * can be processed.
     */
    UNINITIALIZED
}
