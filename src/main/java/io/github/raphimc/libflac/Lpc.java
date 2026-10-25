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

final class Lpc {
    static final double M_LN2 = 0.69314718055994530942;

    private static final int max_shiftlimit = (1 << (Format.SUBFRAME_LPC_QLP_SHIFT_LEN - 1)) - 1;
    private static final int min_shiftlimit = -max_shiftlimit - 1;

    private Lpc() {
    }

    /**
     * Lpc.windowData()
     *     --------------------------------------------------------------------
     *     Applies the given window to the data.
     *
     * <pre>
     *     IN in[0,dataLen-1]
     *     IN window[0,dataLen-1]
     *     OUT out[0,lag-1]
     *     IN dataLen
     * </pre>
     */
    static void windowData(long[] in, float[] window, float[] out, int dataLen) {
        for (int i = 0; i < dataLen; i++)
            out[i] = (float) in[i] * window[i];
    }

    static void windowDataPartial(long[] in, float[] window, float[] out, int dataLen, int partSize, int dataShift) {
        if (partSize + dataShift < dataLen) {
            int i = 0;
            for (; i < partSize; i++)
                out[i] = (float) in[dataShift + i] * window[i];
            i = Math.min(i, dataLen - partSize - dataShift);
            for (int j = dataLen - partSize; j < dataLen; i++, j++)
                out[i] = (float) in[dataShift + i] * window[j];
            if (i < dataLen)
                out[i] = 0;
        }
    }

    /**
     * Lpc.computeAutocorrelation()
     *     --------------------------------------------------------------------
     *     Compute the autocorrelation for lags between 0 and lag-1.
     *     Assumes data[] outside of [0,dataLen-1] == 0.
     *
     * <pre>
     *     IN data[0,dataLen-1]
     *     IN dataLen
     *     IN 0 &lt; lag &lt;= dataLen
     *     OUT autoc[0,lag-1]
     * </pre>
     */

    /*
     * Technically we should subtract the mean first like so:
     *   for(i = 0; i < dataLen; i++)
     *     data[i] -= mean;
     * but it appears not to make enough of a difference to matter, and
     * most signals are already closely centered around zero
     */
    static double[] computeAutocorrelation(float[] data, int dataLen, int lag) {
        double[] autoc = new double[Format.MAX_LPC_ORDER + 1];
        if (dataLen < Format.MAX_LPC_ORDER || lag > 16) {
            /*
             * this version tends to run faster because of better data locality
             * ('dataLen' is usually much larger than 'lag')
             */
            int limit = dataLen - lag, sample = 0;
            for (; sample <= limit; sample++) {
                double d = data[sample];
                for (int coeff = 0; coeff < lag; coeff++)
                    autoc[coeff] += d * data[sample + coeff];
            }
            for (; sample < dataLen; sample++) {
                double d = data[sample];
                for (int coeff = 0; coeff < dataLen - sample; coeff++)
                    autoc[coeff] += d * data[sample + coeff];
            }
        } else {
            int maxLag = lag <= 8 ? 8 : lag <= 12 ? 12 : 16;
            for (int i = 0; i < maxLag; i++)
                for (int j = 0; j <= i; j++)
                    autoc[j] += (double) data[i] * data[i - j];
            for (int i = maxLag; i < dataLen; i++)
                for (int j = 0; j < maxLag; j++)
                    autoc[j] += (double) data[i] * data[i - j];
        }
        return autoc;
    }

