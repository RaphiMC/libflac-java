/* libFLAC - Free Lossless Audio Codec library
 * Copyright (C) 2001-2009  Josh Coalson
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
 * FLAC CUESHEET structure.  (See the
 * <A HREF="https://xiph.org/flac/format.html#metadata_block_cuesheet">format specification</A>
 * for the full description of each field.)
 */
public final class CueSheet extends StreamMetadata {
    public CueSheet() {
        super(MetadataType.CUESHEET.ordinal());
    }

    /**
     * Media catalog number, in ASCII printable characters 0x20-0x7e.  In
     * general, the media catalog number may be 0 to 128 bytes long.
     */
    String mediaCatalogNumber = "";
    /** The number of lead-in samples. */
    long leadIn = 0;
    /** {@code true} if CUESHEET corresponds to a Compact Disc, else {@code false}. */
    boolean isCd = false;
    /** Array of tracks. */
    CueSheetTrack[] tracks = new CueSheetTrack[0];

    public String getMediaCatalogNumber() {
        return mediaCatalogNumber;
    }

    public void setMediaCatalogNumber(String value) {
        mediaCatalogNumber = value;
    }

    public long getLeadIn() {
        return leadIn;
    }

    public void setLeadIn(long value) {
        leadIn = value;
    }

    public boolean getCd() {
        return isCd;
    }

    public void setCd(boolean value) {
        isCd = value;
    }

    public CueSheetTrack[] getTracks() {
        return tracks;
    }

    public void setTracks(CueSheetTrack[] value) {
        tracks = value;
    }

    public int getNumTracks() {
        return tracks.length;
    }

    /**
     * Resize the track array.
     *
     *  If the size shrinks, elements will truncated; if it grows, new blank
     *  tracks will be added to the end.
     *
     * @param newNumTracks The desired length of the array; may be {@code 0}.
     */
    public void resizeTracks(int newNumTracks) {
        int old = tracks.length;
        tracks = Arrays.copyOf(tracks, newNumTracks);
        for (int i = old; i < newNumTracks; i++)
            tracks[i] = new CueSheetTrack();
    }

    /**
     * Sets a track in a CUESHEET block.
     *
     *  If {@code copy} is {@code true}, a copy of the track is stored; otherwise, the object
     *  retains the {@code track} reference.
     *
     * @param trackNum Index into track array to set.  NOTE: this is not
     *                     necessarily the same as the track's {@code number} field.
     * @param track The track to set the track to.
     * @param copy See above.
     */
    public void setTrack(int trackNum, CueSheetTrack track, boolean copy) {
        tracks[trackNum] = copy ? track.copy() : track;
    }

    /**
     * Insert a track in a CUESHEET block at the given index.
     *
     *  If {@code copy} is {@code true}, a copy of the track is stored; otherwise, the object
     *  retains the {@code track} reference.
     *
     * @param trackNum The index at which to insert the track.  NOTE: this
     *                     is not necessarily the same as the track's {@code number}
     *                     field.  The tracks at and after {@code trackNum} move right
     *                     one position.  To append a track to the end, set
     *                     {@code trackNum} to {@code getNumTracks()} .
     * @param track The track to insert.
     * @param copy See above.
     */
    public void insertTrack(int trackNum, CueSheetTrack track, boolean copy) {
        Objects.checkIndex(trackNum, tracks.length + 1);
        resizeTracks(tracks.length + 1);
        /* move all tracks >= trackNum forward one space */
        System.arraycopy(tracks, trackNum, tracks, trackNum + 1, tracks.length - 1 - trackNum);
        setTrack(trackNum, track, copy);
    }

    /**
     * Insert a blank track in a CUESHEET block at the given index.
     *
     *  A blank track is one in which all fields have their default values.
     *
     * @param trackNum The index at which to insert the track.  NOTE: this
     *                     is not necessarily the same as the track's {@code number}
     *                     field.  The tracks at and after {@code trackNum} move right
     *                     one position.  To append a track to the end, set
     *                     {@code trackNum} to {@code getNumTracks()} .
     */
    public void insertBlankTrack(int trackNum) {
        insertTrack(trackNum, new CueSheetTrack(), false);
    }

    /**
     * Delete a track in a CUESHEET block at the given index.
     *
     * @param trackNum The index into the track array of the track to
     *                     delete.  NOTE: this is not necessarily the same
     *                     as the track's {@code number} field.
     */
    public void deleteTrack(int trackNum) {
        Objects.checkIndex(trackNum, tracks.length);
        /* move all tracks > trackNum backward one space */
        System.arraycopy(tracks, trackNum + 1, tracks, trackNum, tracks.length - trackNum - 1);
        resizeTracks(tracks.length - 1);
    }

