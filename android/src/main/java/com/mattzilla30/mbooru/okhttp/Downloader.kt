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

package com.mattzilla30.mbooru.okhttp

import android.net.Uri
import java.io.IOException
import okhttp3.Response

/** A mechanism to load images from external resources such as a disk cache and/or the internet. */
interface Downloader {
    /**
     * Download the specified image `url` from the internet.
     *
     * @throws IOException if the requested URL cannot successfully be loaded.
     */
    @Throws(IOException::class) fun load(request: okhttp3.Request): Response

    /**
     * Download the specified image `url` from the internet.
     *
     * @throws IOException if the requested URL cannot successfully be loaded.
     */
    @Throws(IOException::class) fun load(uri: Uri): Response

    @Throws(IOException::class) fun load(url: String): Response

    /**
     * Allows to perform a clean up for this [Downloader] including closing the disk cache and other
     * resources.
     */
    fun shutdown()
}