    /**
     * Lpc.computeLpCoefficients()
     *     --------------------------------------------------------------------
     *     Computes LP coefficients for orders 1..maxOrder.
     *     Do not call if autoc[0] == 0.0.  This means the signal is zero
     *     and there is no point in calculating a predictor.
     *
     * <pre>
     *     IN autoc[0,maxOrder]                      autocorrelation values
     *     IN 0 &lt; maxOrder &lt;= Format.MAX_LPC_ORDER    max LP order to compute
     *     OUT lpCoeff[0,maxOrder-1][0,maxOrder-1] LP coefficients for each order
     *     *** IMPORTANT:
     *     *** lpCoeff[0,maxOrder-1][maxOrder,Format.MAX_LPC_ORDER-1] are untouched
     *     OUT error[0,maxOrder-1]                   error for each order (more
     *                                                specifically, the variance of
     *                                                the error signal times # of
     *                                                samples in the signal)
     *
     *     Example: if maxOrder is 9, the LP coefficients for order 9 will be
     *              in lpCoeff[8][0,8], the LP coefficients for order 8 will be
     *              in lpCoeff[7][0,7], etc.
     *
     * </pre>
     * @return The maximum order calculated.
     */
    static int computeLpCoefficients(double[] autoc, int maxOrder, float[][] lpCoeff, double[] error) {
        double err = autoc[0];
        double[] lpc = new double[Format.MAX_LPC_ORDER];
        for (int i = 0; i < maxOrder; i++) {
            /* Sum up this iteration's reflection coefficient. */
            double r = -autoc[i + 1];
            for (int j = 0; j < i; j++)
                r -= lpc[j] * autoc[i - j];
            r /= err;
            /* Update LPC coefficients and total error. */
            lpc[i] = r;
            int j = 0;
            for (; j < (i >> 1); j++) {
                double tmp = lpc[j];
                lpc[j] += r * lpc[i - 1 - j];
                lpc[i - 1 - j] += r * tmp;
            }
            if ((i & 1) != 0)
                lpc[j] += lpc[j] * r;
            err *= 1.0 - r * r;
            /* save this order */
            for (j = 0; j <= i; j++)
                /* negate FIR filter coeff to get predictor coeff */
                lpCoeff[i][j] = (float) -lpc[j];
            error[i] = err;
            /* see SF bug https://sourceforge.net/p/flac/bugs/234/ */
            if (err == 0.0)
                return i + 1;
        }
        return maxOrder;
    }

    /**
     * Lpc.quantizeCoefficients()
     *     --------------------------------------------------------------------
     *     Quantizes the LP coefficients.  NOTE: precision + bitsPerSample
     * must be less than 32 (Integer.SIZE).
     *
     * <pre>
     *     IN lpCoeff[0,order-1]    LP coefficients
     *     IN order                  LP order
     *     IN Format.MIN_QLP_COEFF_PRECISION &lt; precision
     *                               desired precision (in bits, including sign
     *                               bit) of largest coefficient
     *     OUT qlpCoeff[0,order-1]  quantized coefficients
     *     RETURN the nonnegative shift if quantization OK; min_shiftlimit - 1 if
     *        coefficients require too much shifting to fit in the LPC subframe header,
     *        or coefficients are all zero.
     * </pre>
     */
    static int quantizeCoefficients(float[] lpCoeff, int order, int precision, int[] qlpCoeff) {
        /* drop one bit for the sign; from here on out we consider only |lpCoeff[i]| */
        precision--;
        int qmax = 1 << precision, qmin = -qmax;
        qmax--;
        double cmax = 0;
        /* calc cmax = max( |lpCoeff[i]| ) */
        for (int i = 0; i < order; i++)
            cmax = Math.max(cmax, Math.abs((double) lpCoeff[i]));
        /* => coefficients are all 0, which means our constant-detect didn't work */
        if (!(cmax > 0) || !Double.isFinite(cmax))
            return min_shiftlimit - 1;
        int log2cmax = Math.getExponent(cmax);
        int shift = precision - log2cmax - 1;
        if (shift > max_shiftlimit)
            shift = max_shiftlimit;
        else if (shift < min_shiftlimit)
            return min_shiftlimit - 1;
        if (shift >= 0) {
            double error = 0;
            for (int i = 0; i < order; i++) {
                error += lpCoeff[i] * (1 << shift);
                long rounded = Math.round(error);
                int q = (int) Math.max(qmin, Math.min(qmax, rounded));
                error -= q;
                qlpCoeff[i] = q;
            }
        }
        /*
         * negative shift is very rare but due to design flaw, negative shift is
         * not allowed in the decoder, so it must be handled specially by scaling
         * down coeffs
         */
        else {
            int nshift = -shift;
            double error = 0;
            for (int i = 0; i < order; i++) {
                error += lpCoeff[i] / (1 << nshift);
                long rounded = Math.round(error);
                int q = (int) Math.max(qmin, Math.min(qmax, rounded));
                error -= q;
                qlpCoeff[i] = q;
            }
            shift = 0;
        }
        return shift;
    }

