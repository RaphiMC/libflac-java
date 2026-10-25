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
 * FLAC SEEKTABLE structure.  (c.f. <A HREF="https://xiph.org/flac/format.html#metadata_block_seektable">format
 * specification</A>)
 *
 * <p>From the format specification:
 * - The seek points must be sorted by ascending sample number.
 * - Each seek point's sample number must be the first sample of the
 *   target frame.
 * - Each seek point's sample number must be unique within the table.
 * - Existence of a SEEKTABLE block implies a correct setting of
 *   total_samples in the stream_info block.
 * - Behavior is undefined when more than one SEEKTABLE block is
 *   present in a stream.
 */
public final class SeekTable extends StreamMetadata {
    public SeekTable() {
        super(MetadataType.SEEKTABLE.ordinal());
    }

    SeekPoint[] points = new SeekPoint[0];

    public SeekPoint[] getPoints() {
        return points;
    }

    public void setPoints(SeekPoint[] value) {
        points = value;
    }

    public int getNumPoints() {
        return points.length;
    }

    /**
     * Resize the seekpoint array.
     *
     *  If the size shrinks, elements will truncated; if it grows, new placeholder
     *  points will be added to the end.
     *
     * @param newNumPoints The desired length of the array; may be {@code 0}.
     */
    public void resizePoints(int newNumPoints) {
        if (Integer.toUnsignedLong(newNumPoints)
                * Format.STREAM_METADATA_SEEKPOINT_LENGTH >= (1 << Format.STREAM_METADATA_LENGTH_LEN))
            throw new IllegalArgumentException("seek table exceeds the maximum metadata block size");
        int old = points.length;
        points = Arrays.copyOf(points, newNumPoints);
        /* if growing, set new elements to placeholders */
        for (int i = old; i < newNumPoints; i++)
            points[i] = new SeekPoint();
    }

    /**
     * Set a seekpoint in a seektable.
     *
     * @param pointNum Index into seekpoint array to set.
     * @param point The point to set.
     */
    public void setPoint(int pointNum, SeekPoint point) {
        points[pointNum] = point.copy();
    }

    /**
     * Insert a seekpoint into a seektable.
     *
     * @param pointNum Index into seekpoint array to set.
     * @param point The point to set.
     */
    public void insertPoint(int pointNum, SeekPoint point) {
        Objects.checkIndex(pointNum, points.length + 1);
        resizePoints(points.length + 1);
        /* move all points >= pointNum forward one space */
        System.arraycopy(points, pointNum, points, pointNum + 1, points.length - 1 - pointNum);
        setPoint(pointNum, point);
    }

    /**
     * Delete a seekpoint from a seektable.
     *
     * @param pointNum Index into seekpoint array to set.
     */
    public void deletePoint(int pointNum) {
        Objects.checkIndex(pointNum, points.length);
        /* move all points > pointNum backward one space */
        System.arraycopy(points, pointNum + 1, points, pointNum, points.length - 1 - pointNum);
        resizePoints(points.length - 1);
    }

    /**
     * Check a seektable to see if it conforms to the FLAC specification.
     *  See the format specification for limits on the contents of the
     *  seektable.
     *
     * @return {@code false} if seek table is illegal, else {@code true}.
     */
    public boolean isLegal() {
        if ((long) points.length * Format.STREAM_METADATA_SEEKPOINT_LENGTH >= 1 << Format.STREAM_METADATA_LENGTH_LEN)
            return false;
        for (int i = 1; i < points.length; i++)
            if (points[i].sampleNumber != Format.STREAM_METADATA_SEEKPOINT_PLACEHOLDER
                    && Long.compareUnsigned(points[i].sampleNumber, points[i - 1].sampleNumber) <= 0)
                return false;
        return true;
    }

    /**
     * Append a number of placeholder points to the end of a seek table.
     *
     * <p>As with the other ..._seektable_template_... functions, you should
     * call SeekTable.templateSort() when finished
     * to make the seek table legal.
     *
     * @param num The number of placeholder points to append.
     */
    public void templateAppendPlaceholders(int num) {
        /* WATCHOUT: we rely on the fact that growing the array adds PLACEHOLDERS at the end */
        resizePoints(Math.addExact(points.length, num));
    }

    /**
     * Append a specific seek point template to the end of a seek table.
     *
     * <p>As with the other ..._seektable_template_... functions, you should
     * call SeekTable.templateSort() when finished
     * to make the seek table legal.
     *
     * @param sampleNumber The sample number of the seek point template.
     */
    public void templateAppendPoint(long sampleNumber) {
        int old = points.length;
        resizePoints(Math.addExact(old, 1));
        points[old].sampleNumber = sampleNumber;
    }

