/* replaygain_synthesis - Routines for applying ReplayGain to a signal
 * Copyright (C) 2002-2009  Josh Coalson
 * Copyright (C) 2011-2023  Xiph.Org Foundation
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
/*
 * This is an aggregation of pieces of code from John Edwards' WaveGain
 * program.  Mostly cosmetic changes were made; otherwise, the dithering
 * code is almost untouched and the gain processing was converted from
 * processing a whole file to processing chunks of samples.
 *
 * The original copyright notices for WaveGain's dither.c and wavegain.c
 * appear below:
 */
/*
 * (c) 2002 John Edwards
 * mostly lifted from work by Frank Klemm
 * random functions for dithering.
 */
/*
 * Copyright (C) 2002 John Edwards
 * Additional code by Magnus Holmgren and Gian-Carlo Pascutto
 */

package io.github.raphimc.libflac.replaygain;

import java.util.Arrays;
import java.util.Objects;
import io.github.raphimc.libflac.Format;

public final class ReplayGainSynthesis {

    public static final int MAX_SUPPORTED_CHANNELS = Format.MAX_CHANNELS;

    private static final int[] parity_ = {0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1,
            1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1,
            1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1,
            1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1,
            1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0,
            0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1,
            1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1,
            1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0};

    private int r1 = 1, r2 = 1;

    /*
     * This is a simple random number generator with good quality for audio purposes.
     *  It consists of two polycounters with opposite rotation direction and different
     *  periods. The periods are coprime, so the total period is the product of both.
     *
     *     -------------------------------------------------------------------------------------------------
     * +-> |31:30:29:28:27:26:25:24:23:22:21:20:19:18:17:16:15:14:13:12:11:10: 9: 8: 7: 6: 5: 4: 3: 2: 1: 0|
     * |   -------------------------------------------------------------------------------------------------
     * |                                                                          |  |  |  |     |        |
     * |                                                                          +--+--+--+-XOR-+--------+
     * |                                                                                      |
     * +--------------------------------------------------------------------------------------+
     *
     *     -------------------------------------------------------------------------------------------------
     *     |31:30:29:28:27:26:25:24:23:22:21:20:19:18:17:16:15:14:13:12:11:10: 9: 8: 7: 6: 5: 4: 3: 2: 1: 0| <-+
     *     -------------------------------------------------------------------------------------------------   |
     *       |  |           |  |                                                                               |
     *       +--+----XOR----+--+                                                                               |
     *                |                                                                                        |
     *                +----------------------------------------------------------------------------------------+
     *
     *  The first has an period of 3*5*17*257*65537, the second of 7*47*73*178481,
     *  which gives a period of 18.410.713.077.675.721.215. The result is the
     *  XORed values of both generators.
     */
    private int randomInt() {
        /* Parity calculation is done via table lookup. */
        int t1 = parity_[r1 & 0xf5] << 31, t2 = parity_[(r2 >>> 25) & 0x63];
        r1 = (r1 >>> 1) | t1;
        r2 = (r2 + r2) | t2;
        return r1 ^ r2;
    }

    /* gives a equal distributed random number */

    /* between -2^31*mult and +2^31*mult */
    private double randomEqui(double mult) {
        return mult * randomInt();
    }

    /* gives a triangular distributed random number */

    /* between -2^32*mult and +2^32*mult */
    private double randomTriangular(double mult) {
        return mult * ((double) randomInt() + (double) randomInt());
    }

