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

/**
 * FLAC APPLICATION structure.  (c.f. <A HREF="https://xiph.org/flac/format.html#metadata_block_application">format
 * specification</A>)
 */
public final class Application extends StreamMetadata {
    public Application() {
        super(MetadataType.APPLICATION.ordinal());
    }

    byte[] id = new byte[Format.STREAM_METADATA_APPLICATION_ID_LEN / Byte.SIZE];
    byte[] data = new byte[0];

    public byte[] getId() {
        return id;
    }

    public void setId(byte[] value) {
        id = value;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Sets the application data of an APPLICATION block.
     *
     * @param data The data to set.
     */
    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Sets the application data of an APPLICATION block.
     *
     *  If {@code copy} is {@code true}, a copy of the data is stored; otherwise, the object
     *  retains the array.
     *
     * @param data The data to set.
     * @param copy See above.
     */
    public void setData(byte[] data, boolean copy) {
        /* do the copy first so that if we fail we leave the object untouched */
        this.data = data == null && !copy ? new byte[0] : copy ? data.clone() : data;
    }
}
