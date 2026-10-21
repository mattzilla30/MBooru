// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import com.mattzilla30.mbooru.core.model.ArtworkRating
import com.mattzilla30.mbooru.core.model.normalizedRating
import java.util.Locale

/** No credentials or Android objects: suitable for saved site-specific query state. */
data class ArtworkQuery(
    val site: Long,
    val tags: String = "",
    val safeOnly: Boolean = true,
    val sort: DiscoverySort = DiscoverySort.LATEST,
) {
    val normalizedTags
        get() = normalize(tags)

    fun serverTags(): String {
        val terms = normalizedTags.split(' ').filter { it.isNotBlank() }.toMutableList()
        if (safeOnly) {
            terms.removeAll { it.removePrefix("-").startsWith("rating:") }
            terms += listOf("-rating:questionable", "-rating:explicit")
        }
        when (sort) {
            DiscoverySort.RANDOM -> terms += "sort:random"
            DiscoverySort.SCORE -> terms += "sort:score:desc"
            DiscoverySort.LATEST -> Unit
        }
        return terms.joinToString(" ")
    }

    companion object {
        fun normalize(query: String) =
            query
                .replace(',', ' ')
                .trim()
                .lowercase(Locale.ROOT)
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" ")
    }
}

enum class DiscoverySort {
    LATEST,
    RANDOM,
    SCORE,
}

/**
 * Verify only plain AND/exclusion/wildcard searches; complex service operators stay server-owned.
 */
fun matchesRule34Tags(tags: Set<String>, query: String): Boolean {
    val normalized = ArtworkQuery.normalize(query)
    if (normalized.any { it in "{}()~|" }) return true
    return normalized.split(' ').all { token ->
        val term = token.removePrefix("-")
        if (term.isBlank() || ':' in term) true
        else {
            val pattern =
                Regex(
                    "^" + term.split('*').joinToString(".*") { Regex.escape(it) } + "$",
                    RegexOption.IGNORE_CASE,
                )
            val present = tags.any { pattern.matches(it) }
            if (token.startsWith('-')) !present else present
        }
    }
}

/** Known rating clauses can be checked even when normal tag operators stay server-owned. */
fun matchesRule34Rating(raw: String, query: String): Boolean =
    ArtworkQuery.normalize(query).split(' ').all { token ->
        val term = token.removePrefix("-")
        if (!term.startsWith("rating:")) true
        else {
            val requested = normalizedRating(term.substringAfter(':'))
            if (requested == ArtworkRating.UNKNOWN) true
            else (normalizedRating(raw) == requested) != token.startsWith('-')
        }
    }
