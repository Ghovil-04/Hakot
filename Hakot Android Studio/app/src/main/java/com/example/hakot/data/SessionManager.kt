package com.example.hakot.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String
)

@RequiresApi(Build.VERSION_CODES.M)
class SessionManager(private val context: Context) {
    private val keyAlias = "HakotAuthKeyAlias"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val tagLengthBits = 128
    private val ivLengthBytes = 12

    private val sessionFile: File by lazy {
        File(context.noBackupFilesDir, "hakot_secure_session.enc")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        val parameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    fun saveSession(session: UserSession) {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(transformation)
            val iv = ByteArray(ivLengthBytes)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(tagLengthBits, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            val json = JSONObject().apply {
                put("accessToken", session.accessToken)
                put("refreshToken", session.refreshToken)
                put("expiresAt", session.expiresAt)
                put("userId", session.userId)
                put("version", 1)
            }

            val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
            
            // Atomic file replacement using temp file
            val tempFile = File(context.noBackupFilesDir, "hakot_secure_session.tmp")
            tempFile.outputStream().use { fos ->
                fos.write(iv)
                fos.write(ciphertext)
            }
            if (sessionFile.exists()) sessionFile.delete()
            tempFile.renameTo(sessionFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSession(): UserSession? {
        try {
            if (!sessionFile.exists()) return null
            val bytes = sessionFile.readBytes()
            if (bytes.size <= ivLengthBytes) return null

            val iv = bytes.copyOfRange(0, ivLengthBytes)
            val ciphertext = bytes.copyOfRange(ivLengthBytes, bytes.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(tagLengthBits, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))

            return UserSession(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                expiresAt = json.getLong("expiresAt"),
                userId = json.getString("userId")
            )
        } catch (e: Exception) {
            clearSession()
            e.printStackTrace()
            return null
        }
    }

    fun clearSession() {
        try {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
