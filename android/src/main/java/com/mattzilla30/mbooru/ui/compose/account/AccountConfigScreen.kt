package com.mattzilla30.mbooru.ui.compose.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru

@Composable
fun AccountConfigScreen(state: AccountConfigState, booru: Booru,
    username: (String) -> Unit, token: (String) -> Unit, submit: () -> Unit, forgot: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp)) {
        Text(stringResource(R.string.title_account_config), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.title_account_config_and_booru, booru.name), Modifier.padding(vertical = 16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state.username, username, label = { Text(stringResource(R.string.account_username)) },
                    singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(state.token, token, label = { Text(stringResource(if (booru.type == Values.BOORU_TYPE_DAN)
                    R.string.account_api_key else R.string.account_password)) }, singleLine = true,
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
                TextButton(onClick = forgot, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(if (booru.type == Values.BOORU_TYPE_DAN) R.string.account_forgot_api_key else R.string.account_forgot_password))
                }
                if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                else Button(onClick = submit, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.account_set)) }
            }
        }
        val error = state.errorResource?.let { stringResource(it) } ?: state.error
        if (!error.isNullOrEmpty()) Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
}
