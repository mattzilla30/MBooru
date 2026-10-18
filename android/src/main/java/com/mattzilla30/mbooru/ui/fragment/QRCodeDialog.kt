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

package com.mattzilla30.mbooru.ui.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys.BOORU_URL
import com.mattzilla30.mbooru.ui.compose.common.composeView
import java.nio.charset.StandardCharsets

// https://github.com/shadowsocks/shadowsocks-android/blob/master/mobile/src/main/java/com/github/shadowsocks/ProfilesFragment.kt
class QRCodeDialog() : DialogFragment() {

    companion object {
        private val iso88591 = StandardCharsets.ISO_8859_1.newEncoder()
    }

    @SuppressLint("ValidFragment")
    constructor(url: String) : this() {
        arguments = bundleOf(Pair(BOORU_URL, url))
    }

    private val url
        get() = arguments?.getString(BOORU_URL)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val url = url!!
        val size = resources.getDimensionPixelSize(R.dimen.qr_code_size)
        val hints = mutableMapOf<EncodeHintType, Any>()
        if (!iso88591.canEncode(url))
            hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8.name()
        val qrBits =
            try {
                MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints)
            } catch (_: WriterException) {
                null
            }
        if (qrBits == null) {
            dismiss()
            return null
        }
        val bitmap =
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                for (x in 0 until size) for (y in 0 until size) {
                    setPixel(x, y, if (qrBits.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
        return composeView {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = url,
                modifier =
                    Modifier.size((size / resources.displayMetrics.density).dp)
                        .background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}
