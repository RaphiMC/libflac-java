/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2006-2009  Josh Coalson
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

import java.util.Arrays;

final class Window {
    private Window() {
    }

    private static float cosf(double x) {
        return (float) Math.cos((float) x);
    }

    /**
     * Window.*()
     *     --------------------------------------------------------------------
     *     Calculates window coefficients according to different apodization
     *     functions.
     *
     *     OUT window[0,L-1]
     *     IN L (number of points in window)
     */
    static void bartlett(float[] window, final int L) {
        final int N = L - 1;
        int n;

        if ((L & 1) != 0) {
            for (n = 0; n <= N / 2; n++)
                window[n] = 2.0f * n / (float) N;
            for (; n <= N; n++)
                window[n] = 2.0f - 2.0f * n / (float) N;
        } else {
            for (n = 0; n <= L / 2 - 1; n++)
                window[n] = 2.0f * n / (float) N;
            for (; n <= N; n++)
                window[n] = 2.0f - 2.0f * n / (float) N;
        }
    }

    static void bartlettHann(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.62f - 0.48f * Math.abs((float) n / (float) N - 0.5f)
                    - 0.38f * cosf(2.0f * Math.PI * ((float) n / (float) N));
    }

    static void blackman(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.42f - 0.5f * cosf(2.0f * Math.PI * n / N) + 0.08f * cosf(4.0f * Math.PI * n / N);
    }

