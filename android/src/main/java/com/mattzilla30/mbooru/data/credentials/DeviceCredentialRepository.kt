package com.mattzilla30.mbooru.data.credentials

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID

class DeviceCredentialRepository(context: Context, private val cipher: CredentialCipher) : CredentialRepository {
    private val directory = File(context.noBackupFilesDir, "credential-vault")
    override fun store(payload: String): String {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 128 * 1024) { "Credential payload too large" }
        val encrypted = cipher.encrypt(bytes)
        check(directory.isDirectory || directory.mkdirs()) { "Credential storage unavailable" }
        val id = UUID.randomUUID().toString()
        val file = AtomicFile(File(directory, id))
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
        return CredentialRepository.PREFIX + id
    }
    override fun read(reference: String): String? {
        val file = file(reference) ?: return null
        return try {
            if (!file.exists() || file.length() > 256 * 1024) return null
            cipher.decrypt(AtomicFile(file).readFully()).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            // A restored reference without its device key/payload requires sign-in again.
            null
        }
    }
    override fun remove(reference: String) { file(reference)?.let { AtomicFile(it).delete() } }
    private fun file(reference: String): File? {
        if (!isReference(reference)) return null
        val id = reference.removePrefix(CredentialRepository.PREFIX)
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) return null
        return File(directory, id)
    }
}