    private static final float[] F44_0 = new float[16 + 32];
    /** SNR(w) = 4.843163 dB, SNR = -3.192134 dB */
    private static final float[] F44_1 = {(float) 0.85018292704024355931, (float) 0.29089597350995344721,
            (float) -0.05021866022121039450, (float) -0.23545456294599161833, (float) -0.58362726442227032096,
            (float) -0.67038978965193036429, (float) -0.38566861572833459221, (float) -0.15218663390367969967,
            (float) -0.02577543084864530676, (float) 0.14119295297688728127, (float) 0.22398848581628781612,
            (float) 0.15401727203382084116, (float) 0.05216161232906000929, (float) -0.00282237820999675451,
            (float) -0.03042794608323867363, (float) -0.03109780942998826024, (float) 0.85018292704024355931,
            (float) 0.29089597350995344721, (float) -0.05021866022121039450, (float) -0.23545456294599161833,
            (float) -0.58362726442227032096, (float) -0.67038978965193036429, (float) -0.38566861572833459221,
            (float) -0.15218663390367969967, (float) -0.02577543084864530676, (float) 0.14119295297688728127,
            (float) 0.22398848581628781612, (float) 0.15401727203382084116, (float) 0.05216161232906000929,
            (float) -0.00282237820999675451, (float) -0.03042794608323867363, (float) -0.03109780942998826024,
            (float) 0.85018292704024355931, (float) 0.29089597350995344721, (float) -0.05021866022121039450,
            (float) -0.23545456294599161833, (float) -0.58362726442227032096, (float) -0.67038978965193036429,
            (float) -0.38566861572833459221, (float) -0.15218663390367969967, (float) -0.02577543084864530676,
            (float) 0.14119295297688728127, (float) 0.22398848581628781612, (float) 0.15401727203382084116,
            (float) 0.05216161232906000929, (float) -0.00282237820999675451, (float) -0.03042794608323867363,
            (float) -0.03109780942998826024,};
    /** SNR(w) = 10.060213 dB, SNR = -12.766730 dB */
    private static final float[] F44_2 = {(float) 1.78827593892108555290, (float) 0.95508210637394326553,
            (float) -0.18447626783899924429, (float) -0.44198126506275016437, (float) -0.88404052492547413497,
            (float) -1.42218907262407452967, (float) -1.02037566838362314995, (float) -0.34861755756425577264,
            (float) -0.11490230170431934434, (float) 0.12498899339968611803, (float) 0.38065885268563131927,
            (float) 0.31883491321310506562, (float) 0.10486838686563442765, (float) -0.03105361685110374845,
            (float) -0.06450524884075370758, (float) -0.02939198261121969816, (float) 1.78827593892108555290,
            (float) 0.95508210637394326553, (float) -0.18447626783899924429, (float) -0.44198126506275016437,
            (float) -0.88404052492547413497, (float) -1.42218907262407452967, (float) -1.02037566838362314995,
            (float) -0.34861755756425577264, (float) -0.11490230170431934434, (float) 0.12498899339968611803,
            (float) 0.38065885268563131927, (float) 0.31883491321310506562, (float) 0.10486838686563442765,
            (float) -0.03105361685110374845, (float) -0.06450524884075370758, (float) -0.02939198261121969816,
            (float) 1.78827593892108555290, (float) 0.95508210637394326553, (float) -0.18447626783899924429,
            (float) -0.44198126506275016437, (float) -0.88404052492547413497, (float) -1.42218907262407452967,
            (float) -1.02037566838362314995, (float) -0.34861755756425577264, (float) -0.11490230170431934434,
            (float) 0.12498899339968611803, (float) 0.38065885268563131927, (float) 0.31883491321310506562,
            (float) 0.10486838686563442765, (float) -0.03105361685110374845, (float) -0.06450524884075370758,
            (float) -0.02939198261121969816,};
    /** SNR(w) = 15.382598 dB, SNR = -29.402334 dB */
    private static final float[] F44_3 = {(float) 2.89072132015058161445, (float) 2.68932810943698754106,
            (float) 0.21083359339410251227, (float) -0.98385073324997617515, (float) -1.11047823227097316719,
            (float) -2.18954076314139673147, (float) -2.36498032881953056225, (float) -0.95484132880101140785,
            (float) -0.23924057925542965158, (float) -0.13865235703915925642, (float) 0.43587843191057992846,
            (float) 0.65903257226026665927, (float) 0.24361815372443152787, (float) -0.00235974960154720097,
            (float) 0.01844166574603346289, (float) 0.01722945988740875099, (float) 2.89072132015058161445,
            (float) 2.68932810943698754106, (float) 0.21083359339410251227, (float) -0.98385073324997617515,
            (float) -1.11047823227097316719, (float) -2.18954076314139673147, (float) -2.36498032881953056225,
            (float) -0.95484132880101140785, (float) -0.23924057925542965158, (float) -0.13865235703915925642,
            (float) 0.43587843191057992846, (float) 0.65903257226026665927, (float) 0.24361815372443152787,
            (float) -0.00235974960154720097, (float) 0.01844166574603346289, (float) 0.01722945988740875099,
            (float) 2.89072132015058161445, (float) 2.68932810943698754106, (float) 0.21083359339410251227,
            (float) -0.98385073324997617515, (float) -1.11047823227097316719, (float) -2.18954076314139673147,
            (float) -2.36498032881953056225, (float) -0.95484132880101140785, (float) -0.23924057925542965158,
            (float) -0.13865235703915925642, (float) 0.43587843191057992846, (float) 0.65903257226026665927,
            (float) 0.24361815372443152787, (float) -0.00235974960154720097, (float) 0.01844166574603346289,
            (float) 0.01722945988740875099};

