/* libFLAC - Free Lossless Audio Codec
 * Copyright (C) 2004-2009  Josh Coalson
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

import com.jcraft.jogg.Page;
import java.io.IOException;
import java.util.Arrays;

final class OggHelper {
    private static final int OGG_HEADER_FIXED_PORTION_LEN = 27;
    private static final int OGG_MAX_HEADER_LEN = OGG_HEADER_FIXED_PORTION_LEN + 255;

    private OggHelper() {
    }

    private static boolean fullRead(StreamEncoder encoder, byte[] buffer, int offset, int bytes,
            StreamEncoder.ReadCallback readCallback) throws IOException {
        while (bytes > 0) {
            int bytesRead = readCallback.read(encoder, buffer, offset, bytes);
            if (bytesRead < 0) {
                if (bytesRead == -3)
                    return false;
                encoder.state = bytesRead == -1 ? StreamEncoderState.OGG_ERROR : StreamEncoderState.CLIENT_ERROR;
                return false;
            }
            bytes -= bytesRead;
            offset += bytesRead;
        }
        return true;
    }

    static boolean simpleOggPageGetAt(StreamEncoder encoder, long position, Page page,
            StreamEncoder.SeekCallback seekCallback, StreamEncoder.ReadCallback readCallback) throws IOException {
        if (seekCallback == null)
            return false;
        /* move the stream pointer to the supposed beginning of the page */
        StreamEncoderSeekStatus seekStatus = seekCallback.seek(encoder, position);
        if (seekStatus != StreamEncoderSeekStatus.OK) {
            if (seekStatus == StreamEncoderSeekStatus.ERROR)
                encoder.state = StreamEncoderState.CLIENT_ERROR;
            return false;
        }
        /* allocate space for the page header */
        page.header_base = new byte[OGG_MAX_HEADER_LEN];
        /*
         * read in the fixed part of the page header (up to but not including
         * the segment table
         */
        if (!fullRead(encoder, page.header_base, 0, OGG_HEADER_FIXED_PORTION_LEN, readCallback))
            return false;
        page.header_len = OGG_HEADER_FIXED_PORTION_LEN + (page.header_base[26] & 255);
        /* check to see if it's a correct, "simple" page (one packet only) */

        /* doesn't start with OggS */
        if (page.header_base[0] != 'O' || page.header_base[1] != 'g' || page.header_base[2] != 'g'
                || page.header_base[3] != 'S' || /* continued packet */ (page.header_base[5] & 1) != 0
                || /* granulepos is non-zero */ page.granulepos() != 0
                /* packet is 0-size */
                || page.header_base[26] == 0) {
            encoder.state = StreamEncoderState.OGG_ERROR;
            return false;
        }
        /* read in the segment table */
        if (!fullRead(encoder, page.header_base, OGG_HEADER_FIXED_PORTION_LEN, page.header_base[26] & 255,
                readCallback))
            return false;
        int i;
        /* check to see that it specifies a single packet */
        for (i = 0; i < (page.header_base[26] & 255) - 1; i++) {
            if ((page.header_base[i + OGG_HEADER_FIXED_PORTION_LEN] & 255) != 255) {
                encoder.state = StreamEncoderState.OGG_ERROR;
                return false;
            }
        }
        page.body_len = 255 * i + (page.header_base[i + OGG_HEADER_FIXED_PORTION_LEN] & 255);
        /* allocate space for the page body */
        page.body_base = new byte[page.body_len];
        /* read in the page body */
        if (!fullRead(encoder, page.body_base, 0, page.body_len, readCallback))
            return false;
        /* check the CRC */
        byte[] crc = Arrays.copyOfRange(page.header_base, 22, 26);
        pageChecksumSet(page);
        if (!Arrays.equals(crc, 0, crc.length, page.header_base, 22, 26)) {
            encoder.state = StreamEncoderState.OGG_ERROR;
            return false;
        }
        return true;
    }

    static boolean simpleOggPageSetAt(StreamEncoder encoder, long position, Page page,
            StreamEncoder.SeekCallback seekCallback, StreamEncoder.WriteCallback writeCallback) throws IOException {
        if (seekCallback == null)
            return false;
        /* move the stream pointer to the supposed beginning of the page */
        StreamEncoderSeekStatus seekStatus = seekCallback.seek(encoder, position);
        if (seekStatus != StreamEncoderSeekStatus.OK) {
            if (seekStatus == StreamEncoderSeekStatus.ERROR)
                encoder.state = StreamEncoderState.CLIENT_ERROR;
            return false;
        }
        pageChecksumSet(page);
        /* re-write the page */
        if (!writeCallback.write(encoder, page.header_base, page.header, page.header_len, 0, 0)) {
            encoder.state = StreamEncoderState.CLIENT_ERROR;
            return false;
        }
        if (!writeCallback.write(encoder, page.body_base, page.body, page.body_len, 0, 0)) {
            encoder.state = StreamEncoderState.CLIENT_ERROR;
            return false;
        }
        return true;
    }

    private static void pageChecksumSet(Page og) {
        if (og != null) {
            int crc_reg = 0;

            /* safety; needed for API behavior, but not framing code */
            og.header_base[og.header + 22] = 0;
            og.header_base[og.header + 23] = 0;
            og.header_base[og.header + 24] = 0;
            og.header_base[og.header + 25] = 0;

            crc_reg = osUpdateCrc(crc_reg, og.header_base, og.header, og.header_len);
            crc_reg = osUpdateCrc(crc_reg, og.body_base, og.body, og.body_len);

            og.header_base[og.header + 22] = (byte) (crc_reg & 0xff);
            og.header_base[og.header + 23] = (byte) ((crc_reg >>> 8) & 0xff);
            og.header_base[og.header + 24] = (byte) ((crc_reg >>> 16) & 0xff);
            og.header_base[og.header + 25] = (byte) ((crc_reg >>> 24) & 0xff);
        }
    }

    /* checksum the page */
    private static int osUpdateCrc(int crc, byte[] buffer, int offset, int size) {
        /*
         * The same as the ethernet generator polynomial, although we use an
         * unreflected alg and an init/final of 0, not 0xffffffff
         */
        final int polynomial = 0x04c11db7;
        while (size-- != 0) {
            crc ^= (buffer[offset++] & 0xff) << 24;
            for (int j = 0; j < 8; j++)
                crc = (crc << 1) ^ ((crc & (1 << 31)) != 0 ? polynomial : 0);
        }
        return crc;
    }
}
