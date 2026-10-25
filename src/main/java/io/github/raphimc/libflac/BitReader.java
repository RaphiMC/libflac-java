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

import java.io.IOException;
import java.io.InputStream;

final class BitReader {
    private static final long WORD_ALL_ONES = -1L;
    /**
     * This should be at least twice as large as the largest number of words
     * required to represent any 'number' (in any encoding) you are going to
     * read.  With FLAC this is on the order of maybe a few hundred bits.
     * If the buffer is smaller than that, the decoder won't be able to read
     * in a whole number that is in a variable length encoding (e.g. Rice).
     * But to be practical it should be at least 1K bytes.
     *
     * Increase this number to decrease the number of read callbacks, at the
     * expense of using more memory.  Or decrease for the reverse effect,
     * keeping in mind the limit from the first paragraph.  The optimal size
     * also depends on the CPU cache size and other factors; some twiddling
     * may be necessary to squeeze out the best performance.
     *
     * in words
     */
    private static final int BITREADER_DEFAULT_CAPACITY = 65536 / Long.SIZE;

    private final byte[] buffer = new byte[BITREADER_DEFAULT_CAPACITY * Long.BYTES];
    /**
     * words: # of completed words in buffer
     *
     * bytes: # of bytes in incomplete word at words * Long.BYTES
     *
     * consumedWords: #words ...
     *
     * consumedBits: ... + (#bits of head word) already consumed from the front of buffer
     */
    private int words, bytes, consumedWords, consumedBits;
    /**
     * readCrc16: the running frame CRC
     *
     * crc16Offset: the number of words in the current buffer that should not be CRC'd
     *
     * crc16Align: the number of bits in the current consumed word that should not be CRC'd
     */
    private int readCrc16, crc16Offset, crc16Align;
    /** whether reads are limited */
    private boolean readLimitSet;
    /** the remaining size of what can be read */
    private long readLimit = 0xffffffffL;
    /** the location of the last seen framesync, if it is in the buffer, in bits from front of buffer */
    private int lastSeenFrameSync = -1;
    private final InputStream input;

    BitReader(InputStream input) {
        this.input = input;
    }

    private void crc16UpdateWord(int index) {
        for (; crc16Align < Long.SIZE; crc16Align += 8)
            readCrc16 = Crc.crc16Update(buffer[index * Long.BYTES + crc16Align / 8], readCrc16);
        crc16Align = 0;
    }

    private void crc16UpdateBlock() {
        if (consumedWords > crc16Offset && crc16Align != 0)
            crc16UpdateWord(crc16Offset++);
        /* Prevent OOB read due to wrap-around. */
        if (consumedWords > crc16Offset)
            readCrc16 = Crc.crc16(buffer, crc16Offset * Long.BYTES, (consumedWords - crc16Offset) * Long.BYTES,
                    readCrc16);
        crc16Offset = 0;
    }

    private void readFromClient() throws IOException {
        /* first shift the unconsumed buffer data toward the front as much as possible */
        if (consumedWords > 0) {
            /* invalidate last seen framesync */
            lastSeenFrameSync = -1;
            /* CRC consumed words */
            crc16UpdateBlock();
            int start = consumedWords * Long.BYTES;
            int end = words * Long.BYTES + bytes;
            System.arraycopy(buffer, start, buffer, 0, end - start);
            words -= consumedWords;
            consumedWords = 0;
        }
        /* set the target for reading, taking into account word alignment */
        int at = words * Long.BYTES + bytes;
        int count = buffer.length - at;
        /* no space left, buffer is too small; see note for BitReader.BITREADER_DEFAULT_CAPACITY */
        if (count == 0)
            throw new InvalidRice();
        /* read in the data; note that the callback may return a smaller number of bytes */
        int n = input.read(buffer, at, count);
        if (n < 0)
            throw new EndOfInput();
        bytes += n;
        words += bytes / Long.BYTES;
        bytes %= Long.BYTES;
    }

