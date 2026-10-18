package com.mattzilla30.mbooru.data.credentials

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/** AES-GCM; API 21–22 wrap a random AES key with an Android Keystore RSA key. */
class AndroidCredentialCipher(private val context: Context, private val alias: String = "mbooru.credentials.v1") : CredentialCipher {
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (Build.VERSION.SDK_INT >= 23) {
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        }
        if (!store.containsAlias(alias)) {
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            @Suppress("DEPRECATION")
            val spec = KeyPairGeneratorSpec.Builder(context).setAlias(alias)
                .setSubject(X500Principal("CN=MBooru credentials")).setSerialNumber(BigInteger.ONE)
                .setStartDate(java.util.Date()).setEndDate(end.time).setKeySize(2048).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val wrappedFile = AtomicFile(File(context.noBackupFilesDir, "$alias.wrapped"))
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        if (wrappedFile.baseFile.exists()) {
            rsa.init(Cipher.DECRYPT_MODE, store.getKey(alias, null))
            return SecretKeySpec(rsa.doFinal(wrappedFile.readFully()), "AES")
        }
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        rsa.init(Cipher.ENCRYPT_MODE, store.getCertificate(alias).publicKey)
        val wrapped = rsa.doFinal(aes.encoded)
        val output = wrappedFile.startWrite()
        try { output.write(wrapped); wrappedFile.finishWrite(output) }
        catch (failure: Exception) { wrappedFile.failWrite(output); throw failure }
        return aes
    }
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }
    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 29 && ciphertext[0] == 1.toByte()) { "Invalid credential envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, ciphertext.copyOfRange(1,13)))
        return cipher.doFinal(ciphertext.copyOfRange(13,ciphertext.size))
    }
}
