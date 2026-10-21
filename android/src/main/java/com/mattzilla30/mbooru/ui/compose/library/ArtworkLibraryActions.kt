// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattzilla30.mbooru.data.model.common.Post

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun ArtworkLibraryActions(post: Post, model: ArtworkLibraryViewModel) {
    LaunchedEffect(post.booruUid, post.id) { model.select(post) }
    val saved by model.saved.collectAsStateWithLifecycle()
    val liked by model.liked.collectAsStateWithLifecycle()
    val groups by model.collections.collectAsStateWithLifecycle()
    val members by model.memberships.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var showing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TextButton(onClick = model::save) {
            Text(if (saved) "Remove from favorites" else "Add to favorites")
        }
        TextButton(onClick = model::like) { Text(if (liked) "Unlike locally" else "Like locally") }
        TextButton(onClick = { showing = true }) { Text("Collections") }
    }
    if (showing)
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("Collections") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    groups.forEach { group ->
                        Row {
                            Checkbox(
                                group.uid in members,
                                onCheckedChange = { model.membership(group.uid, it) },
                            )
                            Text(group.name, Modifier.padding(top = 12.dp))
                        }
                    }
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("New collection") },
                        singleLine = true,
                    )
                    TextButton(onClick = { model.create(name) { name = "" } }) {
                        Text("Create and add artwork")
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { showing = false }) { Text("Done") } },
        )
}
