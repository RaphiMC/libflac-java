/*
 *  ReplayGainAnalysis - analyzes input samples and give the recommended dB change
 *  Copyright (C) 2001 David Robinson and Glen Sawyer
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  concept and filter values by David Robinson (David@Robinson.org)
 *    -- blame him if you think the idea is flawed
 *  original coding by Glen Sawyer (glensawyer@hotmail.com)
 *    -- blame him if you think this runs too slowly, or the coding is otherwise flawed
 *
 *  lots of code improvements by Frank Klemm ( http://www.uni-jena.de/~pfk/mpp/ )
 *    -- credit him for all the _good_ programming ;)
 *
 *  minor cosmetic tweaks to integrate with FLAC by Josh Coalson
 *
 *
 *  For an explanation of the concepts and the basic algorithms involved, go to:
 *    http://www.replaygain.org/
 */

package io.github.raphimc.libflac.replaygain;

import java.util.Arrays;

/**
 * Here's the deal. Call
 *
 *    initGainAnalysis ( long sampleFrequency );
 *
 *  to initialize everything. Call
 *
 *    analyzeSamples ( float[]  leftSamples,
 *                     float[]  rightSamples,
 *                     int          numSamples,
 *                     int             numChannels );
 *
 *  as many times as you want, with as many or as few samples as you want.
 *  If mono, pass the sample buffer in through leftSamples, leave
 *  rightSamples null, and make sure numChannels = 1.
 *
 *    getTitleGain()
 *
 *  will return the recommended dB level change for all samples analyzed
 *  SINCE THE LAST TIME you called getTitleGain() OR initGainAnalysis().
 *
 *    getAlbumGain()
 *
 *  will return the recommended dB level change for all samples analyzed
 *  since initGainAnalysis() was called and finalized with getTitleGain().
 *
 * So here's the main source of potential code confusion:
 *
 *  The filters applied to the incoming samples are IIR filters,
 *  meaning they rely on up to &lt;filter order&gt; number of previous samples
 *  AND up to &lt;filter order&gt; number of previous filtered samples.
 *
 *  I set up the analyzeSamples routine to minimize memory usage and interface
 *  complexity. The speed isn't compromised too much (I don't think), but the
 *  internal complexity is higher than it should be for such a relatively
 *  simple routine.
 *
 *  Optimization/clarity suggestions are welcome.
 */
public final class ReplayGainAnalysis {
    public static final int GAIN_NOT_ENOUGH_SAMPLES = -24601;
    /** in dB SPL */
    private float referenceLoudness = 89.0f;

    private static final int YULE_ORDER = 10;
    private static final int BUTTER_ORDER = 2;
    private static final double RMS_PERCENTILE = 0.95;
    private static final int RMS_WINDOW_TIME = 50;
    private static final double STEPS_per_dB = 100.0;
    private static final double MAX_dB = 120.0;
    private static final int MAX_ORDER = BUTTER_ORDER > YULE_ORDER ? BUTTER_ORDER : YULE_ORDER;
    /** calibration value */
    private static final double PINK_REF = 64.82;

    /** left input samples, with pre-buffer */
    private final float[] linprebuf = new float[MAX_ORDER * 2];
    /**
     * lstepbuf: left "first step" (i.e. post first filter) samples
     *
     * loutbuf: left "out" (i.e. post second filter) samples
     */
    private float[] lstepbuf, loutbuf;
    /** right input samples ... */
    private final float[] rinprebuf = new float[MAX_ORDER * 2];
    private float[] rstepbuf, routbuf;
    /** sampleWindow: number of samples required to reach number of milliseconds required for RMS window */
    private int sampleWindow, totsamp;
    private double lsum, rsum;
    private final int[] A = new int[(int) (STEPS_per_dB * MAX_dB)], B = new int[(int) (STEPS_per_dB * MAX_dB)];

    private record ReplayGainFilter(int rate, int downsample, float[] bYule, float[] aYule, float[] bButter,
            float[] aButter) {
    }

    private ReplayGainFilter replayGainFilter;

