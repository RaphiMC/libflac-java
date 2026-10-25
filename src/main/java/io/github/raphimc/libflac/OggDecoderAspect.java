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
import com.jcraft.jogg.SyncState;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

final class OggDecoderAspect extends InputStream {
    private static final int OGG_BYTES_CHUNK = 8192;

    /** these are storage for values that can be set through the API */
    private final boolean useFirstSerialNumber;
    private int serialNumber;
    /** these are for internal state related to Ogg decoding */
    private final StreamState streamState = new StreamState();
    private final SyncState syncState = new SyncState();
    private int versionMajor = -1, versionMinor = -1;
    private boolean needSerialNumber;
    private boolean endOfStream;
    /** only if true will the following vars be valid */
    private boolean haveWorkingPage;
    private final Page workingPage = new Page();
    /** only if true will the following vars be valid */
    private boolean haveWorkingPacket;
    /** as we work through the packet we will move workingPacket.packet forward and workingPacket.bytes down */
    private final Packet workingPacket = new Packet();

    private final InputStream input;

    OggDecoderAspect(InputStream input, Integer serialNumber) {
        this.input = input;
        useFirstSerialNumber = serialNumber == null;
        this.serialNumber = serialNumber == null ? 0 : serialNumber;
        /* we will determine the serial number later if necessary */
        streamState.init(this.serialNumber);
        syncState.init();
        needSerialNumber = useFirstSerialNumber;
    }

    void flush() {
        streamState.reset();
        syncState.reset();
        endOfStream = false;
        haveWorkingPage = false;
    }

    void resetAspect() {
        flush();
        if (useFirstSerialNumber)
            needSerialNumber = true;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n;
        do {
            n = read(one, 0, 1);
        } while (n == 0);
        return n < 0 ? -1 : one[0] & 255;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0)
            return 0;
        int bytes = 0;
        /*
         * The FLAC decoding API uses pull-based reads, whereas Ogg decoding
         * is push-based.  In libFLAC, when you ask to decode a frame, the
         * decoder will eventually call the read callback to supply some data,
         * but how much it asks for depends on how much free space it has in
         * its internal buffer.  It does not try to grow its internal buffer
         * to accommodate a whole frame because then the internal buffer size
         * could not be limited, which is necessary in embedded applications.
         *
         * Ogg however grows its internal buffer until a whole page is present;
         * only then can you get decoded data out.  So we can't just ask for
         * the same number of bytes from Ogg, then pass what's decoded down to
         * libFLAC.  If what libFLAC is asking for will not contain a whole
         * page, then we will get no data from syncState.pageout(), and at the
         * same time cannot just read more data from the client for the purpose
         * of getting a whole decoded page because the decoded size might be
         * larger than libFLAC's internal buffer.
         *
         * Instead, whenever this read callback wrapper is called, we will
         * continually request data from the client until we have at least one
         * page, and manage pages internally so that we can send pieces of
         * pages down to libFLAC in such a way that we obey its size
         * requirement.  To limit the amount of callbacks, we will always try
         * to read in enough pages to return the full number of bytes
         * requested.
         */
        while (bytes < length && !endOfStream) {
            if (haveWorkingPage) {
                if (haveWorkingPacket) {
                    int n = length - bytes;
                    if (workingPacket.bytes <= n) {
                        /* the rest of the packet will fit in the buffer */
                        n = workingPacket.bytes;
                        System.arraycopy(workingPacket.packet_base, workingPacket.packet, buffer, offset + bytes, n);
                        bytes += n;
                        haveWorkingPacket = false;
                    } else {
                        /* only n bytes of the packet will fit in the buffer */
                        System.arraycopy(workingPacket.packet_base, workingPacket.packet, buffer, offset + bytes, n);
                        bytes += n;
                        workingPacket.packet += n;
                        workingPacket.bytes -= n;
                    }
                } else {
                    /* try and get another packet */
                    int ret = streamState.packetout(workingPacket);
                    if (ret > 0) {
                        haveWorkingPacket = true;
                        /* if it is the first header packet, check for magic and a supported Ogg FLAC mapping version */
                        if (workingPacket.bytes > 0
                                && workingPacket.packet_base[workingPacket.packet] == (byte) OggMapping.OGG_MAPPING_FIRST_HEADER_PACKET_TYPE) {
                            int headerLength = OggMapping.OGG_MAPPING_PACKET_TYPE_LENGTH
                                    + OggMapping.OGG_MAPPING_MAGIC_LENGTH + OggMapping.OGG_MAPPING_VERSION_MAJOR_LENGTH
                                    + OggMapping.OGG_MAPPING_VERSION_MINOR_LENGTH
                                    + OggMapping.OGG_MAPPING_NUM_HEADERS_LENGTH;
                            if (workingPacket.bytes < headerLength)
                                throw new Aborted();
                            int b = workingPacket.packet + OggMapping.OGG_MAPPING_PACKET_TYPE_LENGTH;
                            if (!Arrays.equals(workingPacket.packet_base, b, b + OggMapping.OGG_MAPPING_MAGIC_LENGTH,
                                    OggMapping.OGG_MAPPING_MAGIC, 0, OggMapping.OGG_MAPPING_MAGIC_LENGTH))
                                throw new Aborted();
                            b += OggMapping.OGG_MAPPING_MAGIC_LENGTH;
                            versionMajor = workingPacket.packet_base[b] & 255;
                            b += OggMapping.OGG_MAPPING_VERSION_MAJOR_LENGTH;
                            versionMinor = workingPacket.packet_base[b] & 255;
                            if (versionMajor != 1)
                                throw new Aborted();
                            workingPacket.packet += headerLength;
                            workingPacket.bytes -= headerLength;
                        }
                    } else if (ret == 0)
                        haveWorkingPage = false;
                    else
                        /* lost sync, we'll leave the working page for the next call */
                        return bytes;
                }
            } else {
                /* try and get another page */
                int ret = syncState.pageout(workingPage);
                if (ret > 0) {
                    /* got a page, grab the serial number if necessary */
                    if (needSerialNumber) {
                        serialNumber = workingPage.serialno();

                        streamState.init(serialNumber);
                        needSerialNumber = false;
                    }
                    if (streamState.pagein(workingPage) == 0) {
                        haveWorkingPage = true;
                        haveWorkingPacket = false;
                    }
                    /* else do nothing, could be a page from another stream */
                } else if (ret == 0) {
                    /* need more data */
                    int oggBytesToRead = Math.max(length - bytes, OGG_BYTES_CHUNK);
                    int oggBuffer = syncState.buffer(oggBytesToRead);
                    int oggBytesRead = input.read(syncState.data, oggBuffer, oggBytesToRead);
                    if (oggBytesRead < 0) {
                        endOfStream = true;
                        oggBytesRead = 0;
                    }
                    if (syncState.wrote(oggBytesRead) != 0)
                        throw new Aborted();
                } else
                    /* lost sync, bail out */
                    /* ret < 0 */
                    return bytes;
            }
        }
        return bytes == 0 && endOfStream ? -1 : bytes;
    }

    static final class Aborted extends IOException {
    }
}
