package com.mattzilla30.mbooru.core.model

object ResumePolicy {
    fun validator(etag: String?, lastModified: String?): String? =
        etag?.takeIf { it.startsWith('"') && it.endsWith('"') && it.length > 2 }
            ?: lastModified?.takeIf { it.isNotBlank() }

    fun acceptsPartial(
        offset: Long,
        contentRange: String?,
        expectedValidator: String?,
        actualValidator: String?,
    ): Boolean {
        if (offset <= 0 || expectedValidator == null || actualValidator != expectedValidator)
            return false
        val match =
            Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(contentRange.orEmpty())
                ?: return false
        val start = match.groupValues[1].toLongOrNull() ?: return false
        val end = match.groupValues[2].toLongOrNull() ?: return false
        val total = match.groupValues[3].toLongOrNull()
        return start == offset && end >= start && (total != null && end == total - 1)
    }
}
