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

package com.mattzilla30.mbooru.extension

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.base.PathActivity
import java.util.*

suspend fun PathActivity.getSaveUri(fileName: String): Uri? = getFileUri("save", fileName)

suspend fun PathActivity.getPoolUri(fileName: String): Uri? = getFileUri("pools", fileName, true)

suspend fun PathActivity.getDownloadUri(host: String, fileName: String): Uri? =
    getFileUri(host, fileName, true)

/**
 * New queue entries retain the complete provider URI; old entries still resolve their document ID.
 */
fun ContentResolver.getFileUriByDocId(docId: String): Uri? {
    val uri = Uri.parse(docId)
    if (uri.scheme == "content") return uri
    val treeUri = getTreeUri() ?: return null
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
}

fun ContentResolver.getTreeUri(preferred: String? = null): Uri? {
    val permissions = persistedUriPermissions.filter { it.isReadPermission && it.isWritePermission }
    return permissions
        .firstOrNull {
            it.uri.toString() == preferred || Uri.decode(it.uri.toString()) == preferred
        }
        ?.uri ?: if (preferred == null) permissions.firstOrNull()?.uri else null
}

private suspend fun PathActivity.getFileUri(
    dirName: String,
    fileName: String,
    staged: Boolean = false,
): Uri? {
    val result =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val treeUri =
                    contentResolver.getTreeUri(settings.downloadDirPath) ?: return@withContext null
                val root =
                    DocumentFile.fromTreeUri(this@getFileUri, treeUri) ?: return@withContext null
                if (!root.canWrite()) return@withContext null
                val directory =
                    (root.findFile(dirName)?.takeIf { it.isDirectory }
                        ?: if (root.findFile(dirName) == null) root.createDirectory(dirName)
                        else null) ?: return@withContext null
                val safeName =
                    fileName.replace(Regex("[\\\\/\u0000-\u001f]"), "_").take(180).ifBlank {
                        "download"
                    }
                // Never reuse/truncate an existing document, including providers with opaque IDs.
                val name =
                    if (staged) "$safeName.${UUID.randomUUID()}.part"
                    else {
                        if (directory.findFile(safeName) == null) safeName
                        else "${UUID.randomUUID()}-$safeName"
                    }
                directory
                    .createFile(
                        if (staged) "application/octet-stream"
                        else safeName.getMimeType().ifBlank { "application/octet-stream" },
                        name,
                    )
                    ?.uri
            } catch (_: SecurityException) {
                null
            } catch (_: java.io.IOException) {
                null
            }
        }
    if (result == null)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            Toast.makeText(this@getFileUri, R.string.msg_path_denied, Toast.LENGTH_LONG).show()
            pickDir()
        }
    return result
}

fun String.getMimeType(): String {
    var extension = fileExt()
    // Convert the URI string to lower case to ensure compatibility with MimeTypeMap (see CB-2185).
    extension = extension.lowercase(Locale.getDefault())
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
}

fun String.fileExt(): String {
    val start = lastIndexOf('.') + 1
    val end = indexOfFirst { it == '?' }
    if (start == 0) {
        return ""
    }
    return if (end > start) {
        substring(start, end)
    } else {
        substring(start)
    }
}

fun String.fileName(): String {
    val start = lastIndexOf('/') + 1
    val end = indexOfFirst { it == '?' }
    val encodeFileName =
        if (end > start) {
            substring(start, end)
        } else {
            substring(start)
        }
    return encodeFileName
        .toDecodedString()
        .replace("?", "")
        .replace("!", "")
        .replace(":", "_")
        .replace("\"", "_")
}

fun String.isImage(): Boolean {
    return isGifImage() || isStillImage()
}

fun String.isVideo(): Boolean {
    val ext = fileExt()
    return ext.equals("mp4", ignoreCase = true) ||
        ext.equals("webm", ignoreCase = true) ||
        ext.equals("mkv", ignoreCase = true) ||
        ext.equals("avi", ignoreCase = true) ||
        ext.equals("mov", ignoreCase = true) ||
        ext.equals("wmv", ignoreCase = true) ||
        ext.equals("flv", ignoreCase = true)
}

fun String.isStillImage(): Boolean {
    val ext = fileExt()
    return ext.equals("jpg", ignoreCase = true) ||
        ext.equals("png", ignoreCase = true) ||
        ext.equals("jpeg", ignoreCase = true) ||
        ext.equals("webp", ignoreCase = true)
}

fun String.isGifImage(): Boolean = fileExt().equals("gif", ignoreCase = true)

fun Uri.toDecodedString(): String = toString().toDecodedString()

fun String.toDecodedString(): String = Uri.decode(this)
