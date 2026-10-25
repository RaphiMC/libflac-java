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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * FLAC VORBIS_COMMENT structure.  (c.f. <A
 * HREF="https://xiph.org/flac/format.html#metadata_block_vorbis_comment">format specification</A>)
 */
public final class VorbisComment extends StreamMetadata {
    public VorbisComment() {
        super(MetadataType.VORBIS_COMMENT.ordinal());
    }

    String vendorString = Format.VENDOR_STRING;
    String[] comments = new String[0];

    public String getVendorString() {
        return vendorString;
    }

    /**
     * Sets the vendor string in a VORBIS_COMMENT block.
     *
     * @param entry The entry to set the vendor string to.
     * @return {@code false} if {@code entry} does not comply with the
     *    Vorbis comment specification, else {@code true}.
     */
    public boolean setVendorString(String entry) {
        if (!Format.vorbiscommentEntryValueIsLegal(entry))
            return false;
        vendorString = entry;
        return true;
    }

    public String[] getComments() {
        return comments;
    }

    public void setComments(String[] value) {
        comments = value;
    }

    public int getNumComments() {
        return comments.length;
    }

    /**
     * Resize the comment array.
     *
     *  If the size shrinks, elements will truncated; if it grows, new empty
     *  fields will be added to the end.
     *
     * @param newNumComments The desired length of the array; may be {@code 0}.
     */
    public void resizeComments(int newNumComments) {
        int old = comments.length;
        comments = Arrays.copyOf(comments, newNumComments);
        if (newNumComments > old)
            Arrays.fill(comments, old, newNumComments, "");
    }

    /**
     * Sets a comment in a VORBIS_COMMENT block.
     *
     * @param commentNum Index into comment array to set.
     * @param entry The entry to set the comment to.
     * @return {@code false} if {@code entry} does not comply with the
     *    Vorbis comment specification, else {@code true}.
     */
    public boolean setComment(int commentNum, String entry) {
        if (!Format.vorbiscommentEntryIsLegal(entry))
            return false;
        comments[commentNum] = entry;
        return true;
    }

    /**
     * Insert a comment in a VORBIS_COMMENT block at the given index.
     *
     * @param commentNum The index at which to insert the comment.  The comments
     *                     at and after {@code commentNum} move right one position.
     *                     To append a comment to the end, set {@code commentNum} to
     *                     {@code getNumComments()} .
     * @param entry The comment to insert.
     * @return {@code false} if {@code entry} does not comply with the
     *    Vorbis comment specification, else {@code true}.
     */
    public boolean insertComment(int commentNum, String entry) {
        if (!Format.vorbiscommentEntryIsLegal(entry))
            return false;
        Objects.checkIndex(commentNum, comments.length + 1);
        resizeComments(comments.length + 1);
        /* move all comments >= commentNum forward one space */
        System.arraycopy(comments, commentNum, comments, commentNum + 1, comments.length - 1 - commentNum);
        return setComment(commentNum, entry);
    }

    /**
     * Appends a comment to a VORBIS_COMMENT block.
     *
     * @param entry The comment to insert.
     * @return {@code false} if {@code entry} does not comply with the
     *    Vorbis comment specification, else {@code true}.
     */
    public boolean appendComment(String entry) {
        return insertComment(comments.length, entry);
    }

    /**
     * Replaces comments in a VORBIS_COMMENT block with a new one.
     *
     *  Depending on the value of {@code all}, either all or just the first comment
     *  whose field name(s) match the given entry's name will be replaced by the
     *  given entry.  If no comments match, {@code entry} will simply be appended.
     *
     * @param entry The comment to insert.
     * @param all If {@code true}, all comments whose field name matches
     *                     {@code entry}'s field name will be removed, and {@code entry} will
     *                     be inserted at the position of the first matching
     *                     comment.  If {@code false}, only the first comment whose
     *                     field name matches {@code entry}'s field name will be
     *                     replaced with {@code entry}.
     * @return {@code false} if {@code entry} does not comply with the
     *    Vorbis comment specification, else {@code true}.
     */
    public boolean replaceComment(String entry, boolean all) {
        if (!Format.vorbiscommentEntryIsLegal(entry))
            return false;
        String name = entry.substring(0, entry.indexOf('='));
        int i = findEntryFrom(0, name);
        if (i < 0)
            return appendComment(entry);
        if (!setComment(i, entry))
            return false;
        if (all) {
            ArrayList<String> list = new ArrayList<>(Arrays.asList(comments));
            /* skip over replaced comment */
            if (list.subList(i + 1, list.size()).removeIf(comment -> entryMatches(comment, name)))
                comments = list.toArray(String[]::new);
        }
        return true;
    }