    private static final ReplayGainFilter[] ReplayGainFilters = {
            /* ORIGINAL */
            new ReplayGainFilter(48000, 0,
                    new float[] {(float) 0.03857599435200, (float) -0.02160367184185, (float) -0.00123395316851,
                            (float) -0.00009291677959, (float) -0.01655260341619, (float) 0.02161526843274,
                            (float) -0.02074045215285, (float) 0.00594298065125, (float) 0.00306428023191,
                            (float) 0.00012025322027, (float) 0.00288463683916},
                    new float[] {(float) 1.00000000000000, (float) -3.84664617118067, (float) 7.81501653005538,
                            (float) -11.34170355132042, (float) 13.05504219327545, (float) -12.28759895145294,
                            (float) 9.48293806319790, (float) -5.87257861775999, (float) 2.75465861874613,
                            (float) -0.86984376593551, (float) 0.13919314567432},
                    new float[] {(float) 0.98621192462708, (float) -1.97242384925416, (float) 0.98621192462708},
                    new float[] {(float) 1.00000000000000, (float) -1.97223372919527, (float) 0.97261396931306}),
            /* ORIGINAL */
            new ReplayGainFilter(44100, 0,
                    new float[] {(float) 0.05418656406430, (float) -0.02911007808948, (float) -0.00848709379851,
                            (float) -0.00851165645469, (float) -0.00834990904936, (float) 0.02245293253339,
                            (float) -0.02596338512915, (float) 0.01624864962975, (float) -0.00240879051584,
                            (float) 0.00674613682247, (float) -0.00187763777362},
                    new float[] {(float) 1.00000000000000, (float) -3.47845948550071, (float) 6.36317777566148,
                            (float) -8.54751527471874, (float) 9.47693607801280, (float) -8.81498681370155,
                            (float) 6.85401540936998, (float) -4.39470996079559, (float) 2.19611684890774,
                            (float) -0.75104302451432, (float) 0.13149317958808},
                    new float[] {(float) 0.98500175787242, (float) -1.97000351574484, (float) 0.98500175787242},
                    new float[] {(float) 1.00000000000000, (float) -1.96977855582618, (float) 0.97022847566350}),
            new ReplayGainFilter(37800, 0,
                    new float[] {(float) 0.10296717174470, (float) -0.04877975583256, (float) -0.02878009075237,
                            (float) -0.03519509188311, (float) 0.02888717172493, (float) -0.00609872684844,
                            (float) 0.00209851217112, (float) 0.00911704668543, (float) 0.01154404718589,
                            (float) -0.00630293688700, (float) 0.00107527155228},
                    new float[] {(float) 1.00000000000000, (float) -2.64848054923531, (float) 3.58406058405771,
                            (float) -3.83794914179161, (float) 3.90142345804575, (float) -3.50179818637243,
                            (float) 2.67085284083076, (float) -1.82581142372418, (float) 1.09530368139801,
                            (float) -0.47689017820395, (float) 0.11171431535905},
                    new float[] {(float) 0.98252400815195, (float) -1.96504801630391, (float) 0.98252400815195},
                    new float[] {(float) 1.00000000000000, (float) -1.96474258269041, (float) 0.96535344991740}),
            new ReplayGainFilter(36000, 0,
                    new float[] {(float) 0.11572297028613, (float) -0.04120916051252, (float) -0.04977731768022,
                            (float) -0.01047308680426, (float) 0.00750863219157, (float) 0.00055507694408,
                            (float) 0.00140344192886, (float) 0.01286095246036, (float) 0.00998223033885,
                            (float) -0.00725013810661, (float) 0.00326503346879},
                    new float[] {(float) 1.00000000000000, (float) -2.43606802820871, (float) 3.01907406973844,
                            (float) -2.90372016038192, (float) 2.67947188094303, (float) -2.17606479220391,
                            (float) 1.44912956803015, (float) -0.87785765549050, (float) 0.53592202672557,
                            (float) -0.26469344817509, (float) 0.07495878059717},
                    new float[] {(float) 0.98165826840326, (float) -1.96331653680652, (float) 0.98165826840326},
                    new float[] {(float) 1.00000000000000, (float) -1.96298008938934, (float) 0.96365298422371}),
            /* ORIGINAL */
            new ReplayGainFilter(32000, 0,
                    new float[] {(float) 0.15457299681924, (float) -0.09331049056315, (float) -0.06247880153653,
                            (float) 0.02163541888798, (float) -0.05588393329856, (float) 0.04781476674921,
                            (float) 0.00222312597743, (float) 0.03174092540049, (float) -0.01390589421898,
                            (float) 0.00651420667831, (float) -0.00881362733839},
                    new float[] {(float) 1.00000000000000, (float) -2.37898834973084, (float) 2.84868151156327,
                            (float) -2.64577170229825, (float) 2.23697657451713, (float) -1.67148153367602,
                            (float) 1.00595954808547, (float) -0.45953458054983, (float) 0.16378164858596,
                            (float) -0.05032077717131, (float) 0.02347897407020},
                    new float[] {(float) 0.97938932735214, (float) -1.95877865470428, (float) 0.97938932735214},
                    new float[] {(float) 1.00000000000000, (float) -1.95835380975398, (float) 0.95920349965459}),
            new ReplayGainFilter(28000, 0,
                    new float[] {(float) 0.23882392323383, (float) -0.22007791534089, (float) -0.06014581950332,
                            (float) 0.05004458058021, (float) -0.03293111254977, (float) 0.02348678189717,
                            (float) 0.04290549799671, (float) -0.00938141862174, (float) 0.00015095146303,
                            (float) -0.00712601540885, (float) -0.00626520210162},
                    new float[] {(float) 1.00000000000000, (float) -2.06894080899139, (float) 1.76944699577212,
                            (float) -0.81404732584187, (float) 0.25418286850232, (float) -0.30340791669762,
                            (float) 0.35616884070937, (float) -0.14967310591258, (float) -0.07024154183279,
                            (float) 0.11078404345174, (float) -0.03551838002425},
                    new float[] {(float) 0.97647981663949, (float) -1.95295963327897, (float) 0.97647981663949},
                    new float[] {(float) 1.00000000000000, (float) -1.95240635772520, (float) 0.95351290883275}),
            /* ORIGINAL */
            new ReplayGainFilter(24000, 0,
                    new float[] {(float) 0.30296907319327, (float) -0.22613988682123, (float) -0.08587323730772,
                            (float) 0.03282930172664, (float) -0.00915702933434, (float) -0.02364141202522,
                            (float) -0.00584456039913, (float) 0.06276101321749, (float) -0.00000828086748,
                            (float) 0.00205861885564, (float) -0.02950134983287},
                    new float[] {(float) 1.00000000000000, (float) -1.61273165137247, (float) 1.07977492259970,
                            (float) -0.25656257754070, (float) -0.16276719120440, (float) -0.22638893773906,
                            (float) 0.39120800788284, (float) -0.22138138954925, (float) 0.04500235387352,
                            (float) 0.02005851806501, (float) 0.00302439095741},
                    new float[] {(float) 0.97531843204928, (float) -1.95063686409857, (float) 0.97531843204928},
                    new float[] {(float) 1.00000000000000, (float) -1.95002759149878, (float) 0.95124613669835}),
            /* ORIGINAL */
            new ReplayGainFilter(22050, 0,
                    new float[] {(float) 0.33642304856132, (float) -0.25572241425570, (float) -0.11828570177555,
                            (float) 0.11921148675203, (float) -0.07834489609479, (float) -0.00469977914380,
                            (float) -0.00589500224440, (float) 0.05724228140351, (float) 0.00832043980773,
                            (float) -0.01635381384540, (float) -0.01760176568150},
                    new float[] {(float) 1.00000000000000, (float) -1.49858979367799, (float) 0.87350271418188,
                            (float) 0.12205022308084, (float) -0.80774944671438, (float) 0.47854794562326,
                            (float) -0.12453458140019, (float) -0.04067510197014, (float) 0.08333755284107,
                            (float) -0.04237348025746, (float) 0.02977207319925},
                    new float[] {(float) 0.97316523498161, (float) -1.94633046996323, (float) 0.97316523498161},
                    new float[] {(float) 1.00000000000000, (float) -1.94561023566527, (float) 0.94705070426118}),
            new ReplayGainFilter(18900, 0,
                    new float[] {(float) 0.38412657295385, (float) -0.44533729608120, (float) 0.20426638066221,
                            (float) -0.28031676047946, (float) 0.31484202614802, (float) -0.26078311203207,
                            (float) 0.12925201224848, (float) -0.01141164696062, (float) 0.03036522115769,
                            (float) -0.03776339305406, (float) 0.00692036603586},
                    new float[] {(float) 1.00000000000000, (float) -1.74403915585708, (float) 1.96686095832499,
                            (float) -2.10081452941881, (float) 1.90753918182846, (float) -1.83814263754422,
                            (float) 1.36971352214969, (float) -0.77883609116398, (float) 0.39266422457649,
                            (float) -0.12529383592986, (float) 0.05424760697665},
                    new float[] {(float) 0.96535326815829, (float) -1.93070653631658, (float) 0.96535326815829},
                    new float[] {(float) 1.00000000000000, (float) -1.92950577983524, (float) 0.93190729279793}),
            /* ORIGINAL */
            new ReplayGainFilter(16000, 0,
                    new float[] {(float) 0.44915256608450, (float) -0.14351757464547, (float) -0.22784394429749,
                            (float) -0.01419140100551, (float) 0.04078262797139, (float) -0.12398163381748,
                            (float) 0.04097565135648, (float) 0.10478503600251, (float) -0.01863887810927,
                            (float) -0.03193428438915, (float) 0.00541907748707},
                    new float[] {(float) 1.00000000000000, (float) -0.62820619233671, (float) 0.29661783706366,
                            (float) -0.37256372942400, (float) 0.00213767857124, (float) -0.42029820170918,
                            (float) 0.22199650564824, (float) 0.00613424350682, (float) 0.06747620744683,
                            (float) 0.05784820375801, (float) 0.03222754072173},
                    new float[] {(float) 0.96454515552826, (float) -1.92909031105652, (float) 0.96454515552826},
                    new float[] {(float) 1.00000000000000, (float) -1.92783286977036, (float) 0.93034775234268}),
            /* ORIGINAL */
            new ReplayGainFilter(12000, 0,
                    new float[] {(float) 0.56619470757641, (float) -0.75464456939302, (float) 0.16242137742230,
                            (float) 0.16744243493672, (float) -0.18901604199609, (float) 0.30931782841830,
                            (float) -0.27562961986224, (float) 0.00647310677246, (float) 0.08647503780351,
                            (float) -0.03788984554840, (float) -0.00588215443421},
                    new float[] {(float) 1.00000000000000, (float) -1.04800335126349, (float) 0.29156311971249,
                            (float) -0.26806001042947, (float) 0.00819999645858, (float) 0.45054734505008,
                            (float) -0.33032403314006, (float) 0.06739368333110, (float) -0.04784254229033,
                            (float) 0.01639907836189, (float) 0.01807364323573},
                    new float[] {(float) 0.96009142950541, (float) -1.92018285901082, (float) 0.96009142950541},
                    new float[] {(float) 1.00000000000000, (float) -1.91858953033784, (float) 0.92177618768381}),
            /* ORIGINAL */
            new ReplayGainFilter(11025, 0,
                    new float[] {(float) 0.58100494960553, (float) -0.53174909058578, (float) -0.14289799034253,
                            (float) 0.17520704835522, (float) 0.02377945217615, (float) 0.15558449135573,
                            (float) -0.25344790059353, (float) 0.01628462406333, (float) 0.06920467763959,
                            (float) -0.03721611395801, (float) -0.00749618797172},
                    new float[] {(float) 1.00000000000000, (float) -0.51035327095184, (float) -0.31863563325245,
                            (float) -0.20256413484477, (float) 0.14728154134330, (float) 0.38952639978999,
                            (float) -0.23313271880868, (float) -0.05246019024463, (float) -0.02505961724053,
                            (float) 0.02442357316099, (float) 0.01818801111503},
                    new float[] {(float) 0.95856916599601, (float) -1.91713833199203, (float) 0.95856916599601},
                    new float[] {(float) 1.00000000000000, (float) -1.91542108074780, (float) 0.91885558323625}),
            /* ORIGINAL */
            new ReplayGainFilter(8000, 0,
                    new float[] {(float) 0.53648789255105, (float) -0.42163034350696, (float) -0.00275953611929,
                            (float) 0.04267842219415, (float) -0.10214864179676, (float) 0.14590772289388,
                            (float) -0.02459864859345, (float) -0.11202315195388, (float) -0.04060034127000,
                            (float) 0.04788665548180, (float) -0.02217936801134},
                    new float[] {(float) 1.00000000000000, (float) -0.25049871956020, (float) -0.43193942311114,
                            (float) -0.03424681017675, (float) -0.04678328784242, (float) 0.26408300200955,
                            (float) 0.15113130533216, (float) -0.17556493366449, (float) -0.18823009262115,
                            (float) 0.05477720428674, (float) 0.04704409688120},
                    new float[] {(float) 0.94597685600279, (float) -1.89195371200558, (float) 0.94597685600279},
                    new float[] {(float) 1.00000000000000, (float) -1.88903307939452, (float) 0.89487434461664}),};

