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
import java.util.Arrays;

final class BitWriter {
    /**
     * The default capacity here doesn't matter too much.  The buffer always grows
     * to hold whatever is written to it.  Usually the encoder will stop adding at
     * a frame or metadata block, then write that out and clear the buffer for the
     * next one.
     *
     * size in words
     */
    static final int BITWRITER_DEFAULT_CAPACITY = 32768 / Long.BYTES;
    /**
     * When growing, increment 4K at a time
     *
     * size in words
     */
    private static final int BITWRITER_DEFAULT_INCREMENT = 4096 / Long.BYTES;

    private byte[] buffer = new byte[BITWRITER_DEFAULT_CAPACITY * Long.BYTES];
    /** accumulator; bits are right-justified; when full, accum is appended to buffer */
    private long accum;
    /**
     * words: # of complete words in buffer
     *
     * bits: # of used bits in accum
     */
    private int words, bits;

    /* WATCHOUT: The current implementation only grows the buffer. */
    private void grow(long bitsToAdd) throws IOException {
        /* calculate total words needed to store 'bitsToAdd' additional bits */
        long newCapacity = words + ((bits + bitsToAdd + Long.SIZE - 1) / Long.SIZE);
        int capacity = buffer.length / Long.BYTES;
        /*
         * it's possible (due to pessimism in the growth estimation that
         * leads to this call) that we don't actually need to grow
         */
        if (capacity >= newCapacity)
            return;
        /*
         * Requested new capacity is larger than the largest possible metadata block,
         * which is also larger than the largest sane framesize. That means something
         * went very wrong somewhere and previous checks failed.
         * To prevent chrashing, give up
         */
        if (newCapacity * Long.BYTES > (1L << Format.STREAM_METADATA_LENGTH_LEN))
            throw new IOException("bit writer capacity exceeds the maximum metadata block size");
        if ((newCapacity - capacity) % BITWRITER_DEFAULT_INCREMENT != 0)
            /* round up capacity increase to the nearest BitWriter.BITWRITER_DEFAULT_INCREMENT */
            newCapacity += BITWRITER_DEFAULT_INCREMENT - ((newCapacity - capacity) % BITWRITER_DEFAULT_INCREMENT);
        buffer = Arrays.copyOf(buffer, (int) newCapacity * Long.BYTES);
    }

    void clear() {
        words = bits = 0;
    }

    int getWriteCrc16() throws IOException {
        flushAccumulator();
        return Crc.crc16(buffer, 0, words * Long.BYTES + bits / Byte.SIZE);
    }

    int getWriteCrc8() throws IOException {
        flushAccumulator();
        return Crc.crc8(buffer, 0, words * Long.BYTES + bits / Byte.SIZE);
    }

    /** info functions */
    boolean isByteAligned() {
        return (bits & 7) == 0;
    }

    /** can be called anytime, returns total # of bits unconsumed */
    int getInputBitsUnconsumed() {
        return words * Long.SIZE + bits;
    }

    /** Before get, bitwriter MUST be byte aligned: check with BitWriter.isByteAligned() */
    byte[] getBuffer() throws IOException {
        flushAccumulator();
        /* now we can just return what we have */
        return Arrays.copyOf(buffer, words * Long.BYTES + bits / Byte.SIZE);
    }

    private void flushAccumulator() throws IOException {
        if (!isByteAligned())
            throw new IllegalStateException("bit writer is not byte aligned");
        /* if we have bits in the accumulator we have to flush those to the buffer first */
        if (bits != 0) {
            if (words == buffer.length / Long.BYTES)
                grow(Long.SIZE);
            /* append bits as complete word to buffer, but don't change accum or bits */
            putWord(words, accum << (Long.SIZE - bits));
        }
    }

    /** write functions */
    void writeZeroes(long bits) throws IOException {
        if (bits < 0 || bits > 0xffffffffL)
            throw new IllegalArgumentException();
        if (bits == 0)
            return;
        grow(bits);
        /* first part gets to word alignment */
        if (this.bits != 0) {
            int n = (int) Math.min(Long.SIZE - this.bits, bits);
            accum <<= n;
            bits -= n;
            this.bits += n;
            if (this.bits == Long.SIZE) {
                putWord(words++, accum);
                this.bits = 0;
            } else
                return;
        }
        /* do whole words */
        while (bits >= Long.SIZE) {
            putWord(words++, 0);
            bits -= Long.SIZE;
        }
        /* do any leftovers */
        if (bits > 0) {
            accum = 0;
            this.bits = (int) bits;
        }
    }

    private void writeRawUInt32NoCheck(int val, int bits) throws IOException {
        if (bits == 0)
            return;
        grow(bits);
        int left = Long.SIZE - this.bits;
        if (bits < left) {
            accum <<= bits;
            accum |= Integer.toUnsignedLong(val);
            this.bits += bits;
        } else {
            accum <<= left;
            accum |= Integer.toUnsignedLong(val) >>> (this.bits = bits - left);
            putWord(words++, accum);
            /* unused top bits can contain garbage */
            accum = Integer.toUnsignedLong(val);
        }
    }

