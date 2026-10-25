/* example_c_decode_file - Simple FLAC file decoder using libFLAC
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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import io.github.raphimc.libflac.StreamDecoder;
import io.github.raphimc.libflac.StreamDecoderInitStatus;
import io.github.raphimc.libflac.StreamInfo;

public final class DecodeFile {
    private DecodeFile() {
    }

    private static void writeLittleEndianUInt16(OutputStream f, int x) throws IOException {
        f.write(x);
        f.write(x >>> 8);
    }

    private static void writeLittleEndianInt16(OutputStream f, short x) throws IOException {
        writeLittleEndianUInt16(f, Short.toUnsignedInt(x));
    }

    private static void writeLittleEndianUInt32(OutputStream f, int x) throws IOException {
        f.write(x);
        f.write(x >>> 8);
        f.write(x >>> 16);
        f.write(x >>> 24);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            throw new IllegalArgumentException("usage: DecodeFile infile.flac outfile.wav");
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(args[1]));
                StreamDecoder decoder = new StreamDecoder()) {
            StreamInfo[] info = {null};
            decoder.setMd5Checking(true);
            StreamDecoderInitStatus initStatus = decoder.initFile(args[0], (d, frame, pcm) -> {
                StreamInfo streamInfo = info[0];
                if (streamInfo == null || streamInfo.getTotalSamples() == 0)
                    throw new IOException("this example only works for FLAC files that have a total_samples count in"
                            + " STREAMINFO");
                int channels = streamInfo.getChannels(), bps = streamInfo.getBitsPerSample();
                if (channels != 2 || bps != 16)
                    throw new IOException("this example only supports 16bit stereo streams");
                if (frame.getHeader().getChannels() != 2)
                    throw new IOException(
                            "This frame contains " + frame.getHeader().getChannels() + " channels (should be 2)");
                /* write WAVE header before we write the first frame */
                if (frame.getHeader().getNumber() == 0) {
                    int totalSize = (int) (streamInfo.getTotalSamples() * channels * (bps / Byte.SIZE));
                    output.write("RIFF".getBytes(StandardCharsets.US_ASCII));
                    writeLittleEndianUInt32(output, totalSize + 36);
                    output.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
                    writeLittleEndianUInt32(output, 16);
                    writeLittleEndianUInt16(output, 1);
                    writeLittleEndianUInt16(output, channels);
                    writeLittleEndianUInt32(output, streamInfo.getSampleRate());
                    writeLittleEndianUInt32(output, streamInfo.getSampleRate() * channels * (bps / Byte.SIZE));
                    /* block align */
                    writeLittleEndianUInt16(output, channels * (bps / Byte.SIZE));
                    writeLittleEndianUInt16(output, bps);
                    output.write("data".getBytes(StandardCharsets.US_ASCII));
                    writeLittleEndianUInt32(output, totalSize);
                }
                /* write decoded PCM samples */
                for (int i = 0; i < frame.getHeader().getBlocksize(); i++)
                    for (int channel = 0; channel < 2; channel++)
                        writeLittleEndianInt16(output, (short) pcm[channel][i]);
                return true;
            }, (d, block) -> {
                if (block instanceof StreamInfo streamInfo) {
                    /* save for later */
                    info[0] = streamInfo;
                    /* print some stats */
                    System.err.printf(
                            "sample rate    : %d Hz%nchannels       : %d%nbits per sample: %d%ntotal"
                                    + " samples  : %d%n",
                            streamInfo.getSampleRate(), streamInfo.getChannels(), streamInfo.getBitsPerSample(),
                            streamInfo.getTotalSamples());
                }
            }, (d, error) -> System.err.println("Got error callback: " + error));
            if (initStatus != StreamDecoderInitStatus.OK)
                throw new IOException("initializing decoder: " + initStatus);
            if (!decoder.processUntilEndOfStream())
                throw new IOException("decoding: " + decoder.getState());
            System.err.println("decoding: succeeded");
            System.err.println("   state: " + decoder.getState());
        }
    }
}