    public float getReplayGainReferenceLoudness() {
        return referenceLoudness;
    }

    public void setReplayGainReferenceLoudness(float value) {
        referenceLoudness = value;
    }

    /* When calling this procedure, make sure that input[inputOffset-order] and output[outputOffset-order] contain real data! */
    private static void filter(float[] input, int inputOffset, float[] output, int outputOffset, int nSamples,
            float[] a, float[] b, int order, int downsample) {
        for (int i = 0; i < nSamples; i++, inputOffset += downsample) {
            double y = input[inputOffset] * b[0];
            for (int k = 1; k <= order; k++)
                y += input[inputOffset - k * downsample] * b[k] - output[outputOffset + i - k] * a[k];
            output[outputOffset + i] = (float) y;
        }
    }

    private static ReplayGainFilter createGainFilter(long sampleFrequency) {
        int downsample = 1;
        int maxRate = 0;
        while (true) {
            for (ReplayGainFilter gainFilter : ReplayGainFilters) {
                maxRate = Math.max(maxRate, gainFilter.rate);
                if (gainFilter.rate == sampleFrequency)
                    return new ReplayGainFilter(gainFilter.rate, downsample, gainFilter.bYule, gainFilter.aYule,
                            gainFilter.bButter, gainFilter.aButter);
            }
            if (sampleFrequency < maxRate)
                return null;
            while (sampleFrequency > maxRate) {
                downsample *= 2;
                sampleFrequency /= 2;
            }
        }
    }