    private static final int[] default_dither = {92, 92, 88, 84, 81, 78, 74, 67, 0, 0};
    private static final float[][] F = {F44_0, F44_1, F44_2, F44_3};
    private static final long[] hard_clip_factors_ = {/* 0 bits-per-sample (not supported) */ 0,
            /* 1 bits-per-sample (not supported) */ 0, /* 2 bits-per-sample (not supported) */ 0,
            /* 3 bits-per-sample (not supported) */ 0, /* 4 bits-per-sample */ -8, /* 5 bits-per-sample */ -16,
            /* 6 bits-per-sample */ -32, /* 7 bits-per-sample */ -64, /* 8 bits-per-sample */ -128,
            /* 9 bits-per-sample */ -256, /* 10 bits-per-sample */ -512, /* 11 bits-per-sample */ -1024,
            /* 12 bits-per-sample */ -2048, /* 13 bits-per-sample */ -4096, /* 14 bits-per-sample */ -8192,
            /* 15 bits-per-sample */ -16384, /* 16 bits-per-sample */ -32768, /* 17 bits-per-sample */ -65536,
            /* 18 bits-per-sample */ -131072, /* 19 bits-per-sample */ -262144, /* 20 bits-per-sample */ -524288,
            /* 21 bits-per-sample */ -1048576, /* 22 bits-per-sample */ -2097152, /* 23 bits-per-sample */ -4194304,
            /* 24 bits-per-sample */ -8388608, /* 25 bits-per-sample */ -16777216, /* 26 bits-per-sample */ -33554432,
            /* 27 bits-per-sample */ -67108864, /* 28 bits-per-sample */ -134217728,
            /* 29 bits-per-sample */ -268435456, /* 30 bits-per-sample */ -536870912,
            /* 31 bits-per-sample */ -1073741824, /* 32 bits-per-sample */ -1073741824L * 2};

    private static double scalar16(float[] x, float[] y, int offset) {

        float result = x[0] * y[offset];
        for (int i = 1; i < 16; i++)
            result += x[i] * y[offset + i];
        return result;
    }

    public static void initDitherContext(DitherContext ditherContext, int bits, int shapingType) {
        if (bits < Format.MIN_BITS_PER_SAMPLE || bits > Integer.SIZE)
            throw new IllegalArgumentException("bit depth");
        shapingType = Math.max(0, Math.min(3, shapingType));
        ditherContext.shapingType = NoiseShaping.values()[shapingType];
        int index = Math.max(0, Math.min(9, bits - 11 - shapingType));
        for (float[] row : ditherContext.errorHistory)
            Arrays.fill(row, 0);
        for (float[] row : ditherContext.ditherHistory)
            Arrays.fill(row, 0);
        ditherContext.filterCoeff = F[shapingType].clone();
        ditherContext.mask = -1L << (Integer.SIZE - bits);
        ditherContext.add = 0.5 * ((1L << (Integer.SIZE - bits)) - 1);
        ditherContext.dither = 0.01f * default_dither[index] / (1L << bits);
        ditherContext.lastHistoryIndex = 0;
    }

    private static long round64(DitherContext ditherContext, double x) {
        return (long) Math.rint(x + ditherContext.add);
    }

    /* the following is based on parts of wavegain.c */
    private long ditherOutput(DitherContext ditherContext, boolean doDithering, int shapingType, int i, double sum,
            int channel) {
        if (doDithering) {
            double sum2;
            long val;
            if (shapingType == 0) {
                double tmp = randomEqui(ditherContext.dither);
                sum2 = tmp - ditherContext.lastRandomNumber[channel];
                ditherContext.lastRandomNumber[channel] = (int) tmp;
                sum2 = sum += sum2;
                val = round64(ditherContext, sum2) & ditherContext.mask;
            } else {
                sum2 = randomTriangular(ditherContext.dither)
                        - scalar16(ditherContext.ditherHistory[channel], ditherContext.filterCoeff, i);
                ditherContext.ditherHistory[channel][(-1 - i) & 15] = (float) sum2;
                sum += ditherContext.ditherHistory[channel][(-1 - i) & 15];
                sum2 = sum + scalar16(ditherContext.errorHistory[channel], ditherContext.filterCoeff, i);
                val = round64(ditherContext, sum2) & ditherContext.mask;
                ditherContext.errorHistory[channel][(-1 - i) & 15] = (float) (sum - val);
            }
            return val;
        }
        return round64(ditherContext, sum);
    }

