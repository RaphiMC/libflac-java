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

/**
 * FLAC PICTURE structure.  (See the
 * <A HREF="https://xiph.org/flac/format.html#metadata_block_picture">format specification</A>
 * for the full description of each field.)
 */
public final class Picture extends StreamMetadata {
    public Picture() {
        super(MetadataType.PICTURE.ordinal());
    }

    /** The kind of picture stored. */
    int pictureType = 0;
    /**
     * Picture data's MIME type, in ASCII printable characters
     * 0x20-0x7e.  For best compatibility with players,
     * use picture data of MIME type {@code image/jpeg} or {@code image/png}.  A
     * MIME type of '--&gt;' is also allowed, in which case the picture
     * data should be a complete URL.  In file storage, the MIME type is
     * stored as a 32-bit length followed by the ASCII string with no NUL
     * terminator, but is converted to a plain String in this structure
     * for convenience.
     */
    String mimeType = "";
    /**
     * Picture's description in UTF-8.  In file storage,
     * the description is stored as a 32-bit length followed by the UTF-8
     * string with no NUL terminator, but is converted to a plain String
     * in this structure for convenience.
     */
    String description = "";
    /** Picture's width in pixels. */
    int width = 0;
    /** Picture's height in pixels. */
    int height = 0;
    /** Picture's color depth in bits-per-pixel. */
    int depth = 0;
    /**
     * For indexed palettes (like GIF), picture's number of colors (the
     * number of palette entries), or {@code 0} for non-indexed (i.e. 2^depth).
     */
    int colors = 0;
    /** Binary picture data. */
    byte[] data = new byte[0];

    public int getPictureType() {
        return pictureType;
    }

    public void setPictureType(int value) {
        pictureType = value;
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the MIME type of a PICTURE block.
     *
     * @param mimeType The MIME type String.  The string must be
     *                    ASCII characters 0x20-0x7e.  No validation
     *                    is done.
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of a PICTURE block.
     *
     * @param description The description string.  The string must be
     *                    valid UTF-8.  No validation is done.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int value) {
        width = value;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int value) {
        height = value;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int value) {
        depth = value;
    }

    public int getColors() {
        return colors;
    }

    public void setColors(int value) {
        colors = value;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Sets the picture data of a PICTURE block.
     *
     * @param data The data to set.
     */
    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Sets the picture data of a PICTURE block.
     *
     *  If {@code copy} is {@code true}, a copy of the data is stored; otherwise, the object
     *  retains the array.
     *
     * @param data The data to set.
     * @param copy See above.
     */
    public void setData(byte[] data, boolean copy) {
        /* do the copy first so that if we fail we leave the object untouched */
        this.data = data == null && !copy ? new byte[0] : copy ? data.clone() : data;
    }

    /**
     * Check a PICTURE block to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  PICTURE block.
     *
     * @return A violation message if illegal, else null.
     */
    public String checkLegal() {
        for (int i = 0; i < mimeType.length(); i++) {
            char c = mimeType.charAt(i);
            if (c < 0x20 || c > 0x7e)
                return "MIME type string must contain only printable ASCII characters (0x20-0x7e)";
        }
        return Format.vorbiscommentEntryValueIsLegal(description) ? null : "description string must be valid UTF-8";
    }

    /**
     * Check picture data to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  PICTURE block.
     *
     * @return {@code false} if picture data is illegal, else {@code true}.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public boolean isLegal() {
        return checkLegal() == null;
    }
}
