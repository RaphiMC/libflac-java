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
 * Contents of a Rice partitioned residual
 *
 * Header for a Rice partitioned residual.  (c.f. <A HREF="https://xiph.org/flac/format.html#partitioned_rice">format
 * specification</A>)
 *
 * Header for the entropy coding method.  (c.f. <A HREF="https://xiph.org/flac/format.html#residual">format
 * specification</A>)
 */
public final class EntropyCodingMethod {
    EntropyCodingMethodType type = EntropyCodingMethodType.PARTITIONED_RICE;
    /** The partition order, i.e. # of contexts = 2 ^ {@code order}. */
    int order = 0;
    /** The Rice parameters for each context. */
    int[] parameters = new int[0];
    /**
     * Widths for escape-coded partitions.  Will be non-zero for escaped
     * partitions and zero for unescaped partitions.
     */
    int[] rawBits = new int[0];

    public EntropyCodingMethodType getType() {
        return type;
    }

    public void setType(EntropyCodingMethodType value) {
        type = value;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int value) {
        order = value;
    }

    public int[] getParameters() {
        return parameters;
    }

    public void setParameters(int[] value) {
        parameters = value;
    }

    public int[] getRawBits() {
        return rawBits;
    }

    public void setRawBits(int[] value) {
        rawBits = value;
    }
}
