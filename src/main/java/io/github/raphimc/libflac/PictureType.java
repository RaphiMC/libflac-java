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

/** An enumeration of the PICTURE types (see Picture and id3 v2.4 APIC tag). */
public enum PictureType {
    /** Other */
    OTHER("Other"),
    /** 32x32 pixels 'file icon' (PNG only) */
    FILE_ICON_STANDARD("32x32 pixels 'file icon' (PNG only)"),
    /** Other file icon */
    FILE_ICON("Other file icon"),
    /** Cover (front) */
    COVER_FRONT("Cover (front)"),
    /** Cover (back) */
    COVER_BACK("Cover (back)"),
    /** Leaflet page */
    LEAFLET_PAGE("Leaflet page"),
    /** Media (e.g. label side of CD) */
    MEDIA("Media (e.g. label side of CD)"),
    /** Lead artist/lead performer/soloist */
    LEAD_ARTIST("Lead artist/lead performer/soloist"),
    /** Artist/performer */
    ARTIST("Artist/performer"),
    /** Conductor */
    CONDUCTOR("Conductor"),
    /** Band/Orchestra */
    BAND("Band/Orchestra"),
    /** Composer */
    COMPOSER("Composer"),
    /** Lyricist/text writer */
    LYRICIST("Lyricist/text writer"),
    /** Recording Location */
    RECORDING_LOCATION("Recording Location"),
    /** During recording */
    DURING_RECORDING("During recording"),
    /** During performance */
    DURING_PERFORMANCE("During performance"),
    /** Movie/video screen capture */
    VIDEO_SCREEN_CAPTURE("Movie/video screen capture"),
    /** A bright coloured fish */
    FISH("A bright coloured fish"),
    /** Illustration */
    ILLUSTRATION("Illustration"),
    /** Band/artist logotype */
    BAND_LOGOTYPE("Band/artist logotype"),
    /** Publisher/Studio logotype */
    PUBLISHER_LOGOTYPE("Publisher/Studio logotype"),
    UNDEFINED("UNDEFINED");

    private final String description;

    PictureType(String description) {
        this.description = description;
    }

    /** Maps a PictureType to a String. */
    public String getDescription() {
        return description;
    }
}
