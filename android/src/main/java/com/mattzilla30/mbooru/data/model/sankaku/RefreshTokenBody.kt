package com.mattzilla30.mbooru.data.model.sankaku

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class RefreshTokenBody(@SerialName("refresh_token") val refreshToken: String)
