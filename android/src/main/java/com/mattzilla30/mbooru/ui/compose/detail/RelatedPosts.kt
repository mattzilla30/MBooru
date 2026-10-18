/* Boorusphere PostRelatedButton adaptation (Chaldeaprjkt, BSD-3-Clause).
 * See assets/licenses/boorusphere.txt. MBooru modifications © 2026 mattzilla30. */
package com.mattzilla30.mbooru.ui.compose.detail

import com.mattzilla30.mbooru.data.model.common.TagBase

private const val ARTIST_CATEGORY = 1
private const val CHARACTER_CATEGORY = 4
private const val COPYRIGHT_CATEGORY = 3
private const val GENERAL_CATEGORY = 0
private const val NAMED_CATEGORY_LIMIT = 2
private const val GENERAL_LIMIT = 3

/** Prefer identifying tags over generic attributes; omit metadata and blank tags. */
fun relatedPostQuery(tags: List<TagBase>): String =
    listOf(
            ARTIST_CATEGORY to NAMED_CATEGORY_LIMIT,
            CHARACTER_CATEGORY to NAMED_CATEGORY_LIMIT,
            COPYRIGHT_CATEGORY to NAMED_CATEGORY_LIMIT,
            GENERAL_CATEGORY to GENERAL_LIMIT,
        )
        .flatMap { (category, limit) ->
            tags
                .filter { (if (it.category < 0) GENERAL_CATEGORY else it.category) == category }
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(limit)
        }
        .distinct()
        .joinToString(" ")
