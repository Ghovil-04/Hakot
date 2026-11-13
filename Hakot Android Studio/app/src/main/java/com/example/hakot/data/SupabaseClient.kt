package com.example.hakot.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class SupabaseFetchResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val data: JSONArray?,
    val errorMessage: String?
)

data class SupabaseInsertResult(
    val isSuccess: Boolean,
    val statusCode: Int,
    val errorMessage: String?
)

data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String
)

data class RefreshSessionResult(
    val tokenResponse: AuthTokenResponse?,
    val isPermanentFailure: Boolean,
    val statusCode: Int
)

data class FetchProfileResult(
    val profile: JSONObject?,
    val isPermanentFailure: Boolean,
    val statusCode: Int
)

object SupabaseClient {

    const val SUPABASE_URL = "https://vyegsnkerrbdqacwegff.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_rd7efjfP3gHndcibiIVzHw_sshkqRxv"

    var connectionState: String by mutableStateOf("Checking...")
    var lastSyncTimestamp: String by mutableStateOf("Not Synced Yet")
    var gisLoadError: String by mutableStateOf("")

    private val uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private fun isValidUuid(id: String): Boolean {
        if (id.isBlank()) return false
        return uuidPattern.matcher(id).matches()
    }

    private fun currentTimeString(): String {
        return SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    private fun readErrorStream(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
        } catch (_: Exception) {
            "Failed to read error stream"
        }
    }