    void clear() {
        words = bytes = consumedWords = consumedBits = 0;
        readLimitSet = false;
        readLimit = 0xffffffffL;
        lastSeenFrameSync = -1;
    }

    void setFrameSyncLocation() {
        lastSeenFrameSync = consumedWords * Long.BYTES + consumedBits / 8;
    }

    boolean rewindToAfterLastSeenFrameSync() {
        if (lastSeenFrameSync == -1) {
            consumedWords = consumedBits = 0;
            return false;
        }
        consumedWords = (lastSeenFrameSync + 1) / Long.BYTES;
        consumedBits = ((lastSeenFrameSync + 1) % Long.BYTES) * 8;
        return true;
    }

    /** CRC functions */
    void resetReadCrc16(int seed) {
        readCrc16 = seed;
        crc16Offset = consumedWords;
        crc16Align = consumedBits;
    }

    int getReadCrc16() {
        /* CRC consumed words up to here */
        crc16UpdateBlock();
        /* CRC any tail bytes in a partially-consumed word */
        for (; crc16Align < consumedBits; crc16Align += 8)
            readCrc16 = Crc.crc16Update(buffer[consumedWords * Long.BYTES + crc16Align / 8], readCrc16);
        return readCrc16;
    }

    /** info functions */
    boolean isConsumedByteAligned() {
        return (consumedBits & 7) == 0;
    }

    int getBitsLeftForByteAlignment() {
        return 8 - (consumedBits & 7);
    }

    int getInputBitsUnconsumed() {
        return (words - consumedWords) * Long.SIZE + bytes * Byte.SIZE - consumedBits;
    }

    void setLimit(long limit) {
        readLimit = limit;
        readLimitSet = true;
    }

    void removeLimit() {
        readLimitSet = false;
        readLimit = 0xffffffffL;
    }

    long limitRemaining() {
        return readLimit;
    }

    void invalidateLimit() {
        readLimit = 0xffffffffL;
    }

    private long word(int index) {
        long value = 0;
        int end = words * Long.BYTES + bytes;
        for (int i = index * Long.BYTES; i < (index + 1) * Long.BYTES; i++)
            value = (value << 8) | (i < end ? buffer[i] & 255L : 0);
        return value;
    }

    /** read functions */
    int readRawUInt32(int bits) throws IOException {
        if (bits < 0 || bits > Integer.SIZE)
            throw new IllegalArgumentException("bit count");
        /* OPT: investigate if this can ever happen, maybe change to assertion */
        if (bits == 0)
            return 0;
        if (readLimitSet && readLimit < 0xffffffffL) {
            if (readLimit < bits) {
                readLimit = 0xffffffffL;
                throw new LimitExceeded();
            } else
                readLimit -= bits;
        }
        while ((words - consumedWords) * Long.SIZE + bytes * 8 - consumedBits < bits)
            readFromClient();
        /* if we've not consumed up to a partial tail word... */
        if (consumedWords < words) {
            /* OPT: taking out the consumedBits==0 "else" case below might make things faster if less code allows the compiler to inline this function */
            if (consumedBits != 0) {
                int n = Long.SIZE - consumedBits;
                long word = word(consumedWords);
                /* this also works when consumedBits==0, it's just a little slower than necessary for that case */
                long mask = consumedBits < Long.SIZE ? WORD_ALL_ONES >>> consumedBits : 0;
                if (bits < n) {
                    int shift = n - bits;
                    /* The result has <= 32 non-zero bits */
                    int val = shift < Long.SIZE ? (int) ((word & mask) >>> shift) : 0;
                    consumedBits += bits;
                    return val;
                }
                /* (Long.SIZE - consumedBits <= bits) ==> (WORD_ALL_ONES >> consumedBits) has no more than 'bits' non-zero bits */
                int val = (int) (word & mask);
                bits -= n;
                consumedWords++;
                consumedBits = 0;
                /* if there are still bits left to read, there have to be less than 32 so they will all be in the next word */
                if (bits != 0) {
                    int shift = Long.SIZE - bits;
                    val = bits < Integer.SIZE ? val << bits : 0;
                    val |= shift < Long.SIZE ? (int) (word(consumedWords) >>> shift) : 0;
                    consumedBits = bits;
                }
                return val;
            } else {
                /* consumedBits == 0 */
                int val = (int) (word(consumedWords) >>> (Long.SIZE - bits));
                consumedBits = bits;
                return val;
            }
        } else {
            /*
             * in this case we're starting our read at a partial tail word;
             * the reader has guaranteed that we have at least 'bits' bits
             * available to read, which makes this case simpler.
             */

            /* OPT: taking out the consumedBits==0 "else" case below might make things faster if less code allows the compiler to inline this function */
            if (consumedBits != 0) {
                /* this also works when consumedBits==0, it's just a little slower than necessary for that case */
                int val = (int) ((word(consumedWords) & (WORD_ALL_ONES >>> consumedBits)) >>> (Long.SIZE - consumedBits
                        - bits));
                consumedBits += bits;
                return val;
            } else {
                int val = (int) (word(consumedWords) >>> (Long.SIZE - bits));
                consumedBits += bits;
                return val;
            }
        }
    }

