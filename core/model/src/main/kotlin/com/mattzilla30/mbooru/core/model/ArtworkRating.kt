// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.core.model

import java.util.Locale

enum class ArtworkRating {
    SAFE,
    QUESTIONABLE,
    EXPLICIT,
    UNKNOWN,
}

fun normalizedRating(raw: String): ArtworkRating =
    when (raw.trim().lowercase(Locale.ROOT)) {
        "s",
        "safe",
        "g",
        "general" -> ArtworkRating.SAFE
        "q",
        "questionable",
        "sensitive" -> ArtworkRating.QUESTIONABLE
        "e",
        "explicit" -> ArtworkRating.EXPLICIT
        else -> ArtworkRating.UNKNOWN
    }
