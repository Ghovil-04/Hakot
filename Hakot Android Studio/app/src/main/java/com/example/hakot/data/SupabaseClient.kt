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

    private fun currentTimeString(): String {
        return SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    private fun readErrorStream(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
        } catch (e: Exception) {
            e.message ?: "Failed to read error stream"
        }
    }

    // ---------------------------------------------------------
    // SUPABASE AUTHENTICATION
    // ---------------------------------------------------------

    suspend fun signIn(emailInput: String, passwordInput: String): AuthTokenResponse? =
        withContext(Dispatchers.IO) {
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
                    val json = JSONObject(jsonText)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600)
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id") ?: ""

                    if (accessToken.isNotBlank() && userId.isNotBlank()) {
                        return@withContext AuthTokenResponse(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000, userId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection?.disconnect()
            }
            null
        }

    suspend fun refreshSession(refreshTokenInput: String): AuthTokenResponse? =
        withContext(Dispatchers.IO) {
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
                    val json = JSONObject(jsonText)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600)
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id") ?: ""

                    if (accessToken.isNotBlank() && userId.isNotBlank()) {
                        return@withContext AuthTokenResponse(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000, userId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection?.disconnect()
            }
            null
        }

    suspend fun signOutRemote(accessToken: String): Boolean =
        withContext(Dispatchers.IO) {
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
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun fetchProfile(userId: String, accessToken: String): JSONObject? =
        withContext(Dispatchers.IO) {
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
                    if (arr.length() > 0) {
                        return@withContext arr.getJSONObject(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
    // TEST LIVE READ AND WRITE (STRICT READ-BACK VERIFICATION)
    // ---------------------------------------------------------

    suspend fun testLiveReadWrite(): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            var writeConn: HttpURLConnection? = null
            var readConn: HttpURLConnection? = null

            try {
                connectionState = "Synchronizing Read/Write..."
                val testId = "aud_test_${System.currentTimeMillis() % 10000}"

                // 1. WRITE TEST
                val writeUrl = "$SUPABASE_URL/rest/v1/audit_logs"
                val writePayload = JSONObject().apply {
                    put("id", testId)
                    put("acting_user_email", "test.android@hakot.gov.ph")
                    put("action", "Live Android App Verification Write Test")
                    put("target_record", "Supabase Project")
                }

                writeConn = (URL(writeUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                writeConn.outputStream.use { output ->
                    output.write(writePayload.toString().toByteArray(Charsets.UTF_8))
                }

                val writeCode = writeConn.responseCode
                if (writeCode !in 200..299) {
                    val errorText = readErrorStream(writeConn)
                    connectionState = "Write Error (HTTP $writeCode)"
                    return@withContext Pair(false, "Write failed with HTTP $writeCode: $errorText")
                }

                // 2. READ-BACK VERIFICATION QUERY (Specific lookup for testId)
                val readUrl = "$SUPABASE_URL/rest/v1/audit_logs?id=eq.$testId"
                readConn = (URL(readUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val readCode = readConn.responseCode
                if (readCode in 200..299) {
                    val jsonText = readConn.inputStream.bufferedReader().use { it.readText() }
                    val records = JSONArray(jsonText)

                    var found = false
                    for (i in 0 until records.length()) {
                        val rec = records.getJSONObject(i)
                        if (rec.optString("id") == testId) {
                            found = true
                            break
                        }
                    }

                    if (found) {
                        connectionState = "Connected (Live Read/Write Verified)"
                        lastSyncTimestamp = "Just now (${currentTimeString()})"
                        Pair(true, "Successfully wrote and verified record $testId via read-back query from Supabase Cloud DB!")
                    } else {
                        connectionState = "Read-Back Verification Failed"
                        Pair(false, "Write succeeded (HTTP $writeCode), but read-back query did not find test ID $testId")
                    }
                } else {
                    val errorText = readErrorStream(readConn)
                    connectionState = "Read Error (HTTP $readCode)"
                    Pair(false, "Write succeeded (HTTP $writeCode), but read-back query failed with HTTP $readCode: $errorText")
                }

            } catch (e: Exception) {
                connectionState = "Offline / Connection Error"
                Pair(false, "Connection error: ${e.message}")
            } finally {
                writeConn?.disconnect()
                readConn?.disconnect()
            }
        }

    // ---------------------------------------------------------
    // FETCH TABLE RECORDS
    // ---------------------------------------------------------

    suspend fun fetchTableRecords(tableName: String, accessToken: String? = null): SupabaseFetchResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            try {
                val urlString = "$SUPABASE_URL/rest/v1/$tableName?select=*"
                val tokenToUse = accessToken ?: SUPABASE_ANON_KEY

                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $tokenToUse")
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
    // INSERT TABLE RECORD
    // ---------------------------------------------------------

    suspend fun insertTableRecord(
        tableName: String,
        jsonPayload: JSONObject,
        accessToken: String? = null
    ): SupabaseInsertResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val urlString = "$SUPABASE_URL/rest/v1/$tableName"
            val tokenToUse = accessToken ?: SUPABASE_ANON_KEY

            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $tokenToUse")
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
    // FETCH NASUGBU GIS GEOJSON
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

            } catch (e: Exception) {
                gisLoadError = e.message ?: "Unknown GIS loading error"
                null
            } finally {
                connection?.disconnect()
            }
        }
}