    int readRawInt32(int bits) throws IOException {
        if (bits < 1 || bits > Integer.SIZE)
            throw new IllegalArgumentException("signed bit count");
        int uval = readRawUInt32(bits);
        /* sign-extend uval assuming it is currently bits wide. */

        /* From: https://graphics.stanford.edu/~seander/bithacks.html#FixedSignExtend */
        int mask = 1 << (bits - 1);
        return (uval ^ mask) - mask;
    }

    long readRawUInt64(int bits) throws IOException {
        if (bits < 0 || bits > Long.SIZE)
            throw new IllegalArgumentException("bit count");
        if (bits > Integer.SIZE) {
            long hi = Integer.toUnsignedLong(readRawUInt32(bits - Integer.SIZE));
            long lo = Integer.toUnsignedLong(readRawUInt32(Integer.SIZE));
            return (hi << Integer.SIZE) | lo;
        }
        return Integer.toUnsignedLong(readRawUInt32(bits));
    }

    long readRawInt64(int bits) throws IOException {
        if (bits < 1 || bits > Long.SIZE)
            throw new IllegalArgumentException("signed bit count");
        long uval = readRawUInt64(bits);
        /* sign-extend uval assuming it is currently bits wide. */

        /* From: https://graphics.stanford.edu/~seander/bithacks.html#FixedSignExtend */
        long mask = 1L << (bits - 1);
        return (uval ^ mask) - mask;
    }

    /** only for bits=32 */
    int readUInt32LittleEndian() throws IOException {
        /* this doesn't need to be that fast as currently it is only used for vorbis comments */
        int x32 = readRawUInt32(8);
        int x8 = readRawUInt32(8);
        x32 |= x8 << 8;
        x8 = readRawUInt32(8);
        x32 |= x8 << 16;
        x8 = readRawUInt32(8);
        x32 |= x8 << 24;
        return x32;
    }

    /** WATCHOUT: does not CRC the skipped data! */
    void skipBitsNoCrc(long bits) throws IOException {
        /*
         * OPT: a faster implementation is possible but probably not that useful
         * since this is only called a couple of times in the metadata readers.
         */
        if (!isConsumedByteAligned() && bits > 0) {
            int n = (int) Math.min(getBitsLeftForByteAlignment(), bits);
            readRawUInt32(n);
            bits -= n;
        }
        if (bits >= 8)
            skipByteBlockAlignedNoCrc((int) (bits / 8));
        if (bits % 8 != 0)
            readRawUInt32((int) (bits % 8));
    }

