package com.mattzilla30.mbooru.worker

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/** A transfer becomes a final file only after all bytes have been validated and flushed. */
internal fun publishDownload(context: Context, part: Uri, requestedName: String): Uri {
    val resolver = context.contentResolver
    val name =
        requestedName.replace(Regex("[\\\\/\\x00-\\x1f]"), "_").take(180).ifBlank { "download" }
    // Providers must disambiguate name collisions. Never delete an existing completed file.
    return DocumentsContract.renameDocument(resolver, part, name)
        ?: throw IOException("Provider cannot publish the partial download")
}