    private fun parseAuthResponse(jsonText: String): AuthTokenResponse? {
        try {
            val json = JSONObject(jsonText)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            
            // Strict expires_in validation without type coercion from strings or fractional values
            if (!json.has("expires_in") || json.isNull("expires_in")) return null
            val expiresVal = json.get("expires_in")
            
            val expiresIn: Long = when (expiresVal) {
                is Int -> expiresVal.toLong()
                is Long -> expiresVal
                is Number -> {
                    val d = expiresVal.toDouble()
                    if (d % 1.0 != 0.0) return null // Reject fractional values
                    d.toLong()
                }
                else -> return null // Reject Strings, booleans, null, non-numeric values
            }

            if (expiresIn <= 0 || expiresIn > 604800) return null

            val userObj = json.optJSONObject("user")
            val userId = userObj?.optString("id") ?: ""

            if (accessToken.isBlank() || refreshToken.isBlank()) return null
            if (!isValidUuid(userId)) return null

            // Safe expiry timestamp calculation avoiding overflow
            val maxDelta = 604800_000L
            val safeDelta = minOf(expiresIn * 1000, maxDelta)
            val expiresAt = System.currentTimeMillis() + safeDelta

            return AuthTokenResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                userId = userId
            )
        } catch (_: Exception) {
            return null
        }
    }

    // ---------------------------------------------------------
    // SUPABASE AUTHENTICATION
    // ---------------------------------------------------------

    suspend fun signIn(emailInput: String, passwordInput: String): AuthTokenResponse? =
        withContext(Dispatchers.IO) {
            if (emailInput.isBlank() || passwordInput.isBlank()) return@withContext null
            var connection: HttpURLConnection? = null
            try {
                val urlString = "$SUPABASE_URL/auth/v1/token?grant_type=password"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val payload = JSONObject().apply {
                    put("email", emailInput)
                    put("password", passwordInput)
                }
                val bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bodyBytes.size)
                connection.outputStream.use { it.write(bodyBytes) }

                val code = connection.responseCode
                Log.d("HAKOT_AUTH", "signIn HTTP status code: $code")
                if (code in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext parseAuthResponse(jsonText)
                } else {
                    val errText = readErrorStream(connection)
                    val sanitizedErr = if (errText.contains("invalid_grant", true)) "Invalid credentials" else "HTTP error $code"
                    Log.e("HAKOT_AUTH", "signIn failed with HTTP $code: $sanitizedErr")
                }
            } catch (e: Exception) {
                Log.e("HAKOT_AUTH", "signIn exception: ${e.message}")
            } finally {
                connection?.disconnect()
            }
            null
        }

    suspend fun refreshSession(refreshTokenInput: String): RefreshSessionResult =
        withContext(Dispatchers.IO) {
            if (refreshTokenInput.isBlank()) {
                return@withContext RefreshSessionResult(null, true, 400)
            }
            var connection: HttpURLConnection? = null
            try {
                val urlString = "$SUPABASE_URL/auth/v1/token?grant_type=refresh_token"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val payload = JSONObject().apply {
                    put("refresh_token", refreshTokenInput)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                if (code in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val tokenResp = parseAuthResponse(jsonText)
                    return@withContext RefreshSessionResult(tokenResp, tokenResp == null, code)
                } else {
                    val errText = readErrorStream(connection)
                    Log.e("HAKOT_AUTH", "refreshSession failed with HTTP $code: $errText")
                    val isPermanent = code in 400..403
                    return@withContext RefreshSessionResult(null, isPermanent, code)
                }
            } catch (e: Exception) {
                Log.e("HAKOT_AUTH", "refreshSession exception: ${e.message}")
                return@withContext RefreshSessionResult(null, false, -1)
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun signOutRemote(accessToken: String): Boolean =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) return@withContext false
            var connection: HttpURLConnection? = null
            try {
                val urlString = "$SUPABASE_URL/auth/v1/logout"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                return@withContext connection.responseCode in 200..299
            } catch (_: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun recoverPassword(email: String): SupabaseFetchResult =
        withContext(Dispatchers.IO) {
            if (email.isBlank()) {
                return@withContext SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = 400,
                    data = null,
                    errorMessage = "Email cannot be blank"
                )
            }
            var connection: HttpURLConnection? = null
            try {
                val encodedRedirect = URLEncoder.encode("hakot://auth/invite-callback", "UTF-8")
                val urlString = "$SUPABASE_URL/auth/v1/recover?redirect_to=$encodedRedirect"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val payload = JSONObject().apply {
                    put("email", email.trim())
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    SupabaseFetchResult(
                        isSuccess = true,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = null
                    )
                } else {
                    val errorText = readErrorStream(connection)
                    SupabaseFetchResult(
                        isSuccess = false,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = "HTTP $responseCode: $errorText"
                    )
                }
            } catch (e: Exception) {
                val parsedHost = try {
                    val encodedRedirect = URLEncoder.encode("hakot://auth/invite-callback", "UTF-8")
                    val urlString = "$SUPABASE_URL/auth/v1/recover?redirect_to=$encodedRedirect"
                    URL(urlString).host
                } catch (_: Exception) {
                    "unknown"
                }
                val rootCause = generateSequence(e as Throwable) { it.cause }.last()
                Log.e("HAKOT_AUTH", "recoverPassword exception: host=$parsedHost, class=${e.javaClass.name}, msg=${e.message}, root=${rootCause.javaClass.name}: ${rootCause.message}")
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = -1,
                    data = null,
                    errorMessage = e.message ?: "Unknown password recovery exception"
                )
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun getCurrentUser(accessToken: String): SupabaseFetchResult =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                return@withContext SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = 401,
                    data = null,
                    errorMessage = "Missing authenticated user access token"
                )
            }
            var connection: HttpURLConnection? = null
            try {
                val urlString = "$SUPABASE_URL/auth/v1/user"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    SupabaseFetchResult(
                        isSuccess = true,
                        statusCode = responseCode,
                        data = JSONArray().apply { put(JSONObject(jsonText)) },
                        errorMessage = null
                    )
                } else {
                    val errorText = readErrorStream(connection)
                    SupabaseFetchResult(
                        isSuccess = false,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = "HTTP $responseCode: $errorText"
                    )
                }
            } catch (e: Exception) {
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = -1,
                    data = null,
                    errorMessage = e.message ?: "Unknown user verification exception"
                )
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun updateUserPassword(newPassword: String, accessToken: String): SupabaseFetchResult =
        withContext(Dispatchers.IO) {
            if (newPassword.isBlank()) {
                return@withContext SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = 400,
                    data = null,
                    errorMessage = "Password cannot be blank"
                )
            }
            if (accessToken.isBlank()) {
                return@withContext SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = 401,
                    data = null,
                    errorMessage = "Missing authenticated user access token"
                )
            }
            var connection: HttpURLConnection? = null
            try {
                val urlString = "$SUPABASE_URL/auth/v1/user"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val payload = JSONObject().apply {
                    put("password", newPassword)
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    SupabaseFetchResult(
                        isSuccess = true,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = null
                    )
                } else {
                    val errorText = readErrorStream(connection)
                    SupabaseFetchResult(
                        isSuccess = false,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = "HTTP $responseCode: $errorText"
                    )
                }
            } catch (e: Exception) {
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = -1,
                    data = null,
                    errorMessage = e.message ?: "Unknown password update exception"
                )
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun fetchProfileResult(userId: String, accessToken: String): FetchProfileResult =
        withContext(Dispatchers.IO) {
            if (userId.isBlank() || accessToken.isBlank() || !isValidUuid(userId)) {
                return@withContext FetchProfileResult(null, true, 400)
            }

            var connection: HttpURLConnection? = null
            try {
                val urlString = "$SUPABASE_URL/rest/v1/profiles?id=eq.$userId&select=*"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val code = connection.responseCode
                if (code in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(jsonText)
                    if (arr.length() == 1) {
                        val profileObj = arr.getJSONObject(0)
                        val profileId = profileObj.optString("id")
                        if (profileId == userId && isValidUuid(profileId)) {
                            return@withContext FetchProfileResult(profileObj, false, code)
                        }
                    }
                    return@withContext FetchProfileResult(null, true, code)
                } else {
                    val errText = readErrorStream(connection)
                    Log.e("HAKOT_AUTH", "fetchProfile failed with HTTP $code: $errText")
                    val isPermanent = code in 401..403
                    return@withContext FetchProfileResult(null, isPermanent, code)
                }
            } catch (e: Exception) {
                Log.e("HAKOT_AUTH", "fetchProfile exception: ${e.message}")
                return@withContext FetchProfileResult(null, false, -1)
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun fetchProfile(userId: String, accessToken: String): JSONObject? {
        return fetchProfileResult(userId, accessToken).profile
    }

    // ---------------------------------------------------------
    // CHECK SUPABASE CONNECTION
    // ---------------------------------------------------------

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        connectionState = "Synchronizing..."
        var connection: HttpURLConnection? = null

        try {
            val urlString = "$SUPABASE_URL/rest/v1/"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                connectTimeout = 4000
                readTimeout = 4000
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connectionState = "Connected (Supabase Cloud HTTP 200)"
                lastSyncTimestamp = "Just now (${currentTimeString()})"
                true
            } else {
                connectionState = "Disconnected (HTTP $responseCode)"
                false
            }

        } catch (_: Exception) {
            connectionState = "Offline / Connection Error"
            false
        } finally {
            connection?.disconnect()
        }
    }

    // ---------------------------------------------------------
    // EXPLICIT PUBLIC ANONYMOUS FETCH
    // ---------------------------------------------------------

    suspend fun fetchPublicTableRecords(tableName: String): SupabaseFetchResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                val urlString = "$SUPABASE_URL/rest/v1/$tableName?select=*"
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    SupabaseFetchResult(
                        isSuccess = true,
                        statusCode = responseCode,
                        data = JSONArray(jsonText),
                        errorMessage = null
                    )
                } else {
                    val errorText = readErrorStream(connection)
                    SupabaseFetchResult(
                        isSuccess = false,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = "HTTP $responseCode: $errorText"
                    )
                }

            } catch (e: Exception) {
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = -1,
                    data = null,
                    errorMessage = e.message ?: "Unknown fetch exception"
                )
            } finally {
                connection?.disconnect()
            }
        }

    // ---------------------------------------------------------
    // EXPLICIT PUBLIC ANONYMOUS INSERT (For Anonymous Reports)
    // ---------------------------------------------------------

    suspend fun insertPublicTableRecord(
        tableName: String,
        jsonPayload: JSONObject
    ): SupabaseInsertResult = withContext(Dispatchers.IO) {
        Log.d("HAKOT_SUPABASE_INSERT", "insertPublicTableRecord started for table: $tableName")
        var connection: HttpURLConnection? = null

        try {
            val urlString = "$SUPABASE_URL/rest/v1/$tableName"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                // Send publishable key via apikey header ONLY; do not send as Authorization: Bearer for anonymous requests
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }

            Log.d("HAKOT_SUPABASE_INSERT", "Executing HTTP POST request to $urlString")
            connection.outputStream.use { output ->
                output.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            Log.d("HAKOT_SUPABASE_INSERT", "Received HTTP response for $tableName: status code $responseCode")
            if (responseCode in 200..299) {
                SupabaseInsertResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    errorMessage = null
                )
            } else {
                val errorText = readErrorStream(connection)
                SupabaseInsertResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    errorMessage = "HTTP $responseCode: $errorText"
                )
            }

        } catch (e: Exception) {
            Log.e("HAKOT_SUPABASE_INSERT", "Exception during insertPublicTableRecord for $tableName: ${e.javaClass.simpleName} - ${e.message}")
            SupabaseInsertResult(
                isSuccess = false,
                statusCode = -1,
                errorMessage = e.message ?: "Unknown insert exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    // ---------------------------------------------------------
    // EXPLICIT AUTHENTICATED FETCH (Requires user JWT)
    // ---------------------------------------------------------

    suspend fun fetchAuthenticatedTableRecords(tableName: String, accessToken: String): SupabaseFetchResult =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                return@withContext SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = 401,
                    data = null,
                    errorMessage = "Missing authenticated user access token"
                )
            }
            var connection: HttpURLConnection? = null

            try {
                val hasSelect = tableName.contains("select=", ignoreCase = true)
                val urlString = if (hasSelect) {
                    "$SUPABASE_URL/rest/v1/$tableName"
                } else {
                    val separator = if (tableName.contains("?")) "&" else "?"
                    "$SUPABASE_URL/rest/v1/$tableName${separator}select=*"
                }
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    SupabaseFetchResult(
                        isSuccess = true,
                        statusCode = responseCode,
                        data = JSONArray(jsonText),
                        errorMessage = null
                    )
                } else {
                    val errorText = readErrorStream(connection)
                    Log.e("HAKOT_SUPABASE", "GET /rest/v1/$tableName failed with HTTP $responseCode: $errorText")
                    SupabaseFetchResult(
                        isSuccess = false,
                        statusCode = responseCode,
                        data = null,
                        errorMessage = "HTTP $responseCode: $errorText"
                    )
                }

            } catch (e: Exception) {
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = -1,
                    data = null,
                    errorMessage = e.message ?: "Unknown fetch exception"
                )
            } finally {
                connection?.disconnect()
            }
        }

    // ---------------------------------------------------------
    // EXPLICIT AUTHENTICATED INSERT (Requires user JWT)
    // ---------------------------------------------------------

    suspend fun insertAuthenticatedTableRecord(
        tableName: String,
        jsonPayload: JSONObject,
        accessToken: String
    ): SupabaseInsertResult = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext SupabaseInsertResult(
                isSuccess = false,
                statusCode = 401,
                errorMessage = "Missing authenticated user access token"
            )
        }
        var connection: HttpURLConnection? = null

        try {
            val urlString = "$SUPABASE_URL/rest/v1/$tableName"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }

            connection.outputStream.use { output ->
                output.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                SupabaseInsertResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    errorMessage = null
                )
            } else {
                val errorText = readErrorStream(connection)
                SupabaseInsertResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    errorMessage = "HTTP $responseCode: $errorText"
                )
            }

        } catch (e: Exception) {
            SupabaseInsertResult(
                isSuccess = false,
                statusCode = -1,
                errorMessage = e.message ?: "Unknown insert exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    // ---------------------------------------------------------
    // EXPLICIT AUTHENTICATED UPDATE (Requires user JWT & PATCH)
    // ---------------------------------------------------------

    suspend fun updateAuthenticatedTableRecord(
        tableName: String,
        queryFilter: String,
        jsonPayload: JSONObject,
        accessToken: String
    ): SupabaseInsertResult = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext SupabaseInsertResult(
                isSuccess = false,
                statusCode = 401,
                errorMessage = "Missing authenticated user access token"
            )
        }
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/rest/v1/$tableName?$queryFilter"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=representation")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }
            connection.outputStream.use { output ->
                output.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = try {
                    connection.inputStream?.bufferedReader()?.use { it.readText() } ?: "[]"
                } catch (_: Exception) {
                    "[]"
                }
                val jsonArray = try { JSONArray(responseText) } catch (_: Exception) { JSONArray() }
                if (jsonArray.length() == 1) {
                    SupabaseInsertResult(true, responseCode, null)
                } else {
                    SupabaseInsertResult(false, responseCode, "Update failed: Server updated ${jsonArray.length()} rows instead of exactly 1.")
                }
            } else {
                val errorText = readErrorStream(connection)
                SupabaseInsertResult(false, responseCode, "HTTP $responseCode: $errorText")
            }
        } catch (e: Exception) {
            SupabaseInsertResult(false, -1, e.message ?: "Unknown update exception")
        } finally {
            connection?.disconnect()
        }
    }

    // ---------------------------------------------------------
    // EXPLICIT EDGE FUNCTION INVOCATION (Requires user JWT)
    // ---------------------------------------------------------

    suspend fun invokeManagementEdgeFunction(
        action: String,
        applicationId: String?,
        accessToken: String
    ): SupabaseFetchResult = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext SupabaseFetchResult(
                isSuccess = false,
                statusCode = 401,
                data = null,
                errorMessage = "Missing authenticated user access token"
            )
        }
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/functions/v1/manage-account-applications"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("action", action)
                if (!applicationId.isNullOrBlank()) {
                    put("applicationId", applicationId)
                }
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val rootObj = JSONObject(jsonText)
                val dataArray = rootObj.optJSONArray("data")
                val finalData = if (dataArray != null && dataArray.length() > 0) {
                    dataArray
                } else {
                    JSONArray().apply { put(rootObj) }
                }
                SupabaseFetchResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    data = finalData,
                    errorMessage = null
                )
            } else {
                val errorText = readErrorStream(connection)
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = "HTTP $responseCode: $errorText"
                )
            }
        } catch (e: Exception) {
            SupabaseFetchResult(
                isSuccess = false,
                statusCode = -1,
                data = null,
                errorMessage = e.message ?: "Unknown Edge Function exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun submitOrganizationApplicationEdgeFunction(
        payload: JSONObject
    ): SupabaseFetchResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/functions/v1/submit-organization-application"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                readErrorStream(connection)
            }

            if (responseCode in 200..299) {
                val rootObj = JSONObject(responseText)
                SupabaseFetchResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    data = JSONArray().apply { put(rootObj) },
                    errorMessage = null
                )
            } else {
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = "HTTP $responseCode: $responseText"
                )
            }
        } catch (e: Exception) {
            SupabaseFetchResult(
                isSuccess = false,
                statusCode = -1,
                data = null,
                errorMessage = e.message ?: "Unknown Edge Function exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun resetAccountPasswordEdgeFunction(
        email: String,
        code: String,
        newPassword: String
    ): SupabaseFetchResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/functions/v1/reset-account-password"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("email", email.trim())
                put("code", code.trim())
                put("newPassword", newPassword)
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                readErrorStream(connection)
            }

            if (responseCode in 200..299) {
                val rootObj = JSONObject(responseText)
                SupabaseFetchResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    data = JSONArray().apply { put(rootObj) },
                    errorMessage = null
                )
            } else {
                val errorMsg = try {
                    JSONObject(responseText).optString("error", responseText)
                } catch (_: Exception) {
                    responseText
                }
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = errorMsg.ifBlank { "HTTP $responseCode" }
                )
            }
        } catch (e: Exception) {
            val isTimeout = e is SocketTimeoutException || e is InterruptedIOException
            val statusCode = if (isTimeout) -2 else -1
            SupabaseFetchResult(
                isSuccess = false,
                statusCode = statusCode,
                data = null,
                errorMessage = e.message ?: "Unknown password reset network exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun activateAccountEdgeFunction(
        email: String,
        code: String,
        newPassword: String
    ): SupabaseFetchResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/functions/v1/activate-account"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("email", email.trim())
                put("code", code.trim())
                put("newPassword", newPassword)
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                readErrorStream(connection)
            }

            if (responseCode in 200..299) {
                val rootObj = JSONObject(responseText)
                SupabaseFetchResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    data = JSONArray().apply { put(rootObj) },
                    errorMessage = null
                )
            } else {
                val errorMsg = try {
                    JSONObject(responseText).optString("error", responseText)
                } catch (_: Exception) {
                    responseText
                }
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = errorMsg.ifBlank { "HTTP $responseCode" }
                )
            }
        } catch (e: Exception) {
            val isTimeout = e is SocketTimeoutException || e is InterruptedIOException
            val statusCode = if (isTimeout) -2 else -1
            SupabaseFetchResult(
                isSuccess = false,
                statusCode = statusCode,
                data = null,
                errorMessage = e.message ?: "Unknown account activation network exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun reconcileUncertainPasswordReset(
        otpId: String,
        action: String,
        accessToken: String
    ): SupabaseFetchResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/rest/v1/rpc/reconcile_uncertain_password_reset"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("p_otp_id", otpId)
                put("p_action", action)
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                readErrorStream(connection)
            }

            if (responseCode in 200..299) {
                SupabaseFetchResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    data = JSONArray().apply { put(JSONObject().put("success", true)) },
                    errorMessage = null
                )
            } else {
                val errorMsg = try {
                    JSONObject(responseText).optString("message", responseText)
                } catch (_: Exception) {
                    responseText
                }
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = errorMsg.ifBlank { "HTTP $responseCode" }
                )
            }
        } catch (e: Exception) {
            SupabaseFetchResult(
                isSuccess = false,
                statusCode = -1,
                data = null,
                errorMessage = e.message ?: "Unknown reconciliation exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchRouteGeometryGeoJson(
        routeVersionId: String,
        accessToken: String
    ): SupabaseFetchResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlString = "$SUPABASE_URL/rest/v1/rpc/get_route_geometry_geojson"
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("p_route_version_id", routeVersionId)
            }

            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                readErrorStream(connection)
            }

            if (responseCode in 200..299) {
                SupabaseFetchResult(
                    isSuccess = true,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = responseText
                )
            } else {
                val errorMsg = try {
                    JSONObject(responseText).optString("message", responseText)
                } catch (_: Exception) {
                    responseText
                }
                SupabaseFetchResult(
                    isSuccess = false,
                    statusCode = responseCode,
                    data = null,
                    errorMessage = errorMsg.ifBlank { "HTTP $responseCode" }
                )
            }
        } catch (e: Exception) {
            SupabaseFetchResult(
                isSuccess = false,
                statusCode = -1,
                data = null,
                errorMessage = e.message ?: "Unknown route geometry exception"
            )
        } finally {
            connection?.disconnect()
        }
    }

    // ---------------------------------------------------------
    // FETCH NASUGBU GIS GEOJSON (Public RPC)
    // ---------------------------------------------------------

    suspend fun fetchNasugbuMapGeoJson(): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                gisLoadError = ""
                val urlString = "$SUPABASE_URL/rest/v1/rpc/get_nasugbu_map_geojson"

                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 60000
                }

                connection.outputStream.use { output ->
                    output.write("{}".toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val errorText = readErrorStream(connection)
                    gisLoadError = "HTTP $responseCode: $errorText"
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val barangays = json.getJSONObject("barangays").toString()
                val roads = json.getJSONObject("roads").toString()

                lastSyncTimestamp = "GIS Synced (${currentTimeString()})"
                Pair(barangays, roads)

            } catch (_: Exception) {
                gisLoadError = "Unknown GIS loading error"
                null
            } finally {
                connection?.disconnect()
            }
        }
}
