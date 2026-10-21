// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.core.model

object DownloadLimits {
    const val MIB = 1024L * 1024L
    const val DEFAULT_BYTES = 4096L * MIB
    const val MIN_BYTES = 256L * MIB
    const val MAX_BYTES = 8192L * MIB
    private const val SMALL_MIB = 256L
    private const val MEDIUM_MIB = 1024L
    private const val LARGE_MIB = 4096L
    private const val MAXIMUM_MIB = 8192L
    val choicesMiB = listOf(SMALL_MIB, MEDIUM_MIB, LARGE_MIB, MAXIMUM_MIB)
}
