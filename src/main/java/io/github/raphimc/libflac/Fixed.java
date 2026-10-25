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

final class Fixed {
    private Fixed() {
    }

    static int computeBestPredictor(long[] data, int dataOffset, int dataLen, float[] residualBitsPerSample) {
        int[] totalError = new int[Format.MAX_FIXED_ORDER + 1];
        for (int i = dataOffset; i < dataOffset + dataLen; i++) {
            totalError[0] += (int) Math.abs(data[i]);
            totalError[1] += (int) Math.abs(data[i] - data[i - 1]);
            totalError[2] += (int) Math.abs(data[i] - 2 * data[i - 1] + data[i - 2]);
            totalError[3] += (int) Math.abs(data[i] - 3 * data[i - 1] + 3 * data[i - 2] - data[i - 3]);
            totalError[4] += (int) Math
                    .abs(data[i] - 4 * data[i - 1] + 6 * data[i - 2] - 4 * data[i - 3] + data[i - 4]);
        }
        /* prefer lower order */
        int order = 0;
        for (int i = 1; i <= Format.MAX_FIXED_ORDER; i++)
            if (Integer.compareUnsigned(totalError[i], totalError[order]) < 0)
                order = i;
        /* Estimate the expected number of bits per residual signal sample. */
        /* 'totalError*' is linearly related to the variance of the residual */
        /* signal, so we use it directly to compute E(|x|) */
        for (int i = 0; i <= Format.MAX_FIXED_ORDER; i++) {
            long error = Integer.toUnsignedLong(totalError[i]);
            residualBitsPerSample[i] = error > 0 ? (float) (Math.log(Lpc.M_LN2 * error / dataLen) / Lpc.M_LN2) : 0;
        }
        return order;
    }

    static int computeBestPredictorWide(long[] data, int dataOffset, int dataLen, float[] residualBitsPerSample) {
        long[] totalError = new long[Format.MAX_FIXED_ORDER + 1];
        for (int i = dataOffset; i < dataOffset + dataLen; i++) {
            totalError[0] += Math.abs(data[i]);
            totalError[1] += Math.abs(data[i] - data[i - 1]);
            totalError[2] += Math.abs(data[i] - 2 * data[i - 1] + data[i - 2]);
            totalError[3] += Math.abs(data[i] - 3 * data[i - 1] + 3 * data[i - 2] - data[i - 3]);
            totalError[4] += Math.abs(data[i] - 4 * data[i - 1] + 6 * data[i - 2] - 4 * data[i - 3] + data[i - 4]);
        }
        /* prefer lower order */
        int order = 0;
        for (int i = 1; i <= Format.MAX_FIXED_ORDER; i++)
            if (Long.compareUnsigned(totalError[i], totalError[order]) < 0)
                order = i;
        /* Estimate the expected number of bits per residual signal sample. */
        /* 'totalError*' is linearly related to the variance of the residual */
        /* signal, so we use it directly to compute E(|x|) */
        for (int i = 0; i <= Format.MAX_FIXED_ORDER; i++) {
            long error = totalError[i];
            residualBitsPerSample[i] = error > 0 ? (float) (Math.log(Lpc.M_LN2 * error / dataLen) / Lpc.M_LN2) : 0;
        }
        return order;
    }

    static int computeBestPredictorLimitResidual(long[] data, int dataOffset, int dataLen,
            float[] residualBitsPerSample) {
        long[] totalError = new long[Format.MAX_FIXED_ORDER + 1];
        long[] error = new long[Format.MAX_FIXED_ORDER + 1];
        boolean[] orderIsValid = {true, true, true, true, true};
        for (int i = dataOffset - Format.MAX_FIXED_ORDER; i < dataOffset + dataLen; i++) {
            error[0] = Math.abs(data[i]);
            error[1] = i >= dataOffset - Format.MAX_FIXED_ORDER + 1 ? Math.abs(data[i] - data[i - 1]) : 0;
            error[2] = i >= dataOffset - Format.MAX_FIXED_ORDER + 2 ? Math.abs(data[i] - 2 * data[i - 1] + data[i - 2])
                    : 0;
            error[3] = i >= dataOffset - Format.MAX_FIXED_ORDER + 3
                    ? Math.abs(data[i] - 3 * data[i - 1] + 3 * data[i - 2] - data[i - 3]) : 0;
            error[4] = i >= dataOffset - Format.MAX_FIXED_ORDER + 4
                    ? Math.abs(data[i] - 4 * data[i - 1] + 6 * data[i - 2] - 4 * data[i - 3] + data[i - 4]) : 0;
            for (int order = 0; order <= Format.MAX_FIXED_ORDER; order++)
                totalError[order] += error[order];
            /* residual must not be Integer.MIN_VALUE */
            for (int order = 0; order <= Format.MAX_FIXED_ORDER; order++) {
                if (error[order] > Integer.MAX_VALUE)
                    orderIsValid[order] = false;
            }
        }
        int order = 0;
        long smallestError = -1L;

        for (int i = 0; i <= Format.MAX_FIXED_ORDER; i++) {
            if (orderIsValid[i] && Long.compareUnsigned(totalError[i], smallestError) < 0) {
                order = i;
                smallestError = totalError[i];
                residualBitsPerSample[i] = totalError[0] > 0
                        ? (float) (Math.log(Lpc.M_LN2 * totalError[0] / dataLen) / Lpc.M_LN2) : 0;
            } else
                residualBitsPerSample[i] = 34.0f;
        }
        return order;
    }