    /** WATCHOUT: does not CRC the read data! */
    void skipByteBlockAlignedNoCrc(int nvals) throws IOException {
        if (readLimitSet && readLimit < 0xffffffffL) {
            if (readLimit < Integer.toUnsignedLong(nvals * Byte.SIZE)) {
                readLimit = 0xffffffffL;
                throw new LimitExceeded();
            }
        }
        /* step 1: skip over partial head word to get word aligned */

        /* i.e. run until we read 'nvals' bytes or we hit the end of the head word */
        while (nvals > 0 && consumedBits != 0) {
            readRawUInt32(8);
            nvals--;
        }
        /* step 2: skip whole words in chunks */
        while (nvals >= Long.BYTES) {
            if (consumedWords < words) {
                consumedWords++;
                nvals -= Long.BYTES;
                if (readLimitSet)
                    readLimit = (readLimit - Long.SIZE) & 0xffffffffL;
            } else
                readFromClient();
        }
        /* step 3: skip any remainder from partial tail bytes */
        while (nvals-- > 0)
            readRawUInt32(8);
    }

    /** WATCHOUT: does not CRC the read data! */
    byte[] readByteBlockAlignedNoCrc(int nvals) throws IOException {
        if (readLimitSet && readLimit < 0xffffffffL) {
            if (readLimit < Integer.toUnsignedLong(nvals * Byte.SIZE)) {
                readLimit = 0xffffffffL;
                throw new LimitExceeded();
            }
        }
        byte[] data = new byte[nvals];
        int at = 0;
        /* step 1: read from partial head word to get word aligned */

        /* i.e. run until we read 'nvals' bytes or we hit the end of the head word */
        while (at < nvals && consumedBits != 0)
            data[at++] = (byte) readRawUInt32(8);
        /* step 2: read whole words in chunks */
        while (nvals - at >= Long.BYTES) {
            if (consumedWords < words) {
                System.arraycopy(buffer, consumedWords * Long.BYTES, data, at, Long.BYTES);
                consumedWords++;
                at += Long.BYTES;
                if (readLimitSet)
                    readLimit = (readLimit - Long.SIZE) & 0xffffffffL;
            } else
                readFromClient();
        }
        /* step 3: read any remainder from partial tail bytes */
        while (at < nvals)
            data[at++] = (byte) readRawUInt32(8);
        return data;
    }

    long readUnaryUnsigned() throws IOException {
        int val = 0;
        while (true) {
            /* if we've not consumed up to a partial tail word... */
            while (consumedWords < words) {
                long b = consumedBits < Long.SIZE ? word(consumedWords) << consumedBits : 0;
                if (b != 0) {
                    int i = Long.numberOfLeadingZeros(b);
                    val += i;
                    i++;
                    consumedBits += i;
                    /* faster way of testing if(consumedBits == Long.SIZE) */
                    if (consumedBits >= Long.SIZE) {
                        consumedWords++;
                        consumedBits = 0;
                    }
                    return Integer.toUnsignedLong(val);
                }
                /* didn't find stop bit yet, have to keep going... */
                val += Long.SIZE - consumedBits;
                consumedWords++;
                consumedBits = 0;
            }
            /*
             * at this point we've eaten up all the whole words; have to try
             * reading through any tail bytes before calling the read callback.
             * this is a repeat of the above logic adjusted for the fact we
             * don't have a whole word.  note though if the client is feeding
             * us data a byte at a time (unlikely), consumedBits may not
             * be zero.
             */
            if (bytes * 8 > consumedBits) {
                int end = bytes * 8;
                long b = word(consumedWords) << consumedBits;
                if (b != 0) {
                    int i = Long.numberOfLeadingZeros(b);
                    val += i;
                    i++;
                    consumedBits += i;
                    return Integer.toUnsignedLong(val);
                }
                /* didn't find stop bit yet, have to keep going... */
                val += end - consumedBits;
                consumedBits = end;
            }
            readFromClient();
        }
    }

