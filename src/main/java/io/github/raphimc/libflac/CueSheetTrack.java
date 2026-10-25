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

import java.util.Arrays;
import java.util.Objects;

/**
 * FLAC CUESHEET track structure.  (See the
 * <A HREF="https://xiph.org/flac/format.html#cuesheet_track">format specification</A> for
 * the full description of each field.)
 */
public final class CueSheetTrack {
    /** Track offset in samples, relative to the beginning of the FLAC audio stream. */
    long offset = 0;
    /** The track number. */
    int number = 0;
    /** Track ISRC.  This is a 12-digit alphanumeric code */
    String isrc = "";
    /** The track type: 0 for audio, 1 for non-audio. */
    int type = 0;
    /** The pre-emphasis flag: false for no pre-emphasis, true for pre-emphasis. */
    boolean preEmphasis = false;
    /** Array of index points. */
    CueSheetIndex[] indices = new CueSheetIndex[0];

    public long getOffset() {
        return offset;
    }

    public void setOffset(long value) {
        offset = value;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int value) {
        number = value & 255;
    }

    public String getIsrc() {
        return isrc;
    }

    public void setIsrc(String value) {
        isrc = value;
    }

    public int getType() {
        return type;
    }

    public void setType(int value) {
        type = value & 1;
    }

    public boolean getPreEmphasis() {
        return preEmphasis;
    }

    public void setPreEmphasis(boolean value) {
        preEmphasis = value;
    }

    public CueSheetIndex[] getIndices() {
        return indices;
    }

    public void setIndices(CueSheetIndex[] value) {
        indices = value;
    }

    public int getNumIndices() {
        return indices.length;
    }

    /**
     * Create a copy of an existing CUESHEET track object.
     *
     *  The copy is a "deep" copy, i.e. dynamically allocated data within the
     *  object is also copied.
     *
     * @return The new instance.
     */
    public CueSheetTrack copy() {
        CueSheetTrack c = new CueSheetTrack();
        c.offset = offset;
        c.number = number;
        c.isrc = isrc;
        c.type = type;
        c.preEmphasis = preEmphasis;
        c.indices = Arrays.stream(indices).map(CueSheetIndex::copy).toArray(CueSheetIndex[]::new);
        return c;
    }

    /**
     * Resize a track's index point array.
     *
     *  If the size shrinks, elements will truncated; if it grows, new blank
     *  indices will be added to the end.
     *
     * @param newNumIndices The desired length of the array; may be {@code 0}.
     */
    public void resizeIndices(int newNumIndices) {
        int old = indices.length;
        indices = Arrays.copyOf(indices, newNumIndices);
        for (int i = old; i < newNumIndices; i++)
            indices[i] = new CueSheetIndex();
    }

    /**
     * Insert an index point in a CUESHEET track at the given index.
     *
     * @param indexNum The index into the track's index array at which to
     *                     insert the index point.  NOTE: this is not necessarily
     *                     the same as the index point's {@code number} field.  The
     *                     indices at and after {@code indexNum} move right one
     *                     position.  To append an index point to the end, set
     *                     {@code indexNum} to
     *                     {@code getNumIndices()} .
     * @param index The index point to insert.
     */
    public void insertIndex(int indexNum, CueSheetIndex index) {
        Objects.checkIndex(indexNum, indices.length + 1);
        resizeIndices(indices.length + 1);
        /* move all indices >= indexNum forward one space */
        System.arraycopy(indices, indexNum, indices, indexNum + 1, indices.length - 1 - indexNum);
        indices[indexNum] = index.copy();
    }

    /**
     * Insert a blank index point in a CUESHEET track at the given index.
     *
     *  A blank index point is one in which all field values are zero.
     *
     * @param indexNum The index into the track's index array at which to
     *                     insert the index point.  NOTE: this is not necessarily
     *                     the same as the index point's {@code number} field.  The
     *                     indices at and after {@code indexNum} move right one
     *                     position.  To append an index point to the end, set
     *                     {@code indexNum} to
     *                     {@code getNumIndices()} .
     */
    public void insertBlankIndex(int indexNum) {
        insertIndex(indexNum, new CueSheetIndex());
    }

    /**
     * Delete an index point in a CUESHEET track at the given index.
     *
     * @param indexNum The index into the track's index array of the index
     *                     to delete.  NOTE: this is not necessarily the same
     *                     as the index's {@code number} field.
     */
    public void deleteIndex(int indexNum) {
        Objects.checkIndex(indexNum, indices.length);
        /* move all indices > indexNum backward one space */
        System.arraycopy(indices, indexNum + 1, indices, indexNum, indices.length - indexNum - 1);
        resizeIndices(indices.length - 1);
    }
}
