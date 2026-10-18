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

package com.mattzilla30.mbooru.ui.base

import android.content.Intent
import android.os.Bundle
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.extension.toDecodedString
import com.mattzilla30.mbooru.ui.helper.StorageFolderLifecycleObserver

abstract class PathActivity : BaseActivity() {

    private lateinit var observer: StorageFolderLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observer = StorageFolderLifecycleObserver(activityResultRegistry) { uri ->
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                // Acquire the new grant before releasing any working destination.
                contentResolver.takePersistableUriPermission(uri, flags)
                val previous = contentResolver.persistedUriPermissions
                Settings.downloadDirPath = uri.toDecodedString()
                previous.filter { it.isWritePermission && it.uri != uri }.forEach { permission ->
                    val oldFlags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try { contentResolver.releasePersistableUriPermission(permission.uri, oldFlags) }
                    catch (_: SecurityException) { /* The provider may already have revoked it. */ }
                }
            } catch (_: SecurityException) {
                android.widget.Toast.makeText(this, com.mattzilla30.mbooru.R.string.msg_path_denied,
                    android.widget.Toast.LENGTH_LONG).show()
            }

        }
        lifecycle.addObserver(observer)
    }

    fun pickDir() {
        observer.openDocumentTree(this)
    }
}