    /* this is by far the most heavily used reader call.  it ain't pretty but it's fast */
    void readRiceSignedBlock(int[] vals, int offset, int nvals, int parameter) throws IOException {
        /* Maximal msbs that can occur with residual bounded to int */
        int limit = -1 >>> parameter;
        int val = offset, end = offset + nvals;
        /*
         * Checking limit here would be overzealous: coding 0xffffffff
         * with parameter == 0 would take 4GiB
         */
        if (parameter == 0) {
            while (val < end) {
                /* read the unary MSBs and end bit */
                int msbs = (int) readUnaryUnsigned();
                vals[val++] = (msbs >>> 1) ^ -(msbs & 1);
            }
            return;
        }
        /*
         * try and get consumedWords and consumedBits into local variables;
         * must remember to flush them back to the reader fields before calling other
         * bitreader functions that use them, and before returning
         */
        int cwords = consumedWords, words = this.words,
                /* keep track of the number of unconsumed bits in word */ ucbits = 0, x = 0, msbs = 0;
        long b = 0;

        int tail = cwords >= words ? 1 : 0;
        /* if we've not consumed up to a partial tail word... */
        if (tail == 0) {
            ucbits = Long.SIZE - consumedBits;
            /* keep unconsumed bits aligned to left */
            b = word(cwords) << consumedBits;
        }
        while (val < end || tail != 0) {
            if (tail == 0) {
                /* read the unary MSBs and end bit */
                int y = Long.numberOfLeadingZeros(b);
                x = y;
                if (x == Long.SIZE) {
                    x = ucbits;
                    do {
                        /* didn't find stop bit yet, have to keep going... */
                        cwords++;
                        if (cwords >= words) {
                            tail = 2;
                            break;
                        }
                        b = word(cwords);
                        y = Long.numberOfLeadingZeros(b);
                        x += y;
                    } while (y == Long.SIZE);
                }
                if (tail == 0) {
                    b = y == Long.SIZE ? 0 : b << y;
                    /* account for stop bit */
                    b <<= 1;
                    ucbits = (ucbits - x - 1) & (Long.SIZE - 1);
                    msbs = x;
                    if (Integer.compareUnsigned(x, limit) > 0)
                        throw new InvalidRice();
                    /* read the binary LSBs */

                    /* parameter < 32, so we can cast to 32-bit int */
                    x = (int) (b >>> (Long.SIZE - parameter));
                    if (parameter <= ucbits) {
                        ucbits -= parameter;
                        b <<= parameter;
                    } else {
                        /* there are still bits left to read, they will all be in the next word */
                        cwords++;
                        if (cwords >= words)
                            tail = 3;
                        else {
                            b = word(cwords);
                            ucbits += Long.SIZE - parameter;
                            x |= (int) (b >>> ucbits);
                            b = ucbits == 0 ? 0 : b << (Long.SIZE - ucbits);
                        }
                    }
                    if (tail == 0) {
                        int lsbs = x;
                        x = (msbs << parameter) | lsbs;
                        vals[val++] = (x >>> 1) ^ -(x & 1);
                        continue;
                    }
                }
            }
            /* at this point we've eaten up all the whole words */
            if (tail == 2) {
                consumedBits = 0;
                consumedWords = cwords;
            }
            if (tail != 3) {
                /* read the unary MSBs and end bit */
                msbs = (int) readUnaryUnsigned();
                msbs += x;
                x = ucbits = 0;
            } else {
                consumedBits = 0;
                consumedWords = cwords;
            }
            /* read the binary LSBs */
            int lsbs = readRawUInt32(parameter - ucbits);
            lsbs = x | lsbs;
            /* compose the value */
            x = (msbs << parameter) | lsbs;
            vals[val++] = (x >>> 1) ^ -(x & 1);
            x = 0;
            cwords = consumedWords;
            words = this.words;
            ucbits = Long.SIZE - consumedBits;
            b = cwords < buffer.length / Long.BYTES ? word(cwords) << consumedBits : 0;
            tail = cwords >= words && val < end ? 1 : 0;
        }
        /* don't leave the head word with no unconsumed bits */
        if (ucbits == 0 && cwords < words) {
            cwords++;
            ucbits = Long.SIZE;
        }
        consumedBits = Long.SIZE - ucbits;
        consumedWords = cwords;
    }

