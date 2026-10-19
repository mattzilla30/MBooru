// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import com.mattzilla30.mbooru.data.model.common.User
import java.net.URLDecoder

/** Values are transient; persisted accounts use MBooru's existing encrypted credential vault. */
data class Rule34Credentials(val userId: String, val key: String) {
    override fun toString() = "Rule34Credentials(redacted)"

    fun asUser() = User(id = userId.toIntOrNull() ?: -1, name = userId, token = key)

    companion object {
        fun parse(
            userInput: String,
            keyInput: String,
            existingKey: String = "",
        ): Rule34Credentials {
            var user = userInput.trim()
            var key = keyInput.trim().ifBlank { existingKey }
            val pattern =
                Regex("(?:^|[?&])(?:amp;)?(user_id|api_key)=([^&#]*)", RegexOption.IGNORE_CASE)
            for (input in listOf(userInput, keyInput)) {
                pattern.findAll(input.trim()).forEach { match ->
                    val value = decodeCopied(match.groupValues[2])
                    if (match.groupValues[1].equals("user_id", true)) user = value else key = value
                }
            }
            if (!user.matches(Regex("[0-9]{1,19}")) || user.toLongOrNull()?.let { it > 0 } != true)
                throw Rule34Exception("Enter the numeric user_id from Rule34 account options.")
            validateKey(key)
            return Rule34Credentials(user, key)
        }

        private fun decodeCopied(value: String): String =
            try {
                URLDecoder.decode(value, "UTF-8").trim()
            } catch (_: IllegalArgumentException) {
                throw Rule34Exception("The copied credentials contain invalid URL encoding.")
            }

        private fun validateKey(key: String) {
            val invalidCharacters = key.any { it.isWhitespace() || it in "&?#" }
            val invalidLength = key.isBlank() || key.length > MAX_KEY_LENGTH
            if (invalidLength || invalidCharacters || key.contains("api_key="))
                throw Rule34Exception(
                    "Enter an API key or paste the complete API Access Credentials string."
                )
        }

        fun from(user: User?): Rule34Credentials =
            parse(user?.name.orEmpty(), user?.token.orEmpty())
    }
}

class Rule34Exception(val safeMessage: String) : java.io.IOException(safeMessage)

private const val MAX_KEY_LENGTH = 4096
