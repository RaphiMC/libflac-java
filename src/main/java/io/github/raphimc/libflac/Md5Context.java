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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Md5Context {
    private final MessageDigest digest;

    Md5Context() {
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    void reset() {
        digest.reset();
    }

    byte[] finish() {
        return digest.digest();
    }

    /* Convert the incoming audio signal to a byte stream */
    private static void formatInput(byte[] buffer, int[][] signal, int offset, int channels, int samples,
            int bytesPerSample) {
        /* Storage in the output buffer is little endian. */
        int p = 0;
        for (int sample = 0; sample < samples; sample++)
            for (int channel = 0; channel < channels; channel++) {
                int aWord = signal[channel][offset + sample];
                for (int b = 0; b < bytesPerSample; b++)
                    buffer[p++] = (byte) (aWord >>> (Byte.SIZE * b));
            }
    }

    /* Convert the incoming audio signal to a byte stream and update the digest. */
    void accumulate(int[][] signal, int offset, int channels, int samples, int bytesPerSample) {
        byte[] block = new byte[4096 * channels * bytesPerSample];
        for (int begin = 0; begin < samples;) {
            int n = Math.min(4096, samples - begin), p = n * channels * bytesPerSample;
            formatInput(block, signal, offset + begin, channels, n, bytesPerSample);
            digest.update(block, 0, p);
            begin += n;
        }
    }
}
