/* replaygain_synthesis - Routines for applying ReplayGain to a signal
 * Copyright (C) 2002-2009  Josh Coalson
 * Copyright (C) 2011-2023  Xiph.Org Foundation
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
/*
 * This is an aggregation of pieces of code from John Edwards' WaveGain
 * program.  Mostly cosmetic changes were made; otherwise, the dithering
 * code is almost untouched and the gain processing was converted from
 * processing a whole file to processing chunks of samples.
 *
 * The original copyright notices for WaveGain's dither.c and wavegain.c
 * appear below:
 */
/*
 * (c) 2002 John Edwards
 * mostly lifted from work by Frank Klemm
 * random functions for dithering.
 */
/*
 * Copyright (C) 2002 John Edwards
 * Additional code by Magnus Holmgren and Gian-Carlo Pascutto
 */

package io.github.raphimc.libflac.replaygain;

public enum NoiseShaping {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}