    /** scale = (float) Math.pow(10., (double)replaygain * 0.05); */
    public int applyGain(byte[] dataOut, boolean littleEndianDataOut, boolean unsignedDataOut, int[][] input,
            int wideSamples, int channels, int sourceBps, int targetBps, double scale, boolean hardLimit,
            boolean doDithering, DitherContext ditherContext) {
        if (channels < 1 || channels > MAX_SUPPORTED_CHANNELS || sourceBps < Format.MIN_BITS_PER_SAMPLE
                || sourceBps > Integer.SIZE || targetBps < Byte.SIZE || targetBps >= Integer.SIZE
                || (targetBps & 7) != 0 || wideSamples < 0)
            throw new IllegalArgumentException("PCM format");
        int bytesPerSample = targetBps / Byte.SIZE,
                outputByteCount = Math.multiplyExact(Math.multiplyExact(wideSamples, channels), bytesPerSample);
        Objects.checkFromIndexSize(0, outputByteCount, dataOut.length);
        int convShift = Integer.SIZE - targetBps;
        long hardClipFactor = hard_clip_factors_[targetBps];
        /*
         * The integer input coming in has a varying range based on the
         * sourceBps.  We want to normalize it to [-1.0, 1.0) so instead
         * of doing two multiplies on each sample, we just multiple
         * 'scale' by 1/(2^(sourceBps-1))
         */
        double multiScale = scale / (1L << (sourceBps - 1));
        int lastHistoryIndex = ditherContext.lastHistoryIndex;
        NoiseShaping noiseShaping = ditherContext.shapingType;
        int twiggle = 1 << (targetBps - 1);
        for (int channel = 0; channel < channels; channel++) {
            int incr = bytesPerSample * channels;
            int outputOffset = bytesPerSample * channel;
            int[] input_ = input[channel];
            for (int i = 0; i < wideSamples; i++, outputOffset += incr) {
                double sample = input_[i] * multiScale;
                if (hardLimit) {
                    /* hard 6dB limiting */
                    if (sample < -0.5)
                        sample = Math.tanh((sample + 0.5) / (1 - 0.5)) * (1 - 0.5) - 0.5;
                    else if (sample > 0.5)
                        sample = Math.tanh((sample - 0.5) / (1 - 0.5)) * (1 - 0.5) + 0.5;
                }
                sample *= Integer.MAX_VALUE;
                long val64 = ditherOutput(ditherContext, doDithering, noiseShaping.ordinal(),
                        (i + lastHistoryIndex) % 32, sample, channel) >> convShift;
                int val32 = (int) val64;
                if (val64 >= -hardClipFactor)
                    val32 = (int) -(hardClipFactor + 1);
                else if (val64 < hardClipFactor)
                    val32 = (int) hardClipFactor;
                int uval32 = val32;
                if (unsignedDataOut)
                    uval32 ^= twiggle;
                if (littleEndianDataOut) {
                    switch (targetBps) {
                        case 24 -> {
                            dataOut[outputOffset + 2] = (byte) (uval32 >> 16);
                            dataOut[outputOffset + 1] = (byte) (uval32 >> 8);
                            dataOut[outputOffset] = (byte) uval32;
                        }
                        case 16 -> {
                            dataOut[outputOffset + 1] = (byte) (uval32 >> 8);
                            dataOut[outputOffset] = (byte) uval32;
                        }
                        case 8 -> dataOut[outputOffset] = (byte) uval32;
                    }
                } else {
                    switch (targetBps) {
                        case 24 -> {
                            dataOut[outputOffset] = (byte) (uval32 >> 16);
                            dataOut[outputOffset + 1] = (byte) (uval32 >> 8);
                            dataOut[outputOffset + 2] = (byte) uval32;
                        }
                        case 16 -> {
                            dataOut[outputOffset] = (byte) (uval32 >> 8);
                            dataOut[outputOffset + 1] = (byte) uval32;
                        }
                        case 8 -> dataOut[outputOffset] = (byte) uval32;
                    }
                }
            }
        }
        ditherContext.lastHistoryIndex = (lastHistoryIndex + wideSamples) % 32;
        return outputByteCount;
    }
}
