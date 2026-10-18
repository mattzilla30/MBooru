package com.mattzilla30.mbooru.ui.compose.common

import android.content.Context
import android.net.*
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R

@Composable
fun rememberOnlineState(): State<Boolean> {
    val context = LocalContext.current
    return produceState(true, context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun connected(): Boolean =
            if (Build.VERSION.SDK_INT >= 23) {
                manager.activeNetwork
                    ?.let { manager.getNetworkCapabilities(it) }
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                manager.activeNetworkInfo?.isConnected == true
            }
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    value = connected()
                }

                override fun onLost(network: Network) {
                    value = connected()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    value = connected()
                }
            }
        value = connected()
        manager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        awaitDispose { manager.unregisterNetworkCallback(callback) }
    }
}

@Composable
fun ConnectivityBanner() {
    val online by rememberOnlineState()
    if (!online)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(R.string.browse_offline),
                Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
}
