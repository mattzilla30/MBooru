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

package com.mattzilla30.mbooru.data.repository.suggestion

import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL_LEGACY
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.model.common.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SuggestionRepositoryImpl(private val booruApis: BooruApis) : SuggestionRepository {

    override suspend fun fetchSuggestions(action: ActionTag): List<Tag>? {
        return when (action.booru.type) {
            com.mattzilla30.mbooru.app.Values.BOORU_TYPE_RULE34,
            com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE -> emptyList()
            BOORU_TYPE_DAN -> getDanTags(action)
            BOORU_TYPE_DAN1 -> getDan1Tags(action)
            BOORU_TYPE_MOE -> getMoeTags(action)
            BOORU_TYPE_GEL -> getGelTags(action)
            BOORU_TYPE_GEL_LEGACY -> getGelTagsLegacy(action)
            else -> getSankakuTags(action)
        }
    }

    private suspend fun getDanTags(action: ActionTag): List<Tag>? {
        return withContext(Dispatchers.IO) {
            try {
                booruApis.danApi.getTags(action.getDanTagsUrl(1)).body()?.map { it.toTag() }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null
            }
        }
    }

    private suspend fun getDan1Tags(action: ActionTag): List<Tag>? {
        return withContext(Dispatchers.IO) {
            try {
                booruApis.dan1Api.getTags(action.getDan1TagsUrl(1)).body()?.map { it.toTag() }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null
            }
        }
    }

    private suspend fun getMoeTags(action: ActionTag): List<Tag>? {
        return withContext(Dispatchers.IO) {
            try {
                booruApis.moeApi.getTags(action.getMoeTagsUrl(1)).body()?.map { it.toTag() }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null
            }
        }
    }

    private suspend fun getGelTags(action: ActionTag): List<Tag>? {
        return withContext(Dispatchers.IO) {
            try {
                booruApis.gelApi.getTags(action.getGelTagsUrl(0)).body()?.tags?.map { it.toTag() }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null
            }
        }
    }

    private suspend fun getGelTagsLegacy(action: ActionTag): List<Tag>? {
        return withContext(Dispatchers.IO) {
            try {
                booruApis.gelApi.getTagsLegacy(action.getGelTagsUrl(0)).body()?.tags?.map {
                    it.toTag()
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null
            }
        }
    }

    private suspend fun getSankakuTags(action: ActionTag): List<Tag>? {
        return withContext(Dispatchers.IO) {
            try {
                booruApis.sankakuApi.getTags(action.getSankakuTagsUrl(1)).body()?.map { it.toTag() }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                null
            }
        }
    }
}
