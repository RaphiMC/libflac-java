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
 * CONSTANT subframe.  (c.f. <A HREF="https://xiph.org/flac/format.html#subframe_constant">format specification</A>)
 *
 * VERBATIM subframe.  (c.f. <A HREF="https://xiph.org/flac/format.html#subframe_verbatim">format specification</A>)
 *
 * FIXED subframe.  (c.f. <A HREF="https://xiph.org/flac/format.html#subframe_fixed">format specification</A>)
 *
 * LPC subframe.  (c.f. <A HREF="https://xiph.org/flac/format.html#subframe_lpc">format specification</A>)
 *
 * FLAC subframe structure.  (c.f. <A HREF="https://xiph.org/flac/format.html#subframe">format specification</A>)
 */
public final class Subframe {
    SubframeType type = SubframeType.VERBATIM;
    int wastedBits = 0;
    long[] data = new long[0];
    VerbatimSubframeDataType dataType = VerbatimSubframeDataType.INT32;
    /** The constant signal value. */
    long value;
    /**
     * FIXED: The polynomial order.
     *
     * LPC: The FIR order.
     */
    int order = 0;
    /** Quantized FIR filter coefficient precision in bits. */
    int qlpCoeffPrecision = 0;
    /** The qlp coeff shift needed. */
    int quantizationLevel = 0;
    /** FIR filter coefficients. */
    int[] qlpCoeff = new int[0];
    /** Warmup samples to prime the predictor, length == order. */
    long[] warmup = new long[0];
    /** The residual signal, length == (blocksize minus order) samples. */
    int[] residual = new int[0];
    /** The residual coding method. */
    EntropyCodingMethod entropyCodingMethod = new EntropyCodingMethod();

    public SubframeType getType() {
        return type;
    }

    public void setType(SubframeType value) {
        type = value;
    }

    public int getWastedBits() {
        return wastedBits;
    }

    public void setWastedBits(int value) {
        wastedBits = value;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public long[] getData() {
        return data;
    }

    public void setData(long[] value) {
        data = value;
    }

    public VerbatimSubframeDataType getDataType() {
        return dataType;
    }

    public void setDataType(VerbatimSubframeDataType value) {
        dataType = value;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int value) {
        order = value;
    }

    public int getQlpCoeffPrecision() {
        return qlpCoeffPrecision;
    }

    public void setQlpCoeffPrecision(int value) {
        qlpCoeffPrecision = value;
    }

    public int getQuantizationLevel() {
        return quantizationLevel;
    }

    public void setQuantizationLevel(int value) {
        quantizationLevel = value;
    }

    public int[] getQlpCoeff() {
        return qlpCoeff;
    }

    public void setQlpCoeff(int[] value) {
        qlpCoeff = value;
    }

    public long[] getWarmup() {
        return warmup;
    }

    public void setWarmup(long[] value) {
        warmup = value;
    }

    public int[] getResidual() {
        return residual;
    }

    public void setResidual(int[] value) {
        residual = value;
    }

    public EntropyCodingMethod getEntropyCodingMethod() {
        return entropyCodingMethod;
    }

    public void setEntropyCodingMethod(EntropyCodingMethod value) {
        entropyCodingMethod = value;
    }
}
