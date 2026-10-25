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

import java.util.ArrayList;
import java.util.List;

record ApodizationSpecification(ApodizationFunction type, float stddev, float p, float start, float end, int parts) {

    /* @@@@add to tests */
    static List<ApodizationSpecification> parse(String specification) {
        List<ApodizationSpecification> result = new ArrayList<>();
        for (String name : specification.split(";", -1)) {
            ApodizationFunction function = switch (name) {
                case "bartlett" -> ApodizationFunction.BARTLETT;
                case "bartlett_hann" -> ApodizationFunction.BARTLETT_HANN;
                case "blackman" -> ApodizationFunction.BLACKMAN;
                case "blackman_harris_4term_92db" -> ApodizationFunction.BLACKMAN_HARRIS_4TERM_92DB_SIDELOBE;
                case "connes" -> ApodizationFunction.CONNES;
                case "flattop" -> ApodizationFunction.FLATTOP;
                case "hamming" -> ApodizationFunction.HAMMING;
                case "hann" -> ApodizationFunction.HANN;
                case "kaiser_bessel" -> ApodizationFunction.KAISER_BESSEL;
                case "nuttall" -> ApodizationFunction.NUTTALL;
                case "rectangle" -> ApodizationFunction.RECTANGLE;
                case "triangle" -> ApodizationFunction.TRIANGLE;
                case "welch" -> ApodizationFunction.WELCH;
                default -> null;
            };
            if (function != null)
                result.add(new ApodizationSpecification(function, 0, 0, 0, 1, 1));
            else {
                int open = name.indexOf('(');
                if (open >= 0 && name.endsWith(")")) {
                    String type = name.substring(0, open);
                    String[] arguments = name.substring(open + 1, name.length() - 1).split("/", -1);
                    try {
                        switch (type) {
                            case "gauss" -> {
                                if (arguments.length == 1) {
                                    float stddev = Float.parseFloat(arguments[0]);
                                    if (stddev > 0 && stddev <= 0.5f)
                                        result.add(new ApodizationSpecification(ApodizationFunction.GAUSS, stddev, 0, 0,
                                                1, 1));
                                }
                            }
                            case "tukey" -> {
                                if (arguments.length == 1) {
                                    float p = Float.parseFloat(arguments[0]);
                                    if (p >= 0 && p <= 1)
                                        result.add(
                                                new ApodizationSpecification(ApodizationFunction.TUKEY, 0, p, 0, 1, 1));
                                }
                            }
                            case "partial_tukey" -> {
                                if (arguments.length <= 3) {
                                    int tukeyParts = Integer.parseInt(arguments[0].trim());
                                    float overlap = arguments.length > 1 ? Float.parseFloat(arguments[1]) : 0.1f;
                                    overlap = Math.min(overlap, 0.99f);
                                    float overlapUnits = 1.0f / (1.0f - overlap) - 1.0f;
                                    float tukeyP = arguments.length > 2 ? Float.parseFloat(arguments[2]) : 0.2f;
                                    if (tukeyParts <= 1)
                                        result.add(new ApodizationSpecification(ApodizationFunction.TUKEY, 0, tukeyP, 0,
                                                1, 1));
                                    else if ((long) result.size()
                                            + tukeyParts < StreamEncoder.MAX_APODIZATION_FUNCTIONS)
                                        for (int m = 0; m < tukeyParts; m++)
                                            result.add(new ApodizationSpecification(ApodizationFunction.PARTIAL_TUKEY,
                                                    0, tukeyP, m / (tukeyParts + overlapUnits),
                                                    (m + 1 + overlapUnits) / (tukeyParts + overlapUnits), 1));
                                }
                            }
                            case "punchout_tukey" -> {
                                if (arguments.length <= 3) {
                                    int tukeyParts = Integer.parseInt(arguments[0].trim());
                                    float overlap = arguments.length > 1 ? Float.parseFloat(arguments[1]) : 0.2f;
                                    overlap = Math.min(overlap, 0.99f);
                                    float overlapUnits = 1.0f / (1.0f - overlap) - 1.0f;
                                    float tukeyP = arguments.length > 2 ? Float.parseFloat(arguments[2]) : 0.2f;
                                    if (tukeyParts <= 1)
                                        result.add(new ApodizationSpecification(ApodizationFunction.TUKEY, 0, tukeyP, 0,
                                                1, 1));
                                    else if ((long) result.size()
                                            + tukeyParts < StreamEncoder.MAX_APODIZATION_FUNCTIONS)
                                        for (int m = 0; m < tukeyParts; m++)
                                            result.add(new ApodizationSpecification(ApodizationFunction.PUNCHOUT_TUKEY,
                                                    0, tukeyP, m / (tukeyParts + overlapUnits),
                                                    (m + 1 + overlapUnits) / (tukeyParts + overlapUnits), 1));
                                }
                            }
                            case "subdivide_tukey" -> {
                                if (arguments.length <= 2) {
                                    int parts = Integer.parseInt(arguments[0].trim());
                                    if (parts > 1) {
                                        float p = arguments.length > 1 ? Float.parseFloat(arguments[1]) : 0.5f;
                                        p = Math.max(0, Math.min(1, p));
                                        result.add(new ApodizationSpecification(ApodizationFunction.SUBDIVIDE_TUKEY, 0,
                                                p / parts, 0, 1, parts));
                                    }
                                }
                            }
                            default -> {
                            }
                        }
                    } catch (NumberFormatException ignored) {

                    }
                }
            }
            if (result.size() == StreamEncoder.MAX_APODIZATION_FUNCTIONS)
                break;
        }
        if (result.isEmpty())
            result.add(new ApodizationSpecification(ApodizationFunction.TUKEY, 0, 0.5f, 0, 1, 1));
        return result;
    }

    float[] window(int blocksize) {
        float[] w = new float[blocksize];
        switch (type) {
            case BARTLETT -> Window.bartlett(w, blocksize);
            case BARTLETT_HANN -> Window.bartlettHann(w, blocksize);
            case BLACKMAN -> Window.blackman(w, blocksize);
            case BLACKMAN_HARRIS_4TERM_92DB_SIDELOBE -> Window.blackmanHarris4Term92DbSidelobe(w, blocksize);
            case CONNES -> Window.connes(w, blocksize);
            case FLATTOP -> Window.flattop(w, blocksize);
            case GAUSS -> Window.gauss(w, blocksize, stddev);
            case HAMMING -> Window.hamming(w, blocksize);
            case HANN -> Window.hann(w, blocksize);
            case KAISER_BESSEL -> Window.kaiserBessel(w, blocksize);
            case NUTTALL -> Window.nuttall(w, blocksize);
            case RECTANGLE -> Window.rectangle(w, blocksize);
            case TRIANGLE -> Window.triangle(w, blocksize);
            case PARTIAL_TUKEY -> Window.partialTukey(w, blocksize, p, start, end);
            case PUNCHOUT_TUKEY -> Window.punchoutTukey(w, blocksize, p, start, end);
            case WELCH -> Window.welch(w, blocksize);
            case TUKEY, SUBDIVIDE_TUKEY -> Window.tukey(w, blocksize, p);
        }
        return w;
    }
}