    /**
     * Lpc.computeResidualFromQlpCoefficients()
     *     --------------------------------------------------------------------
     *     Compute the residual signal obtained from sutracting the predicted
     *     signal from the original.
     *
     * <pre>
     *     IN data[dataOffset-order,dataOffset+dataLen-1] original signal (NOTE THE INDICES!)
     *     IN dataLen                length of original signal
     *     IN qlpCoeff[0,order-1]    quantized LP coefficients
     *     IN order &gt; 0               LP order
     *     IN lpQuantization         quantization of LP coefficients in bits
     *     OUT residual[0,dataLen-1] residual signal
     * </pre>
     */
    static void computeResidualFromQlpCoefficients(long[] data, int dataOffset, int dataLen, int[] qlpCoeff, int order,
            int lpQuantization, int[] residual) {
        for (int i = 0; i < dataLen; i++) {
            int sum = 0;
            for (int j = 0; j < order; j++)
                sum += qlpCoeff[j] * (int) data[dataOffset + i - j - 1];
            residual[i] = (int) data[dataOffset + i] - (sum >> lpQuantization);
        }
    }

    static void computeResidualFromQlpCoefficientsWide(long[] data, int dataOffset, int dataLen, int[] qlpCoeff,
            int order, int lpQuantization, int[] residual) {
        for (int i = 0; i < dataLen; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++)
                sum += (long) qlpCoeff[j] * (int) data[dataOffset + i - j - 1];
            residual[i] = (int) ((int) data[dataOffset + i] - (sum >> lpQuantization));
        }
    }

    static boolean computeResidualFromQlpCoefficientsLimitResidual(long[] data, int dataOffset, int dataLen,
            int[] qlpCoeff, int order, int lpQuantization, int[] residual) {
        for (int i = 0; i < dataLen; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++)
                sum += (long) qlpCoeff[j] * (int) data[dataOffset + i - j - 1];
            long residualToCheck = (int) data[dataOffset + i] - (sum >> lpQuantization);
            /* residual must not be Integer.MIN_VALUE */
            if (residualToCheck <= Integer.MIN_VALUE || residualToCheck > Integer.MAX_VALUE)
                return false;
            residual[i] = (int) residualToCheck;
        }
        return true;
    }

    static boolean computeResidualFromQlpCoefficientsLimitResidual33Bit(long[] data, int dataOffset, int dataLen,
            int[] qlpCoeff, int order, int lpQuantization, int[] residual) {
        for (int i = 0; i < dataLen; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++)
                sum += (long) qlpCoeff[j] * data[dataOffset + i - j - 1];
            long residualToCheck = data[dataOffset + i] - (sum >> lpQuantization);
            /* residual must not be Integer.MIN_VALUE */
            if (residualToCheck <= Integer.MIN_VALUE || residualToCheck > Integer.MAX_VALUE)
                return false;
            residual[i] = (int) residualToCheck;
        }
        return true;
    }

    static int maxPredictionBeforeShiftBps(int subframeBps, int[] qlpCoeff, int order) {
        int sum = 0;
        /*
         * This used to be subframeBps + qlpCoeffPrecision + (Integer.SIZE - 1 - Integer.numberOfLeadingZeros(order))
         * but that treats both the samples as well as the predictor as unknown. The
         * predictor is known however, so taking the log2 of the sum of the absolute values
         * of all coefficients is a more accurate representation of the predictor
         */
        for (int i = 0; i < order; i++)
            sum += Math.abs(qlpCoeff[i]);
        if (sum == 0)
            sum = 1;
        return subframeBps + (Integer.SIZE + 1) - Integer.numberOfLeadingZeros(sum);
    }

    static int maxResidualBps(int subframeBps, int[] qlpCoeff, int order, int lpQuantization) {
        int predictionBps = maxPredictionBeforeShiftBps(subframeBps, qlpCoeff, order) - lpQuantization;
        return Math.max(subframeBps, predictionBps) + 1;
    }

    /**
     * Lpc.restoreSignal()
     *     --------------------------------------------------------------------
     *     Restore the original signal by summing the residual and the
     *     predictor.
     *
     * <pre>
     *     IN residual[0,dataLen-1]  residual signal
     *     IN dataLen                length of original signal
     *     IN qlpCoeff[0,order-1]    quantized LP coefficients
     *     IN order &gt; 0               LP order
     *     IN lpQuantization         quantization of LP coefficients in bits
     *     *** IMPORTANT: the caller must pass in the historical samples:
     *     IN  data[dataOffset-order,dataOffset-1]        previously-reconstructed historical samples
     *     OUT data[dataOffset,dataOffset+dataLen-1]     original signal
     * </pre>
     */
    static void restoreSignal(int[] residual, int dataLen, int[] qlpCoeff, int order, int lpQuantization, long[] data,
            int dataOffset) {
        for (int i = 0; i < dataLen; i++) {
            int sum = 0;
            for (int j = 0; j < order; j++)
                sum += qlpCoeff[j] * (int) data[dataOffset + i - j - 1];
            data[dataOffset + i] = residual[i] + (sum >> lpQuantization);
        }
    }

    static void restoreSignalWide(int[] residual, int dataLen, int[] qlpCoeff, int order, int lpQuantization,
            long[] data, int dataOffset) {
        for (int i = 0; i < dataLen; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++)
                sum += (long) qlpCoeff[j] * (int) data[dataOffset + i - j - 1];
            data[dataOffset + i] = (int) (residual[i] + (sum >> lpQuantization));
        }
    }

    static void restoreSignalWide33Bit(int[] residual, int dataLen, int[] qlpCoeff, int order, int lpQuantization,
            long[] data, int dataOffset) {
        for (int i = 0; i < dataLen; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++)
                sum += (long) qlpCoeff[j] * data[dataOffset + i - j - 1];
            data[dataOffset + i] = residual[i] + (sum >> lpQuantization);
        }
    }

    /**
     * Lpc.computeExpectedBitsPerResidualSample()
     *     --------------------------------------------------------------------
     *     Compute the expected number of bits per residual signal sample
     *     based on the LP error (which is related to the residual variance).
     *
     * <pre>
     *     IN lpcError &gt;= 0.0   error returned from calculating LP coefficients
     *     IN totalSamples &gt; 0  # of samples in residual signal
     *     RETURN                expected bits per sample
     * </pre>
     */
    static double computeExpectedBitsPerResidualSample(double lpcError, int totalSamples) {
        double errorScale = 0.5 / totalSamples;
        return computeExpectedBitsPerResidualSampleWithErrorScale(lpcError, errorScale);
    }

    static double computeExpectedBitsPerResidualSampleWithErrorScale(double lpcError, double errorScale) {
        if (lpcError > 0) {
            double bps = 0.5 * Math.log(errorScale * lpcError) / M_LN2;
            if (bps >= 0)
                return bps;
            else
                return 0;
        } else if (lpcError < 0) {
            /* error should not be negative but can happen due to inadequate floating-point resolution */
            return 1e32;
        } else {
            return 0;
        }
    }

    /**
     * Lpc.computeBestOrder()
     *     --------------------------------------------------------------------
     *     Compute the best order from the array of signal errors returned
     *     during coefficient computation.
     *
     * <pre>
     *     IN lpcError[0,maxOrder-1] &gt;= 0.0  error returned from calculating LP coefficients
     *     IN maxOrder &gt; 0                    max LP order
     *     IN totalSamples &gt; 0                # of samples in residual signal
     *     IN overheadBitsPerOrder          # of bits overhead for each increased LP order
     *                                         (includes warmup sample size and quantized LP coefficient)
     *     RETURN [1,maxOrder]                best order
     * </pre>
     */
    static int computeBestOrder(double[] lpcError, int maxOrder, int totalSamples, int overheadBitsPerOrder) {
        double errorScale = 0.5 / totalSamples;
        int bestIndex = 0;
        double bestBits = 0xffffffffL;
        /* 'index' the index into lpcError; index==order-1 since lpcError[0] is for order==1, lpcError[1] is for order==2, etc */
        for (int indx = 0, order = 1; indx < maxOrder; indx++, order++) {
            double bits = computeExpectedBitsPerResidualSampleWithErrorScale(lpcError[indx], errorScale)
                    * (totalSamples - order) + Integer.toUnsignedLong(order * overheadBitsPerOrder);
            if (bits < bestBits) {
                bestIndex = indx;
                bestBits = bits;
            }
        }
        /* +1 since indx of lpcError[] is order-1 */
        return bestIndex + 1;
    }
}
