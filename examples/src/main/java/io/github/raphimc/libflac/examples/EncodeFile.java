/* example_c_encode_file - Simple FLAC file encoder using libFLAC
 * Copyright (C) 2007-2009  Josh Coalson
 * Copyright (C) 2011-2023  Xiph.Org Foundation
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.github.raphimc.libflac.examples;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import io.github.raphimc.libflac.Padding;
import io.github.raphimc.libflac.StreamEncoder;
import io.github.raphimc.libflac.StreamEncoderInitStatus;
import io.github.raphimc.libflac.StreamMetadata;
import io.github.raphimc.libflac.VorbisComment;

public final class EncodeFile {
    private static final int READSIZE = 1024;

    private EncodeFile() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            throw new IllegalArgumentException("usage: EncodeFile infile.wav outfile.flac");
        /* allocate the encoder */
        try (FileInputStream input = new FileInputStream(args[0]); StreamEncoder encoder = new StreamEncoder()) {
            byte[] buffer = new byte[READSIZE * 2 * Short.BYTES];
            /* read wav header and validate it */
            if (input.readNBytes(buffer, 0, 44) != 44
                    || !Arrays.equals(buffer, 0, 4, "RIFF".getBytes(StandardCharsets.US_ASCII), 0, 4)
                    || !Arrays.equals(buffer, 8, 24,
                            new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' ', 16, 0, 0, 0, 1, 0, 2, 0}, 0, 16)
                    || !Arrays.equals(buffer, 32, 40, new byte[] {4, 0, 16, 0, 'd', 'a', 't', 'a'}, 0, 8))
                throw new IOException(
                        "invalid/unsupported WAVE file, only 16bps stereo WAVE in canonical form allowed");
            int sampleRate = (buffer[24] & 0xff) | ((buffer[25] & 0xff) << 8) | ((buffer[26] & 0xff) << 16)
                    | ((buffer[27] & 0xff) << 24);
            int channels = 2, bps = 16;
            long totalSamples = Integer.toUnsignedLong((buffer[40] & 0xff) | ((buffer[41] & 0xff) << 8)
                    | ((buffer[42] & 0xff) << 16) | ((buffer[43] & 0xff) << 24)) / 4;
            encoder.setVerify(true);
            encoder.setCompressionLevel(5);
            encoder.setChannels(channels);
            encoder.setBitsPerSample(bps);
            encoder.setSampleRate(sampleRate);
            encoder.setTotalSamplesEstimate(totalSamples);
            /* now add some metadata; we'll add some tags and a padding block */
            VorbisComment tags = new VorbisComment();
            /* there are many tag (vorbiscomment) functions but these are convenient for this particular use: */
            if (!tags.appendComment(VorbisComment.entryFromNameValuePair("ARTIST", "Some Artist"))
                    || !tags.appendComment(VorbisComment.entryFromNameValuePair("YEAR", "1984")))
                throw new IOException("adding Vorbis comments");
            /* set the padding length */
            if (!encoder.setMetadata(new StreamMetadata[] {tags, new Padding(1234)}))
                throw new IOException("setting encoder metadata");
            /* initialize encoder */
            StreamEncoderInitStatus initStatus = encoder.initFile(args[1],
                    (e, bytesWritten, samplesWritten, framesWritten, totalFramesEstimate) -> System.err.printf(
                            "wrote %d bytes, %d/%d samples, %d/%d frames%n", bytesWritten, samplesWritten, totalSamples,
                            framesWritten, totalFramesEstimate));
            if (initStatus != StreamEncoderInitStatus.OK)
                throw new IOException("initializing encoder: " + initStatus);
            int[] pcm = new int[READSIZE * 2];
            long left = totalSamples;
            boolean ok = false;
            try {
                /* read blocks of samples from WAVE file and feed to encoder */
                while (left > 0) {
                    int need = (int) Math.min(left, READSIZE);
                    int bytes = need * channels * (bps / Byte.SIZE);
                    if (input.readNBytes(buffer, 0, bytes) != bytes)
                        throw new EOFException();
                    /* convert the packed little-endian 16-bit PCM samples from WAVE into an interleaved int buffer for libFLAC */

                    /* inefficient but simple and works on big- or little-endian machines */
                    for (int i = 0; i < need * channels; i++)
                        pcm[i] = (short) ((buffer[2 * i] & 0xff) | (buffer[2 * i + 1] << 8));
                    /* feed samples to encoder */
                    if (!encoder.processInterleaved(pcm, need))
                        throw new IOException("encoding: " + encoder.getState());
                    left -= need;
                }
                ok = true;
            } finally {

                encoder.finish();
                System.err.println("encoding: " + (ok ? "succeeded" : "FAILED"));
                System.err.println("   state: " + encoder.getState());
            }
        }
    }
}
