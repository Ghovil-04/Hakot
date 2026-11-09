
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object SupabaseClient {

    const val SUPABASE_URL = "https://vyegsnkerrbdqacwegff.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_rd7efjfP3gHndcibiIVzHw_sshkqRxv"

    var connectionState: String by mutableStateOf("Checking...")
    var lastSyncTimestamp: String by mutableStateOf("Not Synced Yet")
    var gisLoadError: String by mutableStateOf("")

    // ---------------------------------------------------------
    // CHECK SUPABASE CONNECTION
    // ---------------------------------------------------------

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        connectionState = "Synchronizing..."

        try {
            val urlString = "$SUPABASE_URL/rest/v1/"

            val connection =
                (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    connectTimeout = 4000
                    readTimeout = 4000
                }

            if (connection.responseCode in 200..299) {
                connectionState = "Connected (Supabase Cloud HTTP 200)"

                lastSyncTimestamp =
                    "Just now (${LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))})"

                connection.disconnect()
                return@withContext true
            } else {
                connectionState = "Disconnected (HTTP ${connection.responseCode})"
                connection.disconnect()
                return@withContext false
            }

        } catch (e: Exception) {
            connectionState = "Offline / Connection Error"
            return@withContext false
        }
    }

    // ---------------------------------------------------------
    // TEST LIVE READ AND WRITE
    // ---------------------------------------------------------

    suspend fun testLiveReadWrite(): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {

            try {
                connectionState = "Synchronizing Read/Write..."

                val testId = "aud_test_${System.currentTimeMillis() % 10000}"

                // WRITE TEST
                val writeUrl = "$SUPABASE_URL/rest/v1/audit_logs"

                val writePayload = JSONObject().apply {
                    put("id", testId)
                    put("acting_user_email", "test.android@hakot.gov.ph")
                    put("action", "Live Android App Verification Write Test")
                    put("target_record", "Supabase Project")
                }

                val writeConn =
                    (URL(writeUrl).openConnection() as HttpURLConnection).apply {
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

                val writeSuccess = writeConn.responseCode in 200..299
                writeConn.disconnect()

                // READ TEST
                val readUrl = "$SUPABASE_URL/rest/v1/audit_logs?select=*"

                val readConn =
                    (URL(readUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("apikey", SUPABASE_ANON_KEY)
                        setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }

                if (readConn.responseCode in 200..299) {
                    val jsonText = readConn.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    val records = JSONArray(jsonText)

                    connectionState = "Connected (Live Read/Write Verified)"

                    lastSyncTimestamp =
                        "Just now (${LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))})"

                    readConn.disconnect()

                    return@withContext Pair(
                        true,
                        "Successfully read ${records.length()} records from Supabase Cloud DB!"
                    )

                } else {
                    val responseCode = readConn.responseCode
                    readConn.disconnect()

                    connectionState = "Read Error (HTTP $responseCode)"

                    return@withContext Pair(
                        writeSuccess,
                        "Write response HTTP ${if (writeSuccess) 200 else "failed"}, Read response HTTP $responseCode"
                    )
                }

            } catch (e: Exception) {
                connectionState = "Offline / Connection Error"
                return@withContext Pair(false, "Connection error: ${e.message}")
            }
        }

    // ---------------------------------------------------------
    // FETCH TABLE RECORDS
    // ---------------------------------------------------------

    suspend fun fetchTableRecords(tableName: String): JSONArray =
        withContext(Dispatchers.IO) {

            var connection: HttpURLConnection? = null

            try {
                val urlString = "$SUPABASE_URL/rest/v1/$tableName?select=*"

                connection =
                    (URL(urlString).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("apikey", SUPABASE_ANON_KEY)
                        setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }

                if (connection.responseCode in 200..299) {
                    val jsonText = connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    return@withContext JSONArray(jsonText)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection?.disconnect()
            }

            JSONArray()
        }

    // ---------------------------------------------------------
    // INSERT TABLE RECORD
    // ---------------------------------------------------------

    suspend fun insertTableRecord(
        tableName: String,
        jsonPayload: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {

        var connection: HttpURLConnection? = null

        try {
            val urlString = "$SUPABASE_URL/rest/v1/$tableName"

            connection =
                (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }

            connection.outputStream.use { output ->
                output.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            return@withContext connection.responseCode in 200..299

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false

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

                val urlString =
                    "$SUPABASE_URL/rest/v1/rpc/get_nasugbu_map_geojson"

                connection =
                    (URL(urlString).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"

                        setRequestProperty("apikey", SUPABASE_ANON_KEY)
                        setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")

                        doOutput = true
                        connectTimeout = 15000
                        readTimeout = 60000
                    }

                // RPC function has no parameters
                connection.outputStream.use { output ->
                    output.write("{}".toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    val errorText = connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: "No error details"

                    gisLoadError = "HTTP $responseCode: $errorText"

                    return@withContext null
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(response)

                val barangays = json
                    .getJSONObject("barangays")
                    .toString()

                val roads = json
                    .getJSONObject("roads")
                    .toString()

                lastSyncTimestamp =
                    "GIS Synced (${LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a"))})"

                Pair(barangays, roads)

            } catch (e: Exception) {
                gisLoadError = e.message ?: "Unknown GIS loading error"
                null

            } finally {
                connection?.disconnect()
            }
        }
}