    private void putWord(int index, long val) {
        for (int i = 0; i < Long.BYTES; i++)
            buffer[index * Long.BYTES + i] = (byte) (val >>> (Long.SIZE - Byte.SIZE - i * Byte.SIZE));
    }

    void writeRawUInt32(int val, int bits) throws IOException {
        if (bits < 0 || bits > Integer.SIZE)
            throw new IllegalArgumentException("bit count");
        /* check that unused bits are unset */
        if (bits < Integer.SIZE && val >>> bits != 0)
            throw new IllegalArgumentException("unsigned value exceeds bit count");
        writeRawUInt32NoCheck(val, bits);
    }

    void writeRawInt32(int val, int bits) throws IOException {
        if (bits < 0 || bits > Integer.SIZE)
            throw new IllegalArgumentException("bit count");
        if (bits < Integer.SIZE)
            /* zero-out unused bits */
            val &= ~(-1 << bits);
        writeRawUInt32NoCheck(val, bits);
    }

    /* this could be a little faster but it's not used for much */
    void writeRawUInt64(long val, int bits) throws IOException {
        if (bits < 0 || bits > Long.SIZE)
            throw new IllegalArgumentException("bit count");
        if (bits > Integer.SIZE) {
            writeRawUInt32((int) (val >>> Integer.SIZE), bits - Integer.SIZE);
            writeRawUInt32NoCheck((int) val, Integer.SIZE);
        } else
            writeRawUInt32((int) val, bits);
    }

    void writeRawInt64(long val, int bits) throws IOException {
        if (bits < 0 || bits > Long.SIZE)
            throw new IllegalArgumentException("bit count");
        long uval = val;
        /* zero-out unused bits */
        if (bits < Long.SIZE)
            uval &= ~(-1L << bits);
        writeRawUInt64(uval, bits);
    }

    /** only for bits=32 */
    void writeRawUInt32LittleEndian(int val) throws IOException {
        /* this doesn't need to be that fast as currently it is only used for vorbis comments */
        writeRawUInt32NoCheck(Integer.reverseBytes(val), Integer.SIZE);
    }

    void writeByteBlock(byte[] vals) throws IOException {
        /* grow capacity upfront to prevent constant reallocation during writes */
        grow((long) vals.length * Byte.SIZE);
        /* this could be faster but currently we don't need it to be since it's only used for writing metadata */
        for (byte b : vals)
            writeRawUInt32NoCheck(b & 255, Byte.SIZE);
    }

    void writeUnaryUnsigned(long val) throws IOException {
        if (val < 0 || val > 0xffffffffL)
            throw new IllegalArgumentException("unary value");
        if (val < Integer.SIZE)
            writeRawUInt32NoCheck(1, (int) val + 1);
        else {
            writeZeroes(val);
            writeRawUInt32NoCheck(1, 1);
        }
    }

    private long writeRiceWord(long wideAccum) {
        if (bits == 0) {
            accum = wideAccum >>> Integer.SIZE;
            bits = Integer.SIZE;
        } else {
            accum <<= Integer.SIZE;
            accum += wideAccum >>> Integer.SIZE;
            putWord(words++, accum);
            bits = 0;
        }
        return wideAccum << Integer.SIZE;
    }

