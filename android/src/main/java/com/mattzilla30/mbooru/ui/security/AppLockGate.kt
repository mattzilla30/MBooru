// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import com.mattzilla30.mbooru.ui.base.BaseActivity

/**
 * Registers before STARTED. Protected content is hidden until Android confirms the device
 * credential.
 */
class AppLockGate(private val owner: BaseActivity) {
    private var pending: (() -> Unit)? = null
    private var authenticating = false
    private var explaining = false
    private val launcher =
        owner.activityResultRegistry.register(
            "mbooru:device-lock",
            owner,
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            authenticating = false
            if (result.resultCode == Activity.RESULT_OK) {
                AppLockSession.unlock()
                resume()
            } else owner.finishAffinity()
        }

    fun open(content: () -> Unit) {
        pending = content
        resume()
    }

    fun resume() {
        if (owner.isFinishing || owner.isDestroyed) return
        if (!owner.settings.appLock || AppLockSession.unlocked) {
            owner.window.decorView.alpha = 1f
            if (owner.settings.appLock)
                owner.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else owner.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val ready = pending
            pending = null
            ready?.invoke()
        } else {
            owner.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            owner.window.decorView.alpha = 0f
            if (!authenticating && !explaining) request()
        }
    }

    private fun request() {
        val manager = owner.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent =
            manager.createConfirmDeviceCredentialIntent(
                "Unlock MBooru",
                "Confirm your Android screen lock to open your library.",
            )
        if (intent != null) {
            authenticating = true
            launcher.launch(intent)
        } else {
            explaining = true
            androidx.appcompat.app.AlertDialog.Builder(owner)
                .setTitle("Android screen lock required")
                .setMessage("Set an Android PIN, password or pattern to use app lock.")
                .setCancelable(false)
                .setPositiveButton("Open security settings") { _, _ ->
                    explaining = false
                    owner.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
                .setNegativeButton("Exit") { _, _ -> owner.finishAffinity() }
                .show()
        }
    }
}