    /* on return, -1 means the utf-8 sequence was invalid */
    long readUtf8UInt32(byte[] raw, int[] rawlen) throws IOException {
        int v = 0;
        int x = readRawUInt32(8), i;
        if (raw != null)
            raw[rawlen[0]++] = (byte) x;
        if ((x & 0x80) == 0) {
            /* 0xxxxxxx */
            v = x;
            i = 0;
        } else if ((x & 0xc0) != 0 && (x & 0x20) == 0) {
            /* 110xxxxx */
            v = x & 0x1f;
            i = 1;
        } else if ((x & 0xe0) != 0 && (x & 0x10) == 0) {
            /* 1110xxxx */
            v = x & 0x0f;
            i = 2;
        } else if ((x & 0xf0) != 0 && (x & 0x08) == 0) {
            /* 11110xxx */
            v = x & 0x07;
            i = 3;
        } else if ((x & 0xf8) != 0 && (x & 0x04) == 0) {
            /* 111110xx */
            v = x & 0x03;
            i = 4;
        } else if ((x & 0xfc) != 0 && (x & 0x02) == 0) {
            /* 1111110x */
            v = x & 0x01;
            i = 5;
        } else
            return -1;
        for (; i > 0; i--) {
            x = readRawUInt32(8);
            if (raw != null)
                raw[rawlen[0]++] = (byte) x;
            /* 10xxxxxx */
            if ((x & 0x80) == 0 || (x & 0x40) != 0)
                return -1;
            v <<= 6;
            v |= x & 0x3f;
        }
        return Integer.toUnsignedLong(v);
    }

    /* on return, -1 means the utf-8 sequence was invalid */
    long readUtf8UInt64(byte[] raw, int[] rawlen) throws IOException {
        long v = 0;
        int x = readRawUInt32(8), i;
        if (raw != null)
            raw[rawlen[0]++] = (byte) x;
        if ((x & 0x80) == 0) {
            /* 0xxxxxxx */
            v = x;
            i = 0;
        } else if ((x & 0xc0) != 0 && (x & 0x20) == 0) {
            /* 110xxxxx */
            v = x & 0x1f;
            i = 1;
        } else if ((x & 0xe0) != 0 && (x & 0x10) == 0) {
            /* 1110xxxx */
            v = x & 0x0f;
            i = 2;
        } else if ((x & 0xf0) != 0 && (x & 0x08) == 0) {
            /* 11110xxx */
            v = x & 0x07;
            i = 3;
        } else if ((x & 0xf8) != 0 && (x & 0x04) == 0) {
            /* 111110xx */
            v = x & 0x03;
            i = 4;
        } else if ((x & 0xfc) != 0 && (x & 0x02) == 0) {
            /* 1111110x */
            v = x & 0x01;
            i = 5;
        } else if ((x & 0xfe) != 0 && (x & 0x01) == 0) {
            /* 11111110 */
            v = 0;
            i = 6;
        } else
            return -1;
        for (; i > 0; i--) {
            x = readRawUInt32(8);
            if (raw != null)
                raw[rawlen[0]++] = (byte) x;
            /* 10xxxxxx */
            if ((x & 0x80) == 0 || (x & 0x40) != 0)
                return -1;
            v <<= 6;
            v |= x & 0x3f;
        }
        return v;
    }

    static final class EndOfInput extends IOException {
    }

    static final class LimitExceeded extends IOException {
    }

    static final class InvalidRice extends IOException {
    }
}