    private boolean resetSampleFrequency(long sampleFrequency) {
        replayGainFilter = createGainFilter(sampleFrequency);
        if (replayGainFilter == null)
            return false;
        sampleWindow = (replayGainFilter.rate * RMS_WINDOW_TIME + 1000 - 1) / 1000;
        /* zero out initial values */
        lstepbuf = new float[sampleWindow + MAX_ORDER];
        rstepbuf = new float[sampleWindow + MAX_ORDER];
        loutbuf = new float[sampleWindow + MAX_ORDER];
        routbuf = new float[sampleWindow + MAX_ORDER];
        Arrays.fill(linprebuf, 0, MAX_ORDER, 0);
        Arrays.fill(rinprebuf, 0, MAX_ORDER, 0);
        lsum = rsum = 0;
        totsamp = 0;
        Arrays.fill(A, 0);
        return true;
    }

    public static boolean validGainFrequency(long sampleFrequency) {
        return createGainFilter(sampleFrequency) != null;
    }

    public boolean initGainAnalysis(long sampleFrequency) {
        if (!resetSampleFrequency(sampleFrequency))
            return false;
        Arrays.fill(B, 0);
        return true;
    }

    /* returns true if successful, false if not */
    public boolean analyzeSamples(float[] leftSamples, float[] rightSamples, int numSamples, int numChannels) {
        if (replayGainFilter == null)
            throw new IllegalStateException("gain analysis uninitialized");
        int downsample = replayGainFilter.downsample;
        numSamples /= downsample;
        if (numSamples == 0)
            return true;
        if (numChannels == 1)
            rightSamples = leftSamples;
        else if (numChannels != 2)
            return false;
        int prebufSamples = Math.min(MAX_ORDER, numSamples);
        for (int i = 0; i < prebufSamples; i++) {
            linprebuf[i + MAX_ORDER] = leftSamples[i * downsample];
            rinprebuf[i + MAX_ORDER] = rightSamples[i * downsample];
        }
        int curSamplePos = 0, batchSamples = numSamples;
        while (batchSamples > 0) {
            int curSamples = Math.min(batchSamples, sampleWindow - totsamp);
            float[] curLeft, curRight;
            int inputOffset;
            if (curSamplePos < MAX_ORDER) {
                downsample = 1;
                curLeft = linprebuf;
                curRight = rinprebuf;
                inputOffset = MAX_ORDER + curSamplePos;
                curSamples = Math.min(curSamples, MAX_ORDER - curSamplePos);
            } else {
                downsample = replayGainFilter.downsample;
                curLeft = leftSamples;
                curRight = rightSamples;
                inputOffset = curSamplePos * downsample;
            }
            filter(curLeft, inputOffset, lstepbuf, MAX_ORDER + totsamp, curSamples, replayGainFilter.aYule,
                    replayGainFilter.bYule, YULE_ORDER, downsample);
            filter(curRight, inputOffset, rstepbuf, MAX_ORDER + totsamp, curSamples, replayGainFilter.aYule,
                    replayGainFilter.bYule, YULE_ORDER, downsample);
            filter(lstepbuf, MAX_ORDER + totsamp, loutbuf, MAX_ORDER + totsamp, curSamples, replayGainFilter.aButter,
                    replayGainFilter.bButter, BUTTER_ORDER, 1);
            filter(rstepbuf, MAX_ORDER + totsamp, routbuf, MAX_ORDER + totsamp, curSamples, replayGainFilter.aButter,
                    replayGainFilter.bButter, BUTTER_ORDER, 1);
            /* Get the squared values */
            for (int i = 0; i < curSamples; i++) {
                lsum += loutbuf[MAX_ORDER + totsamp + i] * loutbuf[MAX_ORDER + totsamp + i];
                rsum += routbuf[MAX_ORDER + totsamp + i] * routbuf[MAX_ORDER + totsamp + i];
            }
            batchSamples -= curSamples;
            curSamplePos += curSamples;
            totsamp += curSamples;
            /* Get the Root Mean Square (RMS) for this set of samples */
            if (totsamp == sampleWindow) {
                double val = STEPS_per_dB * 10 * Math.log10((lsum + rsum) / totsamp * 0.5 + 1e-37);
                int ival = Math.max(0, Math.min(A.length - 1, (int) val));
                A[ival]++;
                lsum = rsum = 0;
                System.arraycopy(loutbuf, totsamp, loutbuf, 0, MAX_ORDER);
                System.arraycopy(routbuf, totsamp, routbuf, 0, MAX_ORDER);
                System.arraycopy(lstepbuf, totsamp, lstepbuf, 0, MAX_ORDER);
                System.arraycopy(rstepbuf, totsamp, rstepbuf, 0, MAX_ORDER);
                totsamp = 0;
            }
            /* somehow I really screwed up: Error in programming! Contact author about totsamp > sampleWindow */
            if (totsamp > sampleWindow)
                return false;
        }
        if (numSamples < MAX_ORDER) {
            System.arraycopy(linprebuf, numSamples, linprebuf, 0, MAX_ORDER - numSamples);
            System.arraycopy(rinprebuf, numSamples, rinprebuf, 0, MAX_ORDER - numSamples);
            System.arraycopy(leftSamples, 0, linprebuf, MAX_ORDER - numSamples, numSamples);
            System.arraycopy(rightSamples, 0, rinprebuf, MAX_ORDER - numSamples, numSamples);
        } else {
            downsample = replayGainFilter.downsample;
            int offset = (numSamples - MAX_ORDER) * downsample;
            for (int i = 0; i < MAX_ORDER; i++) {
                linprebuf[i] = leftSamples[offset + i * downsample];
                rinprebuf[i] = rightSamples[offset + i * downsample];
            }
        }
        return true;
    }

    private static float analyzeResult(int[] array) {
        int elems = 0;
        for (int n : array)
            elems += n;
        if (elems == 0)
            return GAIN_NOT_ENOUGH_SAMPLES;
        int upper = Integer.divideUnsigned(elems, 20) + (Integer.remainderUnsigned(elems, 20) != 0 ? 1 : 0),
                i = array.length;
        while (i-- > 0)
            if ((upper -= array[i]) <= 0)
                break;
        return (float) PINK_REF - (float) i / (float) STEPS_per_dB;
    }

    public float getTitleGain() {
        if (replayGainFilter == null)
            throw new IllegalStateException("gain analysis uninitialized");
        float result = analyzeResult(A);
        for (int i = 0; i < A.length; i++) {
            B[i] += A[i];
            A[i] = 0;
        }
        for (int i = 0; i < MAX_ORDER; i++)
            linprebuf[i] = lstepbuf[i] = loutbuf[i] = rinprebuf[i] = rstepbuf[i] = routbuf[i] = 0;
        totsamp = 0;
        lsum = rsum = 0;
        return result;
    }

    public float getAlbumGain() {
        return analyzeResult(B);
    }
}
