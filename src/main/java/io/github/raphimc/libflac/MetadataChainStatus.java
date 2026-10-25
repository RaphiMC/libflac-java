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

public enum MetadataChainStatus {
    /** The chain is in the normal OK state */
    OK,
    /** The data passed into a function violated the function's usage criteria */
    ILLEGAL_INPUT,
    /** The chain could not open the target file */
    ERROR_OPENING_FILE,
    /** The chain could not find the FLAC signature at the start of the file */
    NOT_A_FLAC_FILE,
    /** The chain tried to write to a file that was not writable */
    NOT_WRITABLE,
    /** The chain encountered input that does not conform to the FLAC metadata specification */
    BAD_METADATA,
    /** The chain encountered an error while reading the FLAC file */
    READ_ERROR,
    /** The chain encountered an error while seeking in the FLAC file */
    SEEK_ERROR,
    /** The chain encountered an error while writing the FLAC file */
    WRITE_ERROR,
    /** The chain encountered an error renaming the FLAC file */
    RENAME_ERROR,
    /** The chain encountered an error removing the temporary file */
    UNLINK_ERROR,
    /** Memory allocation failed */
    MEMORY_ALLOCATION_ERROR,
    /** The caller violated an assertion or an unexpected error occurred */
    INTERNAL_ERROR,
    /** One or more of the required callbacks was null */
    INVALID_CALLBACKS,
    /**
     * MetadataChain.write() was called on a chain read by
     *   MetadataChain.readWithCallbacks()/MetadataChain.readOggWithCallbacks(),
     *   or
     *   MetadataChain.writeWithCallbacks()/MetadataChain.writeWithCallbacksAndTempfile()
     *   was called on a chain read by
     *   MetadataChain.read()/MetadataChain.readOgg().
     *   Matching read/write methods must always be used.
     */
    READ_WRITE_MISMATCH,
    /**
     * MetadataChain.writeWithCallbacks() was called when the
     *   chain write requires a tempfile; use
     *   MetadataChain.writeWithCallbacksAndTempfile() instead.
     *   Or, MetadataChain.writeWithCallbacksAndTempfile() was
     *   called when the chain write does not require a tempfile; use
     *   MetadataChain.writeWithCallbacks() instead.
     *   Always check MetadataChain.checkIfTempfileNeeded()
     *   before writing via callbacks.
     */
    WRONG_WRITE_CALL
}
