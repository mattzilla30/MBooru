package com.mattzilla30.mbooru.data.credentials

/** References are safe to back up; payloads remain encrypted in device-only storage. */
interface CredentialRepository {
    fun store(payload: String): String

    fun read(reference: String): String?

    fun remove(reference: String)

    fun isReference(value: String): Boolean = value.startsWith(PREFIX)

    companion object {
        const val PREFIX = "mbooru-credential:v1:"
    }
}

interface CredentialCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}