    /**
     * Append specific seek point templates to the end of a seek table.
     *
     * <p>As with the other ..._seektable_template_... functions, you should
     * call SeekTable.templateSort() when finished
     * to make the seek table legal.
     *
     * @param sampleNumbers An array of sample numbers for the seek points.
     */
    public void templateAppendPoints(long[] sampleNumbers) {
        if (sampleNumbers.length == 0)
            return;
        int old = points.length;
        resizePoints(Math.addExact(old, sampleNumbers.length));
        for (int i = 0; i < sampleNumbers.length; i++)
            points[old + i].sampleNumber = sampleNumbers[i];
    }

    /**
     * Append a set of evenly-spaced seek point templates to the end of a
     *  seek table.
     *
     * <p>As with the other ..._seektable_template_... functions, you should
     * call SeekTable.templateSort() when finished
     * to make the seek table legal.
     *
     * @param num The number of placeholder points to append.
     * @param totalSamples The total number of samples to be encoded;
     *                       the seekpoints will be spaced approximately
     *                       {@code totalSamples} / {@code num} samples apart.
     */
    public void templateAppendSpacedPoints(int num, long totalSamples) {
        if (num < 0)
            throw new IllegalArgumentException("point count");
        if (num > 0 && totalSamples != 0) {
            int old = points.length;
            resizePoints(Math.addExact(old, num));
            for (int i = 0; i < num; i++)
                points[old + i].sampleNumber = Long.divideUnsigned(totalSamples * i, num);
        }
    }

    /**
     * Append a set of evenly-spaced seek point templates to the end of a
     *  seek table.
     *
     * <p>As with the other ..._seektable_template_... functions, you should
     * call SeekTable.templateSort() when finished
     * to make the seek table legal.
     *
     * @param samples The number of samples apart to space the placeholder
     *                points.  The first point will be at sample {@code 0}, the
     *                second at sample {@code samples}, then 2*{@code samples}, and
     *                so on.  As long as {@code samples} and {@code totalSamples}
     *                are greater than {@code 0}, there will always be at least
     *                one seekpoint at sample {@code 0}.
     * @param totalSamples The total number of samples to be encoded;
     *                       the seekpoints will be spaced
     *                       {@code samples} samples apart.
     */
    public void templateAppendSpacedPointsBySamples(int samples, long totalSamples) {
        long spacing = Integer.toUnsignedLong(samples);
        if (spacing == 0 || totalSamples == 0)
            return;
        /* 1+ for the first sample at 0 */
        long num = 1 + Long.divideUnsigned(totalSamples, spacing);
        /* now account for the fact that we don't place a seekpoint at "totalSamples" since samples are number from 0: */
        if (Long.remainderUnsigned(totalSamples, spacing) == 0)
            num--;
        /* Put a strict upper bound on the number of allowed seek points. */
        if (Long.compareUnsigned(num, 32768) > 0) {
            /* Set the bound and recalculate samples accordingly. */
            num = 32768;
            spacing = Integer.toUnsignedLong((int) Long.divideUnsigned(totalSamples, num));
        }
        int old = points.length;
        resizePoints(Math.addExact(old, (int) num));
        long sample = 0;
        for (int i = 0; i < num; i++, sample += spacing)
            points[old + i].sampleNumber = sample;
    }

    /**
     * Sort a seek table's seek points according to the format specification.
     *  This includes a "unique-ification" step to remove duplicates, i.e.
     *  seek points with identical {@code sampleNumber} values.  Duplicate seek
     *  points are converted into placeholder points and sorted to the end of
     *  the table.
     *
     * @return The number of duplicate seek points converted into placeholders.
     */

    /* @@@@ add to unit tests; it is already indirectly tested by the metadata_object tests */
    public int sort() {
        /* sort the seekpoints */
        Arrays.sort(points, (a, b) -> Long.compareUnsigned(a.sampleNumber, b.sampleNumber));
        /* uniquify the seekpoints */
        int j = 0;
        for (SeekPoint p : points)
            if (p.sampleNumber == Format.STREAM_METADATA_SEEKPOINT_PLACEHOLDER || j == 0
                    || p.sampleNumber != points[j - 1].sampleNumber)
                points[j++] = p;
        int result = j;
        while (j < points.length)
            points[j++] = new SeekPoint();
        return result;
    }

    /**
     * Sort a seek table's seek points according to the format specification,
     *  removing duplicates.
     *
     * @param compact If {@code false}, behaves like sort().
     *                 If {@code true}, duplicates are deleted and the seek table is
     *                 shrunk appropriately; the number of placeholder points
     *                 present in the seek table will be the same after the call
     *                 as before.
     */
    public void templateSort(boolean compact) {
        int n = sort();
        if (compact)
            resizePoints(n);
    }
}
