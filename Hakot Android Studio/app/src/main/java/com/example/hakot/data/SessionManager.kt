package com.example.hakot.data

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
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
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                if (entry != null) return entry.secretKey
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
        } catch (_: Exception) {
            return null
        }
    }

    private fun clearSessionInternal() {
        try {
            // AtomicFile.delete() handles both base file and its internal backup (.bak) state
            atomicFile.delete()
        } catch (_: Exception) {
        }
    }

    suspend fun saveSession(session: UserSession): Boolean = globalMutex.withLock {
        try {
            if (session.accessToken.isBlank() || session.refreshToken.isBlank() || session.expiresAt <= 0L || session.email.isBlank()) {
                return@withLock false
            }
            if (!isValidUuid(session.userId)) {
                return@withLock false
            }

            // Allow key creation when saving a valid session
            val secretKey = getSecretKey(allowCreation = true) ?: return@withLock false
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
                put("email", session.email)
                put("version", 1)
            }

            val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))

            val outputStream = atomicFile.startWrite()
            try {
                outputStream.write(iv)
                outputStream.write(ciphertext)
                outputStream.flush()
                atomicFile.finishWrite(outputStream)
                return@withLock true
            } catch (e: Exception) {
                atomicFile.failWrite(outputStream)
                throw e
            }
        } catch (_: Exception) {
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

            // Allow AtomicFile.readFully() to perform its own recovery behavior (e.g. reading from .bak if base file missing)
            val bytes = try {
                atomicFile.readFully()
            } catch (_: Exception) {
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
        } catch (_: Exception) {
            // Corrupted payload, missing key, or AEADBadTagException: fail closed
            clearSessionInternal()
            return@withLock null
        }
    }

    suspend fun clearSession() = globalMutex.withLock {
        clearSessionInternal()
    }
}
