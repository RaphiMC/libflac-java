/* libFLAC - Free Lossless Audio Codec
 * Copyright (C) 2002-2009  Josh Coalson
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

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import java.io.IOException;
import java.util.Arrays;

final class OggEncoderAspect {
    private static final int OGG_MAPPING_VERSION_MAJOR = 1;
    private static final int OGG_MAPPING_VERSION_MINOR = 0;

    private final int numMetadata;
    /** these are for internal state related to Ogg encoding */
    private final StreamState streamState = new StreamState();
    private final Page page = new Page();
    /** true if we've seen the fLaC magic in the write callback yet */
    private boolean seenMagic;
    private boolean isFirstPacket = true;
    private long samplesWritten;
    private final WriteCallbackProxy writeCallback;

    OggEncoderAspect(int serialNumber, int numMetadata, WriteCallbackProxy writeCallback) {
        /* we will determine the serial number later if necessary */
        streamState.init(serialNumber);
        this.numMetadata = numMetadata;
        this.writeCallback = writeCallback;
    }

    void finish() {
        streamState.clear();
    }

    /*
     * The basic FLAC -> Ogg mapping goes like this:
     *
     * - 'fLaC' magic and STREAMINFO block get combined into the first
     *   packet.  The packet is prefixed with
     *   + the one-byte packet type 0x7F
     *   + 'FLAC' magic
     *   + the 2 byte Ogg FLAC mapping version number
     *   + tne 2 byte big-endian # of header packets
     * - The first packet is flushed to the first page.
     * - Each subsequent metadata block goes into its own packet.
     * - Each metadata packet is flushed to page (this is not required,
     *   the mapping only requires that a flush must occur after all
     *   metadata is written).
     * - Each subsequent FLAC audio frame goes into its own packet.
     *
     * WATCHOUT:
     * This depends on the behavior of StreamEncoder that we get a
     * separate write callback for the fLaC magic, and then separate write
     * callbacks for each metadata block and audio frame.
     */
    void writeCallbackWrapper(byte[] buffer, int samples, boolean isLastBlock) throws IOException {
        /*
         * WATCHOUT:
         * This depends on the behavior of StreamEncoder that 'samples'
         * will be 0 for metadata writes.
         */
        boolean isMetadata = samples == 0;
        /*
         * Treat fLaC magic packet specially.  We will note when we see it, then
         * wait until we get the STREAMINFO and prepend it in that packet
         */
        if (!seenMagic) {
            if (isMetadata && Arrays.equals(buffer, Format.STREAM_SYNC_STRING)) {
                seenMagic = true;
                return;
            }
            throw new IOException("Ogg FLAC stream must begin with the native FLAC signature");
        }
        Packet packet = new Packet();
        packet.granulepos = samplesWritten + samples;
        if (isFirstPacket) {
            if (buffer.length != Format.STREAM_METADATA_HEADER_LENGTH + Format.STREAM_METADATA_STREAMINFO_LENGTH)
                /*
                 * If we get here, our assumption about the way write callbacks happen
                 * (explained above) is wrong
                 */
                throw new IOException("Ogg FLAC first packet must contain STREAMINFO");
            byte[] syntheticFirstPacketBody = new byte[OggMapping.OGG_MAPPING_PACKET_TYPE_LENGTH
                    + OggMapping.OGG_MAPPING_MAGIC_LENGTH + OggMapping.OGG_MAPPING_VERSION_MAJOR_LENGTH
                    + OggMapping.OGG_MAPPING_VERSION_MINOR_LENGTH + OggMapping.OGG_MAPPING_NUM_HEADERS_LENGTH
                    + Format.STREAM_SYNC_LENGTH + Format.STREAM_METADATA_HEADER_LENGTH
                    + Format.STREAM_METADATA_STREAMINFO_LENGTH];
            int b = 0;
            /* add first header packet type */
            syntheticFirstPacketBody[b] = (byte) OggMapping.OGG_MAPPING_FIRST_HEADER_PACKET_TYPE;
            b += OggMapping.OGG_MAPPING_PACKET_TYPE_LENGTH;
            /* add 'FLAC' mapping magic */
            System.arraycopy(OggMapping.OGG_MAPPING_MAGIC, 0, syntheticFirstPacketBody, b,
                    OggMapping.OGG_MAPPING_MAGIC_LENGTH);
            b += OggMapping.OGG_MAPPING_MAGIC_LENGTH;
            /* add Ogg FLAC mapping major version number */
            syntheticFirstPacketBody[b] = OGG_MAPPING_VERSION_MAJOR;
            b += OggMapping.OGG_MAPPING_VERSION_MAJOR_LENGTH;
            /* add Ogg FLAC mapping minor version number */
            syntheticFirstPacketBody[b] = OGG_MAPPING_VERSION_MINOR;
            b += OggMapping.OGG_MAPPING_VERSION_MINOR_LENGTH;
            /* add number of header packets */
            syntheticFirstPacketBody[b++] = (byte) (numMetadata >>> 8);
            syntheticFirstPacketBody[b++] = (byte) numMetadata;
            /* add native FLAC 'fLaC' magic */
            System.arraycopy(Format.STREAM_SYNC_STRING, 0, syntheticFirstPacketBody, b, Format.STREAM_SYNC_LENGTH);
            b += Format.STREAM_SYNC_LENGTH;
            /* add STREAMINFO */
            System.arraycopy(buffer, 0, syntheticFirstPacketBody, b, buffer.length);
            buffer = syntheticFirstPacketBody;
            packet.b_o_s = 1;
            isFirstPacket = false;
        }
        packet.packet_base = buffer;
        packet.bytes = buffer.length;
        packet.e_o_s = isLastBlock ? 1 : 0;
        if (streamState.packetin(packet) != 0)
            throw new IOException("Ogg packet submission failed");
        while (nextPage(isMetadata))
            writePage();
        samplesWritten += samples;
    }

    private void writePage() throws IOException {
        writeCallback.write(page.header_base, page.header, page.header_len);
        writeCallback.write(page.body_base, page.body, page.body_len);
    }

    private boolean nextPage(boolean flush) throws IOException {
        try {
            return (flush ? streamState.flush(page) : streamState.pageout(page)) != 0;
        } catch (IndexOutOfBoundsException e) {
            throw new IOException("JOrbis Ogg page generation failed", e);
        }
    }

    @FunctionalInterface
    interface WriteCallbackProxy {
        void write(byte[] buffer, int offset, int bytes) throws IOException;
    }
}
