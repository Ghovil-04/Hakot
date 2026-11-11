package com.example.hakot.data

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String
)

@RequiresApi(Build.VERSION_CODES.M)
class SessionManager(private val context: Context) {
    private val keyAlias = "HakotAuthKeyAlias_V2"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val tagLengthBits = 128
    private val ivLengthBytes = 12

    private val sessionFile: File by lazy {
        File(context.noBackupFilesDir, "hakot_secure_session_v2.enc")
    }

    private val atomicFile: AtomicFile by lazy {
        AtomicFile(sessionFile)
    }

    companion object {
        private val globalMutex = Mutex()
        private val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isValidUuid(id: String): Boolean {
            if (id.isBlank()) return false
            return uuidPattern.matcher(id).matches()
        }
    }

    private fun getSecretKey(allowCreation: Boolean): SecretKey? {
        try {
            val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                try {
                    val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                    if (entry != null) return entry.secretKey
                } catch (e: Exception) {
                    Log.w("HAKOT_SESSION", "Existing Keystore key alias invalid or corrupted: ${e.javaClass.simpleName} - ${e.message}. Deleting alias.")
                    try {
                        keyStore.deleteEntry(keyAlias)
                    } catch (_: Exception) {}
                }
            }
            if (!allowCreation) return null

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
        } catch (e: Exception) {
            Log.e("HAKOT_SESSION", "Failed to get or generate Android Keystore key: ${e.javaClass.simpleName} - ${e.message}")
            return null
        }
    }

    private fun clearSessionInternal() {
        try {
            atomicFile.delete()
        } catch (e: Exception) {
            Log.w("HAKOT_SESSION", "Failed to clear session file: ${e.javaClass.simpleName}")
        }
    }

    suspend fun saveSession(session: UserSession): Boolean = globalMutex.withLock {
        try {
            if (session.accessToken.isBlank() || session.refreshToken.isBlank() || session.expiresAt <= 0L || session.email.isBlank()) {
                Log.e("HAKOT_SESSION", "saveSession failed: Invalid session parameters (blank token, refresh token, email, or invalid expiry).")
                return@withLock false
            }
            if (!isValidUuid(session.userId)) {
                Log.e("HAKOT_SESSION", "saveSession failed: Invalid userId UUID format.")
                return@withLock false
            }

            // Ensure parent directory exists
            sessionFile.parentFile?.mkdirs()

            // Allow key creation when saving a valid session
            val secretKey = getSecretKey(allowCreation = true)
            if (secretKey == null) {
                Log.e("HAKOT_SESSION", "saveSession failed: SecretKey could not be retrieved or generated from Android Keystore.")
                return@withLock false
            }

            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            if (iv == null || iv.size != ivLengthBytes) {
                Log.e("HAKOT_SESSION", "saveSession failed: Generated IV is null or invalid length (${iv?.size})")
                return@withLock false
            }

            val json = JSONObject().apply {
                put("accessToken", session.accessToken)
                put("refreshToken", session.refreshToken)
                put("expiresAt", session.expiresAt)
                put("userId", session.userId)
                put("email", session.email)
                put("version", 1)
            }

            val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))

            val outputStream = try {
                atomicFile.startWrite()
            } catch (e: Exception) {
                Log.e("HAKOT_SESSION", "AtomicFile startWrite failed: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            }

            try {
                outputStream.write(iv)
                outputStream.write(ciphertext)
                outputStream.flush()
                atomicFile.finishWrite(outputStream)
                return@withLock true
            } catch (e: Exception) {
                Log.e("HAKOT_SESSION", "AtomicFile write or finishWrite failed: ${e.javaClass.simpleName} - ${e.message}")
                atomicFile.failWrite(outputStream)
                throw e
            }
        } catch (e: Exception) {
            Log.e("HAKOT_SESSION", "saveSession general exception: ${e.javaClass.simpleName} - ${e.message}")
            return@withLock false
        }
    }

    suspend fun loadSession(): UserSession? = globalMutex.withLock {
        try {
            val secretKey = getSecretKey(allowCreation = false)
            if (secretKey == null) {
                clearSessionInternal()
                return@withLock null
            }

            if (!sessionFile.exists()) {
                return@withLock null
            }

            val bytes = try {
                atomicFile.readFully()
            } catch (e: Exception) {
                Log.w("HAKOT_SESSION", "AtomicFile readFully failed: ${e.javaClass.simpleName} - ${e.message}")
                clearSessionInternal()
                return@withLock null
            }

            if (bytes.size <= ivLengthBytes) {
                clearSessionInternal()
                return@withLock null
            }

            val iv = bytes.copyOfRange(0, ivLengthBytes)
            val ciphertext = bytes.copyOfRange(ivLengthBytes, bytes.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(tagLengthBits, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            val json = JSONObject(String(decryptedBytes, Charsets.UTF_8))

            if (json.optInt("version", 0) != 1) {
                clearSessionInternal()
                return@withLock null
            }

            val accessToken = json.optString("accessToken")
            val refreshToken = json.optString("refreshToken")
            val expiresAt = json.optLong("expiresAt", 0L)
            val userId = json.optString("userId")
            val email = json.optString("email")

            if (accessToken.isBlank() || refreshToken.isBlank() || expiresAt <= 0L || userId.isBlank() || email.isBlank()) {
                clearSessionInternal()
                return@withLock null
            }

            if (!isValidUuid(userId)) {
                clearSessionInternal()
                return@withLock null
            }

            return@withLock UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                userId = userId,
                email = email
            )
        } catch (e: Exception) {
            Log.e("HAKOT_SESSION", "loadSession exception: ${e.javaClass.simpleName} - ${e.message}")
            clearSessionInternal()
            return@withLock null
        }
    }

    suspend fun clearSession() = globalMutex.withLock {
        clearSessionInternal()
    }
}