    /**
     * Check a cue sheet to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  cue sheet.
     *
     * @param checkCdDaSubset If {@code true}, check CUESHEET against more
     *                   stringent requirements for a CD-DA (audio) disc.
     * @return A violation message if illegal, else null.
     */
    public String checkLegal(boolean checkCdDaSubset) {
        if (checkCdDaSubset) {
            if (Long.compareUnsigned(leadIn, 2 * 44100) < 0)
                return "CD-DA cue sheet must have a lead-in length of at least 2 seconds";
            if (Long.remainderUnsigned(leadIn, 588) != 0)
                return "CD-DA cue sheet lead-in length must be evenly divisible by 588 samples";
        }
        if (tracks.length == 0)
            return "cue sheet must have at least one track (the lead-out)";
        /* the lead-out track... */
        if (checkCdDaSubset && tracks[tracks.length - 1].number != 170)
            return "CD-DA cue sheet must have a lead-out track number 170 (0xAA)";
        for (int i = 0; i < tracks.length; i++) {
            CueSheetTrack t = tracks[i];
            if (t.number == 0)
                return "cue sheet may not have a track number 0";
            if (checkCdDaSubset && !((t.number >= 1 && t.number <= 99) || t.number == 170))
                return "CD-DA cue sheet track number must be 1-99 or 170";
            if (checkCdDaSubset && Long.remainderUnsigned(t.offset, 588) != 0)
                return i == tracks.length - 1
                        ? "CD-DA cue sheet lead-out offset must be evenly divisible by 588 samples"
                        : "CD-DA cue sheet track offset must be evenly divisible by 588 samples";
            if (i < tracks.length - 1) {
                if (t.indices.length == 0)
                    return "cue sheet track must have at least one index point";
                if (t.indices[0].number > 1)
                    return "cue sheet track's first index number must be 0 or 1";
            }
            for (int j = 0; j < t.indices.length; j++) {
                if (checkCdDaSubset && Long.remainderUnsigned(t.indices[j].offset, 588) != 0)
                    return "CD-DA cue sheet track index offset must be evenly divisible by 588 samples";
                if (j > 0 && t.indices[j].number != t.indices[j - 1].number + 1)
                    return "cue sheet track index numbers must increase by 1";
            }
        }
        return null;
    }

    /**
     * Check a cue sheet to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  cue sheet.
     *
     * @param checkCdDaSubset If {@code true}, check CUESHEET against more
     *                   stringent requirements for a CD-DA (audio) disc.
     * @return {@code false} if cue sheet is illegal, else {@code true}.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public boolean isLegal(boolean checkCdDaSubset) {
        return checkLegal(checkCdDaSubset) == null;
    }

    private long getIndex01Offset(int trackNum) {
        if (trackNum >= tracks.length - 1 || tracks[trackNum].indices.length < 1)
            return 0;
        CueSheetTrack track = tracks[trackNum];
        if (track.indices[0].number == 1)
            return track.indices[0].offset + track.offset + leadIn;
        else if (track.indices.length < 2)
            return 0;
        else if (track.indices[1].number == 1)
            return track.indices[1].offset + track.offset + leadIn;
        else
            return 0;
    }

    private static int cddbAddDigits(int x) {
        int n = 0;
        while (x != 0) {
            n += Integer.remainderUnsigned(x, 10);
            x = Integer.divideUnsigned(x, 10);
        }
        return n;
    }

    /**
     * Calculate and return the CDDB/freedb ID for a cue sheet.  The function
     *  assumes the cue sheet corresponds to a CD; the result is undefined
     *  if the cuesheet's isCd flag is not set.
     *
     * @return The unsigned integer representation of the CDDB/freedb ID
     */

    /* @@@@add to tests */
    public int calculateCddbId() {
        /* need at least one real track and the lead-out track */
        if (tracks.length < 2)
            return 0;
        int sum = 0;
        /* -1 to avoid counting the lead-out */
        for (int i = 0; i < tracks.length - 1; i++) {
            sum += cddbAddDigits((int) Long.divideUnsigned(getIndex01Offset(i), 44100));
        }
        int length = (int) Long.divideUnsigned(tracks[tracks.length - 1].offset + leadIn, 44100)
                - (int) Long.divideUnsigned(getIndex01Offset(0), 44100);
        return ((sum % 0xFF) << 24) | (length << 8) | (tracks.length - 1);
    }

}
