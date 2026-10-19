// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun Rule34ConnectionScreen(
    state: Rule34ConnectionState,
    user: (String) -> Unit,
    key: (String) -> Unit,
    save: () -> Unit,
    test: () -> Unit,
    disconnect: () -> Unit,
    accountOptions: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect Rule34", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Rule34 requires your numeric user ID and API key. Paste the complete API " +
                "Access Credentials string into either field. A blank key preserves the saved " +
                "key."
        )
        OutlinedTextField(
            state.userId,
            user,
            label = { Text("Numeric user ID") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            state.keyInput,
            key,
            label = {
                Text(
                    if (state.connected) "API key (saved securely)"
                    else "API key or copied credentials"
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = accountOptions) { Text("Open Rule34 account options") }
        Button(onClick = save, enabled = !state.busy) { Text("Save") }
        OutlinedButton(onClick = test, enabled = !state.busy) { Text("Test connection") }
        if (state.connected)
            TextButton(onClick = disconnect, enabled = !state.busy) { Text("Disconnect") }
        if (state.busy) CircularProgressIndicator()
        if (state.message.isNotBlank()) Text(state.message)
        Text(
            "Credentials stay encrypted on this device and are excluded from backups. No " +
                "remote likes or collection syncing."
        )
    }
}