    /* 4-term -92dB side-lobe */
    static void blackmanHarris4Term92DbSidelobe(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n <= N; n++)
            window[n] = 0.35875f - 0.48829f * cosf(2.0f * Math.PI * n / N) + 0.14128f * cosf(4.0f * Math.PI * n / N)
                    - 0.01168f * cosf(6.0f * Math.PI * n / N);
    }

    static void connes(float[] window, final int L) {
        final int N = L - 1;
        final double N2 = (double) N / 2.;
        int n;

        for (n = 0; n <= N; n++) {
            double k = ((double) n - N2) / N2;
            k = 1.0f - k * k;
            window[n] = (float) (k * k);
        }
    }

    static void flattop(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.21557895f - 0.41663158f * cosf(2.0f * Math.PI * n / N)
                    + 0.277263158f * cosf(4.0f * Math.PI * n / N) - 0.083578947f * cosf(6.0f * Math.PI * n / N)
                    + 0.006947368f * cosf(8.0f * Math.PI * n / N);
    }

    /** 0.0 &lt; stddev &lt;= 0.5 */
    static void gauss(float[] window, final int L, final float stddev) {
        final int N = L - 1;
        final double N2 = (double) N / 2.;
        int n;

        if (!(stddev > 0.0f && stddev <= 0.5f))
            /*
             * stddev is not between 0 and 0.5, might be NaN.
             * Default to 0.5
             */
            gauss(window, L, 0.25f);
        else {
            for (n = 0; n <= N; n++) {
                final double k = ((double) n - N2) / (stddev * N2);
                window[n] = (float) Math.exp(-0.5f * k * k);
            }
        }
    }

    static void hamming(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.54f - 0.46f * cosf(2.0f * Math.PI * n / N);
    }

    static void hann(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.5f - 0.5f * cosf(2.0f * Math.PI * n / N);
    }

    static void kaiserBessel(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.402f - 0.498f * cosf(2.0f * Math.PI * n / N) + 0.098f * cosf(4.0f * Math.PI * n / N)
                    - 0.001f * cosf(6.0f * Math.PI * n / N);
    }

    static void nuttall(float[] window, final int L) {
        final int N = L - 1;
        int n;

        for (n = 0; n < L; n++)
            window[n] = 0.3635819f - 0.4891775f * cosf(2.0f * Math.PI * n / N)
                    + 0.1365995f * cosf(4.0f * Math.PI * n / N) - 0.0106411f * cosf(6.0f * Math.PI * n / N);
    }

    static void rectangle(float[] window, final int L) {
        Arrays.fill(window, 0, L, 1.0f);
    }

    static void triangle(float[] window, final int L) {
        int n;

        if ((L & 1) != 0) {
            for (n = 1; n <= (L + 1) / 2; n++)
                window[n - 1] = 2.0f * n / ((float) L + 1.0f);
            for (; n <= L; n++)
                window[n - 1] = (float) (2 * (L - n + 1)) / ((float) L + 1.0f);
        } else {
            for (n = 1; n <= L / 2; n++)
                window[n - 1] = 2.0f * n / ((float) L + 1.0f);
            for (; n <= L; n++)
                window[n - 1] = (float) (2 * (L - n + 1)) / ((float) L + 1.0f);
        }
    }

    static void tukey(float[] window, final int L, final float p) {
        if (p <= 0.0)
            rectangle(window, L);
        else if (p >= 1.0)
            hann(window, L);
        else if (!(p > 0.0f && p < 1.0f))
            /*
             * p is not between 0 and 1, probably NaN.
             * Default to 0.5
             */
            tukey(window, L, 0.5f);
        else {
            final int Np = (int) (p / 2.0f * L) - 1;
            int n;

            /* start with rectangle... */
            rectangle(window, L);

            if (Np > 0) {
                /* ...replace ends with hann */
                for (n = 0; n <= Np; n++) {
                    window[n] = 0.5f - 0.5f * cosf(Math.PI * n / Np);
                    window[L - Np - 1 + n] = 0.5f - 0.5f * cosf(Math.PI * (n + Np) / Np);
                }
            }
        }
    }

    static void partialTukey(float[] window, final int L, final float p, final float start, final float end) {
        final int start_n = (int) (start * L);
        final int end_n = (int) (end * L);
        final int N = end_n - start_n;
        int Np, n, i;

        if (p <= 0.0f)
            partialTukey(window, L, 0.05f, start, end);
        else if (p >= 1.0f)
            partialTukey(window, L, 0.95f, start, end);
        else if (!(p > 0.0f && p < 1.0f))
            /*
             * p is not between 0 and 1, probably NaN.
             * Default to 0.5
             */
            partialTukey(window, L, 0.5f, start, end);
        else {

            Np = (int) (p / 2.0f * N);

            for (n = 0; n < start_n && n < L; n++)
                window[n] = 0.0f;
            for (i = 1; n < (start_n + Np) && n < L; n++, i++)
                window[n] = 0.5f - 0.5f * cosf(Math.PI * i / Np);
            for (; n < (end_n - Np) && n < L; n++)
                window[n] = 1.0f;
            for (i = Np; n < end_n && n < L; n++, i--)
                window[n] = 0.5f - 0.5f * cosf(Math.PI * i / Np);
            for (; n < L; n++)
                window[n] = 0.0f;
        }
    }

    static void punchoutTukey(float[] window, final int L, final float p, final float start, final float end) {
        final int start_n = (int) (start * L);
        final int end_n = (int) (end * L);
        int Ns, Ne, n, i;

        if (p <= 0.0f)
            punchoutTukey(window, L, 0.05f, start, end);
        else if (p >= 1.0f)
            punchoutTukey(window, L, 0.95f, start, end);
        else if (!(p > 0.0f && p < 1.0f))
            /*
             * p is not between 0 and 1, probably NaN.
             * Default to 0.5
             */
            punchoutTukey(window, L, 0.5f, start, end);
        else {

            Ns = (int) (p / 2.0f * start_n);
            Ne = (int) (p / 2.0f * (L - end_n));

            for (n = 0, i = 1; n < Ns && n < L; n++, i++)
                window[n] = 0.5f - 0.5f * cosf(Math.PI * i / Ns);
            for (; n < start_n - Ns && n < L; n++)
                window[n] = 1.0f;
            for (i = Ns; n < start_n && n < L; n++, i--)
                window[n] = 0.5f - 0.5f * cosf(Math.PI * i / Ns);
            for (; n < end_n && n < L; n++)
                window[n] = 0.0f;
            for (i = 1; n < end_n + Ne && n < L; n++, i++)
                window[n] = 0.5f - 0.5f * cosf(Math.PI * i / Ne);
            for (; n < L - (Ne) && n < L; n++)
                window[n] = 1.0f;
            for (i = Ne; n < L; n++, i--)
                window[n] = 0.5f - 0.5f * cosf(Math.PI * i / Ne);
        }
    }

    static void welch(float[] window, final int L) {
        final int N = L - 1;
        final double N2 = (double) N / 2.;
        int n;

        for (n = 0; n <= N; n++) {
            final double k = ((double) n - N2) / N2;
            window[n] = (float) (1.0f - k * k);
        }
    }
}