    /**
     * Fixed.computeResidual()
     *     --------------------------------------------------------------------
     *     Compute the residual signal obtained from sutracting the predicted
     *     signal from the original.
     *
     * <pre>
     *     IN data[dataOffset-order,dataOffset+dataLen-1]        original signal (NOTE THE INDICES!)
     *     IN dataLen                       length of original signal
     *     IN order &lt;= Format.MAX_FIXED_ORDER fixed-predictor order
     *     OUT residual[0,dataLen-1]        residual signal
     * </pre>
     */
    static void computeResidual(long[] data, int dataOffset, int dataLen, int order, int[] residual) {
        switch (order) {
            case 0 -> {
                for (int i = 0; i < dataLen; i++)
                    residual[i] = (int) (data[dataOffset + i]);
            }
            case 1 -> {
                for (int i = 0; i < dataLen; i++)
                    residual[i] = (int) (data[dataOffset + i] - data[dataOffset + i - 1]);
            }
            case 2 -> {
                for (int i = 0; i < dataLen; i++)
                    residual[i] = (int) (data[dataOffset + i] - 2 * data[dataOffset + i - 1]
                            + data[dataOffset + i - 2]);
            }
            case 3 -> {
                for (int i = 0; i < dataLen; i++)
                    residual[i] = (int) (data[dataOffset + i] - 3 * data[dataOffset + i - 1]
                            + 3 * data[dataOffset + i - 2] - data[dataOffset + i - 3]);
            }
            case 4 -> {
                for (int i = 0; i < dataLen; i++)
                    residual[i] = (int) (data[dataOffset + i] - 4 * data[dataOffset + i - 1]
                            + 6 * data[dataOffset + i - 2] - 4 * data[dataOffset + i - 3] + data[dataOffset + i - 4]);
            }
            default -> throw new IllegalArgumentException();
        }
    }

    /**
     * Fixed.restoreSignal()
     *     --------------------------------------------------------------------
     *     Restore the original signal by summing the residual and the
     *     predictor.
     *
     * <pre>
     *     IN residual[0,dataLen-1]         residual signal
     *     IN dataLen                       length of original signal
     *     IN order &lt;= Format.MAX_FIXED_ORDER fixed-predictor order
     *     *** IMPORTANT: the caller must pass in the historical samples:
     *     IN  data[dataOffset-order,dataOffset-1]               previously-reconstructed historical samples
     *     OUT data[dataOffset,dataOffset+dataLen-1]            original signal
     * </pre>
     */
    static void restoreSignal(int[] residual, int dataLen, int order, long[] data, int dataOffset) {
        switch (order) {
            case 0 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = residual[i];
            }
            case 1 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = (int) (residual[i] + data[dataOffset + i - 1]);
            }
            case 2 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset
                            + i] = (int) (residual[i] + 2 * data[dataOffset + i - 1] - data[dataOffset + i - 2]);
            }
            case 3 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = (int) (residual[i] + 3 * data[dataOffset + i - 1]
                            - 3 * data[dataOffset + i - 2] + data[dataOffset + i - 3]);
            }
            case 4 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = (int) (residual[i] + 4 * data[dataOffset + i - 1]
                            - 6 * data[dataOffset + i - 2] + 4 * data[dataOffset + i - 3] - data[dataOffset + i - 4]);
            }
            default -> throw new IllegalArgumentException();
        }
    }

    static void restoreSignalWide33Bit(int[] residual, int dataLen, int order, long[] data, int dataOffset) {
        switch (order) {
            case 0 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = residual[i];
            }
            case 1 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = residual[i] + data[dataOffset + i - 1];
            }
            case 2 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = residual[i] + 2 * data[dataOffset + i - 1] - data[dataOffset + i - 2];
            }
            case 3 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = residual[i] + 3 * data[dataOffset + i - 1] - 3 * data[dataOffset + i - 2]
                            + data[dataOffset + i - 3];
            }
            case 4 -> {
                for (int i = 0; i < dataLen; i++)
                    data[dataOffset + i] = residual[i] + 4 * data[dataOffset + i - 1] - 6 * data[dataOffset + i - 2]
                            + 4 * data[dataOffset + i - 3] - data[dataOffset + i - 4];
            }
            default -> throw new IllegalArgumentException();
        }
    }

}
