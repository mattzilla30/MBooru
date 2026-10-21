/*
 * Copyright (C) 2026 mattzilla30 <mattzilla30@gmail.com> (MBooru modifications)
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.mattzilla30.mbooru.ui.compose.about

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mattzilla30.mbooru.BuildConfig
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.activity.CopyrightActivity

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    fun getString(id: Int) = context.getString(id)
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.testTag("about-list")) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = {
                            android.widget.ImageView(it).apply {
                                setImageResource(R.mipmap.ic_launcher)
                            }
                        },
                        modifier = Modifier.size(96.dp),
                    )
                    Text(
                        "MBooru",
                        Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { AboutSection(R.string.about_app) }
            item { AboutRow(R.string.about_app_version, BuildConfig.VERSION_NAME) }
            item { AboutSection(R.string.about_author) }
            item {
                AboutRow(
                    R.string.about_author_email,
                    stringResource(R.string.about_email_address),
                ) {
                    val intent =
                        Intent(
                            Intent.ACTION_SENDTO,
                            getString(R.string.about_email_address).let { "mailto:$it" }.toUri(),
                        )
                    context.startActivity(
                        Intent.createChooser(intent, getString(R.string.share_via))
                    )
                }
            }
            item { AboutSection(R.string.about_feedback) }
            item {
                AboutRow(
                    R.string.about_feedback_discord,
                    stringResource(R.string.about_discord_username),
                ) {
                    context.copyText(getString(R.string.about_discord_username))
                }
            }
            item {
                AboutRow(
                    R.string.about_feedback_github,
                    "https://github.com/mattzilla30/MBooru/issues",
                ) {
                    context.launchUrl("https://github.com/mattzilla30/MBooru/issues")
                }
            }
            item { AboutSection(R.string.about_copyright_and_licenses) }
            item {
                AboutRow(
                    R.string.m34_feature_credit,
                    stringResource(R.string.m34_feature_credit_summary),
                ) {
                    context.launchUrl("https://github.com/mattzilla30/M34")
                }
            }
            item {
                AboutRow(
                    R.string.sphere_ui_credit,
                    stringResource(R.string.sphere_ui_credit_summary),
                ) {
                    context.launchUrl("https://github.com/azrim/boorusphere")
                }
            }
            item {
                AboutRow(R.string.sphere_license, "BSD 3-Clause") {
                    context.startActivity(
                        Intent(
                            context,
                            com.mattzilla30.mbooru.ui.activity.DesignLicenseActivity::class.java,
                        )
                    )
                }
            }

            item {
                AboutRow(R.string.about_copyright, stringResource(R.string.about_owner_copyright)) {
                    context.let { context.startActivity(Intent(it, CopyrightActivity::class.java)) }
                }
            }
            item {
                AboutRow(
                    R.string.about_upstream_project,
                    stringResource(R.string.about_upstream_credit),
                ) {
                    context.launchUrl("https://github.com/flexbooru/flexbooru")
                }
            }
            item {
                AboutRow(
                    R.string.about_licenses,
                    stringResource(R.string.about_open_source_licenses),
                ) {
                    context.startActivity(
                        Intent(
                            context,
                            com.mattzilla30.mbooru.ui.activity.LicensesActivity::class.java,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(title: Int) {
    Text(
        stringResource(title),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun AboutRow(title: Int, summary: String, onClick: (() -> Unit)? = null) {
    val action = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Column(
        Modifier.fillMaxWidth()
            .then(action)
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