    /**
     * Delete a comment in a VORBIS_COMMENT block at the given index.
     *
     * @param commentNum The index of the comment to delete.
     */
    public void deleteComment(int commentNum) {
        Objects.checkIndex(commentNum, comments.length);
        /* move all comments > commentNum backward one space */
        System.arraycopy(comments, commentNum + 1, comments, commentNum, comments.length - commentNum - 1);
        resizeComments(comments.length - 1);
    }

    /**
     * Creates a Vorbis comment entry from name and value strings.
     *
     * @param fieldName The field name in ASCII.
     * @param fieldValue The field value in UTF-8.
     * @return The entry, or null if fieldName or fieldValue does not comply with the Vorbis comment specification.
     */
    public static String entryFromNameValuePair(String fieldName, String fieldValue) {
        if (!Format.vorbiscommentEntryNameIsLegal(fieldName) || !Format.vorbiscommentEntryValueIsLegal(fieldValue))
            return null;
        return fieldName + "=" + fieldValue;
    }

    /**
     * Splits a Vorbis comment entry into name and value strings.
     *
     * @param entry An existing Vorbis comment entry.
     * @return The name and value, or null if entry does not comply with the Vorbis comment specification.
     */
    public static String[] entryToNameValuePair(String entry) {
        if (!Format.vorbiscommentEntryIsLegal(entry))
            return null;
        int p = entry.indexOf('=');
        return p < 0 ? null : new String[] {entry.substring(0, p), entry.substring(p + 1)};
    }

    /**
     * Check if the given Vorbis comment entry's field name matches the given
     *  field name.
     *
     * @param entry An existing Vorbis comment entry.
     * @param fieldName The field name to check.
     * @return {@code true} if the field names match, else {@code false}
     */
    public static boolean entryMatches(String entry, String fieldName) {
        int p = entry.indexOf('=');
        return p >= 0 && p == fieldName.length() && entry.regionMatches(true, 0, fieldName, 0, p);
    }

    /**
     * Find a Vorbis comment with the given field name.
     *
     *  The search begins at entry number {@code offset}; use an offset of 0 to
     *  search from the beginning of the comment array.
     *
     * @param offset The offset into the comment array from where to start
     *                    the search.
     * @param fieldName The field name of the comment to find.
     * @return The offset in the comment array of the first comment whose field
     *    name matches {@code fieldName}, or {@code -1} if no match was found.
     */
    public int findEntryFrom(int offset, String fieldName) {
        for (int i = offset; i < comments.length; i++)
            if (entryMatches(comments[i], fieldName))
                return i;
        return -1;
    }

    /**
     * Remove first Vorbis comment matching the given field name.
     *
     * @param fieldName The field name of comment to delete.
     * @return {@code false} for no matching entries, {@code true} for one matching entry deleted.
     */
    public boolean removeEntryMatching(String fieldName) {
        int i = findEntryFrom(0, fieldName);
        if (i < 0)
            return false;
        deleteComment(i);
        return true;
    }

    /**
     * Remove all Vorbis comments matching the given field name.
     *
     * @param fieldName The field name of comments to delete.
     * @return {@code 0} for no matching entries, else the number of matching entries deleted.
     */
    public int removeEntriesMatching(String fieldName) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(comments));
        if (!list.removeIf(entry -> entryMatches(entry, fieldName)))
            return 0;
        int removed = comments.length - list.size();
        comments = list.toArray(String[]::new);
        return removed;
    }
}
