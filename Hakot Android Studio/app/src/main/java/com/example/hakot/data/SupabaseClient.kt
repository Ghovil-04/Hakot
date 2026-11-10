package com.example.hakot.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val payload = JSONObject().apply {
                    put("email", emailInput)
                    put("password", passwordInput)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                if (code in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext parseAuthResponse(jsonText)
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
            null
        }

    suspend fun refreshSession(refreshTokenInput: String): AuthTokenResponse? =
        withContext(Dispatchers.IO) {
            if (refreshTokenInput.isBlank()) return@withContext null
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
                    return@withContext parseAuthResponse(jsonText)
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
            null
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

    suspend fun fetchProfile(userId: String, accessToken: String): JSONObject? =
        withContext(Dispatchers.IO) {
            if (userId.isBlank() || accessToken.isBlank()) return@withContext null
            if (!isValidUuid(userId)) return@withContext null

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
                    // Strict verification: exactly one profile row must match authenticated user ID
                    if (arr.length() == 1) {
                        val profileObj = arr.getJSONObject(0)
                        val profileId = profileObj.optString("id")
                        if (profileId == userId && isValidUuid(profileId)) {
                            return@withContext profileObj
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
            null
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
                val urlString = "$SUPABASE_URL/rest/v1/$tableName?select=*"
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