    void writeRiceSignedBlock(int[] vals, int offset, int nvals, int parameter) throws IOException {
        /* we uval|=mask1 to set the stop bit above it... */
        int mask1 = -1 << parameter,
                /* ...then mask off the bits above the stop bit with uval&=mask2 */ mask2 = -1 >>> (31 - parameter),
                lsbits = 1 + parameter;
        long wideAccum = 0;
        int bitpointer = Long.SIZE;
        if (bits > 0 && bits < Integer.SIZE) {
            bitpointer -= bits;
            wideAccum = accum << bitpointer;
            bits = 0;
        } else if (bits > Integer.SIZE) {
            bitpointer -= bits - Integer.SIZE;
            wideAccum = accum << bitpointer;
            accum >>>= bits - Integer.SIZE;
            bits = Integer.SIZE;
        }
        /*
         * Reserve one Long.SIZE per symbol, so checks for space are only necessary when very large symbols are encountered
         * this might be considered wasteful, but is only at most 8kB more than necessary for a blocksize of 4096
         */
        grow((long) nvals * Long.SIZE);
        while (nvals > 0) {
            /* fold signed to int; actual formula is: negative(v)? -2v-1 : 2v */
            int uval = vals[offset];
            uval <<= 1;
            uval ^= vals[offset] >> 31;
            long msbits = Integer.toUnsignedLong(uval) >>> parameter;
            long totalBits = lsbits + msbits;
            /* set stop bit */
            uval |= mask1;
            /* mask off unused top bits */
            uval &= mask2;
            if (totalBits <= bitpointer) {
                /* There is room enough to store the symbol whole at once */
                wideAccum |= Integer.toUnsignedLong(uval) << (bitpointer - totalBits);
                bitpointer -= (int) totalBits;
                if (bitpointer <= Integer.SIZE) {
                    /* A word is finished, copy the upper 32 bits of the wideAccum */
                    wideAccum = writeRiceWord(wideAccum);
                    bitpointer += Integer.SIZE;
                }
            } else {
                /* The symbol needs to be split. This code isn't used often */

                /* First check for space in the bitwriter */
                if (totalBits > Long.SIZE)
                    grow((long) nvals * Long.SIZE + totalBits - Long.SIZE);
                if (msbits > bitpointer) {
                    /* We have a lot of 0 bits to write, first align with bitwriter word */
                    msbits -= bitpointer - Integer.SIZE;
                    bitpointer = Integer.SIZE;
                    wideAccum = writeRiceWord(wideAccum);
                    bitpointer += Integer.SIZE;
                    while (msbits > bitpointer) {
                        /*
                         * As the accumulator is already zero, we only need to
                         * assign zeroes to the bitbuffer
                         */
                        wideAccum = writeRiceWord(wideAccum);
                        msbits -= Integer.SIZE;
                    }
                    /*
                     * The remaining bits are zero, and the accumulator already is zero,
                     * so just subtract the number of bits from bitpointer. When storing,
                     * we can also just store 0
                     */
                    bitpointer -= (int) msbits;
                    if (bitpointer <= Integer.SIZE) {
                        wideAccum = writeRiceWord(wideAccum);
                        bitpointer += Integer.SIZE;
                    }
                } else {
                    bitpointer -= (int) msbits;
                    if (bitpointer <= Integer.SIZE) {
                        wideAccum = writeRiceWord(wideAccum);
                        bitpointer += Integer.SIZE;
                    }
                }
                /* The lsbs + stop bit always fit 32 bit, so this code mirrors the code above */
                wideAccum |= Integer.toUnsignedLong(uval) << (bitpointer - lsbits);
                bitpointer -= lsbits;
                if (bitpointer <= Integer.SIZE) {
                    /* A word is finished, copy the upper 32 bits of the wideAccum */
                    wideAccum = writeRiceWord(wideAccum);
                    bitpointer += Integer.SIZE;
                }
            }
            offset++;
            nvals--;
        }
        /* Now fixup remainder of wideAccum */
        if (bitpointer < Long.SIZE) {
            if (bits == 0) {
                accum = wideAccum >>> bitpointer;
                bits = Long.SIZE - bitpointer;
            } else {
                accum <<= Long.SIZE - bitpointer;
                accum |= wideAccum >>> bitpointer;
                bits = Integer.SIZE + Long.SIZE - bitpointer;
            }
        }
    }

    void writeUtf8UInt32(int val) throws IOException {
        /* this version only handles 31 bits */
        if ((val & 0x80000000) != 0)
            throw new IllegalArgumentException("FLAC UTF-8 frame number");

        if (val < 0x80) {
            writeRawUInt32NoCheck(val, 8);
        } else if (val < 0x800) {
            writeRawUInt32NoCheck(0xC0 | (val >>> 6), 8);
            writeRawUInt32NoCheck(0x80 | (val & 0x3F), 8);
        } else if (val < 0x10000) {
            writeRawUInt32NoCheck(0xE0 | (val >>> 12), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (val & 0x3F), 8);
        } else if (val < 0x200000) {
            writeRawUInt32NoCheck(0xF0 | (val >>> 18), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (val & 0x3F), 8);
        } else if (val < 0x4000000) {
            writeRawUInt32NoCheck(0xF8 | (val >>> 24), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 18) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (val & 0x3F), 8);
        } else {
            writeRawUInt32NoCheck(0xFC | (val >>> 30), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 24) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 18) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (val & 0x3F), 8);
        }
    }

    void writeUtf8UInt64(long val) throws IOException {
        /* this version only handles 36 bits */
        if ((val & 0xFFFFFFF000000000L) != 0)
            throw new IllegalArgumentException("FLAC UTF-8 integer");

        if (val < 0x80) {
            writeRawUInt32NoCheck((int) val, 8);
        } else if (val < 0x800) {
            writeRawUInt32NoCheck(0xC0 | (int) (val >>> 6), 8);
            writeRawUInt32NoCheck(0x80 | (int) (val & 0x3F), 8);
        } else if (val < 0x10000) {
            writeRawUInt32NoCheck(0xE0 | (int) (val >>> 12), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) (val & 0x3F), 8);
        } else if (val < 0x200000) {
            writeRawUInt32NoCheck(0xF0 | (int) (val >>> 18), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) (val & 0x3F), 8);
        } else if (val < 0x4000000) {
            writeRawUInt32NoCheck(0xF8 | (int) (val >>> 24), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 18) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) (val & 0x3F), 8);
        } else if (val < 0x80000000L) {
            writeRawUInt32NoCheck(0xFC | (int) (val >>> 30), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 24) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 18) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) (val & 0x3F), 8);
        } else {
            writeRawUInt32NoCheck(0xFE, 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 30) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 24) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 18) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 12) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) ((val >>> 6) & 0x3F), 8);
            writeRawUInt32NoCheck(0x80 | (int) (val & 0x3F), 8);
        }
    }

    void zeroPadToByteBoundary() throws IOException {
        /* 0-pad to byte boundary */
        if ((bits & 7) != 0)
            writeZeroes(8 - (bits & 7));
    }
}
