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

package com.mattzilla30.mbooru.data.repository.artist

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.mattzilla30.mbooru.data.action.ActionArtist
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.model.common.Artist
import kotlinx.coroutines.flow.Flow

class ArtistRepositoryImpl(private val booruApis: BooruApis) : ArtistRepository {

    override fun getArtists(action: ActionArtist): Flow<PagingData<Artist>> {
        return Pager(
                config =
                    PagingConfig(
                        pageSize = action.limit,
                        enablePlaceholders = true,
                    ),
                initialKey = 1,
            ) {
                ArtistPagingSource(action, booruApis)
            }
            .flow
    }
}
