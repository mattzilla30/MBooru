// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.licenses

import android.content.Context
import com.mattzilla30.mbooru.R

/** The OSS plugin records UTF-8 byte ranges, not character offsets. */
data class OpenSourceLicense(val name: String, val text: String)

object LicenseCatalog {
    fun load(context: Context): List<OpenSourceLicense> {
        val metadata =
            context.resources
                .openRawResource(R.raw.third_party_license_metadata)
                .bufferedReader()
                .use { it.readText() }
        val bodies =
            context.resources.openRawResource(R.raw.third_party_licenses).use { it.readBytes() }
        val apache =
            context.assets.open("licenses/PhotoView-2.3.0-Apache-2.0.txt").bufferedReader().use {
                it.readText()
            }
        val generated =
            parse(metadata, bodies).map { entry ->
                if (entry.text.trim() in APACHE_URLS) entry.copy(text = apache) else entry
            }
        val notices =
            listOf(
                    "MBooru / Flexbooru (GPL-3.0-or-later)" to "GPL-3.0-or-later.txt",
                    "Boorusphere (BSD 3-Clause)" to "boorusphere.txt",
                    "AndroidX Preference (adapted source)" to "AndroidX-Preference.txt",
                    "PhotoView (Apache 2.0)" to "PhotoView-2.3.0-Apache-2.0.txt",
                )
                .map { (name, asset) ->
                    OpenSourceLicense(
                        name,
                        context.assets.open("licenses/$asset").bufferedReader().use {
                            it.readText()
                        },
                    )
                }
        return (generated + notices).sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    fun parse(metadata: String, bodies: ByteArray): List<OpenSourceLicense> =
        metadata
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val range = line.substringBefore(' ').split(':')
                require(range.size == 2 && ' ' in line) { "Invalid license metadata" }
                val start = range[0].toInt()
                val length = range[1].toInt()
                require(start >= 0 && length > 0 && start.toLong() + length <= bodies.size)
                val name = line.substringAfter(' ').trim()
                require(name.isNotEmpty())
                OpenSourceLicense(name, String(bodies, start, length, Charsets.UTF_8))
            }
            .toList()
            .groupBy { it.name }
            .map { (name, entries) ->
                OpenSourceLicense(name, entries.map { it.text }.distinct().joinToString("\n\n"))
            }
            .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
}

private val APACHE_URLS =
    setOf(
        "http://www.apache.org/licenses/LICENSE-2.0.txt",
        "https://www.apache.org/licenses/LICENSE-2.0.txt",
        "http://www.apache.org/licenses/LICENSE-2.0",
        "https://www.apache.org/licenses/LICENSE-2.0",
    )
