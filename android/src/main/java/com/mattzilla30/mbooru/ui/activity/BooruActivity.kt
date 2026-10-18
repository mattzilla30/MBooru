/*
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

package com.mattzilla30.mbooru.ui.activity

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.database.BooruPresets
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.extension.safeCloseQuietly
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.booru.BooruList
import com.mattzilla30.mbooru.ui.compose.common.*
import com.mattzilla30.mbooru.ui.fragment.QRCodeDialog
import com.mattzilla30.mbooru.ui.helper.CreateFileLifecycleObserver
import com.mattzilla30.mbooru.ui.helper.OpenFileLifecycleObserver
import com.mattzilla30.mbooru.ui.viewmodel.BooruViewModel
import com.mattzilla30.mbooru.ui.viewmodel.getBooruViewModel
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject

class BooruActivity : BaseActivity() {

    private val booruDao by inject<BooruDao>()
    private val preferences by inject<android.content.SharedPreferences>()

    private lateinit var booruViewModel: BooruViewModel
    private lateinit var createFileObserver: CreateFileLifecycleObserver
    private lateinit var openFileObserver: OpenFileLifecycleObserver

    private val clipboard by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createFileObserver =
            CreateFileLifecycleObserver(activityResultRegistry) { uri ->
                saveFile(uri)
            }
        openFileObserver =
            OpenFileLifecycleObserver(activityResultRegistry) { uri ->
                openFile(uri)
            }
        lifecycle.addObserver(createFileObserver)
        lifecycle.addObserver(openFileObserver)
    }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.title_manage_boorus)
        booruViewModel = getBooruViewModel(booruDao)
        lifecycleScope.launch { BooruPresets.installOnce(booruDao, preferences) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                booruViewModel.loadBoorus().collect { boorus ->
                    if (boorus.isNullOrEmpty()) {
                        settings.activatedBooruUid = -1L
                    } else {
                        val uid = settings.activatedBooruUid
                        if (boorus.indexOfFirst { it.uid == uid } < 0) {
                            settings.activatedBooruUid = boorus[0].uid
                        }
                    }
                    booruViewModel.select(settings.activatedBooruUid)
                }
            }
        }
        booruViewModel.select(settings.activatedBooruUid)
        setComposeContent {
            val sites by booruViewModel.boorusState.collectAsStateWithLifecycle()
            val selected by booruViewModel.selectedUid.collectAsStateWithLifecycle()
            Column {
                BooruList(
                    sites,
                    selected,
                    select = {
                        settings.activatedBooruUid = it
                        booruViewModel.select(it)
                    },
                    delete = booruViewModel::deleteBooru,
                    action = { booru, id ->
                        when (id) {
                            AppActionKey.BooruShareQrCode ->
                                QRCodeDialog(booru.toString()).show(supportFragmentManager, "qr")
                            AppActionKey.BooruShareClipboard -> copyText(booru.toString())
                            AppActionKey.BooruEdit ->
                                startActivity(
                                    Intent(this@BooruActivity, BooruConfigActivity::class.java)
                                        .putExtra(BooruConfigActivity.EXTRA_BOORU_UID, booru.uid)
                                )
                            AppActionKey.BooruDelete -> booruViewModel.deleteBooru(booru.uid)
                            else -> Unit
                        }
                    },
                )
            }
        }
        if (intent != null) {
            handleShareIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        val sharedStr = intent.data?.toString()
        if (sharedStr.isNullOrEmpty() || isFinishing) {
            return
        }
        val booru = Booru.url2Booru(sharedStr) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.booru_add_title_dialog)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                booruViewModel.createBooru(booru)
            }
            .setNegativeButton(R.string.dialog_no, null)
            .setMessage(sharedStr)
            .create()
            .show()
    }

    override fun onAction(action: AppActionKey): Boolean {
        return when (action) {
            AppActionKey.Back -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            AppActionKey.BooruAddFeatured -> {
                addFeaturedBooru()
                true
            }
            AppActionKey.BooruAddQr -> {
                addConfigFromQRCode()
                true
            }
            AppActionKey.BooruAddClipboard -> {
                addConfigFromClipboard()
                true
            }
            AppActionKey.BooruAddManual -> {
                addConfig()
                true
            }
            AppActionKey.BooruBackupToFile -> {
                backupToFile()
                true
            }
            AppActionKey.BooruRestoreFromFile -> {
                restoreFromFile()
                true
            }
            else -> super.onAction(action)
        }
    }

    private fun backupToFile() {
        createFileObserver.createDocument("boorus.json")
    }

    private fun restoreFromFile() {
        val mimeType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "application/json"
            else "application/octet-stream"
        openFileObserver.openDocument(mimeType)
    }

    private fun addFeaturedBooru() {
        val presets = BooruPresets.featured()
        AlertDialog.Builder(this)
            .setTitle(R.string.booru_add_featured)
            .setItems(presets.map { "${it.name} (${it.host})" }.toTypedArray()) { _, index ->
                lifecycleScope.launch {
                    val uid = BooruPresets.add(booruDao, presets[index])
                    settings.activatedBooruUid = uid
                    booruViewModel.select(uid)
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun addConfig() {
        startActivity(Intent(this@BooruActivity, BooruConfigActivity::class.java))
    }

    private fun addConfigFromQRCode() {
        startActivity(Intent(this, ScannerActivity::class.java))
    }

    private fun addConfigFromClipboard() {
        val text = clipboard.primaryClip?.getItemAt(0)?.text
        if (text != null || Uri.parse(text.toString()).scheme != "booru") {
            val booru = Booru.url2Booru(text.toString())
            if (booru != null) {
                booruViewModel.createBooru(booru)
            } else {
                Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.booru_add_error,
                        Snackbar.LENGTH_LONG,
                    )
                    .show()
            }
        } else {
            Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.booru_add_error,
                    Snackbar.LENGTH_LONG,
                )
                .show()
        }
    }

    private val json
        get() = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private fun saveFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Main) {
            val success =
                withContext(Dispatchers.IO) {
                    val boorus = booruViewModel.boorusState.value
                    if (boorus.isEmpty()) return@withContext false
                    val outputStream =
                        contentResolver.openOutputStream(uri) ?: return@withContext false
                    var inputStream: InputStream? = null
                    try {
                        inputStream =
                            com.mattzilla30.mbooru.data.backup.SiteBackups.encode(boorus)
                                .byteInputStream()
                        inputStream.copyTo(outputStream)
                    } catch (_: IOException) {
                        return@withContext false
                    } finally {
                        inputStream?.safeCloseQuietly()
                        outputStream.safeCloseQuietly()
                    }
                    return@withContext true
                }
            if (success) {
                Toast.makeText(this@BooruActivity, "Success: ${uri.path}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@BooruActivity, "Save failed!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val sites =
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use {
                            com.mattzilla30.mbooru.data.backup.SiteBackups.decode(it)
                        } ?: throw IOException("Backup is unavailable")
                    }
                booruDao.importSites(sites)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.booru_add_error,
                        Snackbar.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }
}
