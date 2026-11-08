package com.example.hakot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

object OsrmRoutingHelper {

    suspend fun fetchRoadRoute(
        waypoints: List<Pair<Double, Double>>
    ): List<GeoPoint> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext emptyList()
        try {
            // OSRM expects coordinates in {lng},{lat} format separated by semicolons
            val coordsString = waypoints.joinToString(";") { "${it.second},${it.first}" }
            val urlString = "https://router.project-osrm.org/route/v1/driving/$coordsString?overview=full&geometries=geojson"

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "HakotApp/3.0 (Android; Nasugbu Waste System; contact: admin@hakot.app)")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonString)
                val routesArray = jsonObj.getJSONArray("routes")
                if (routesArray.length() > 0) {
                    val routeObj = routesArray.getJSONObject(0)
                    val geometryObj = routeObj.getJSONObject("geometry")
                    val coordsArray = geometryObj.getJSONArray("coordinates")

                    val resultPoints = mutableListOf<GeoPoint>()
                    for (i in 0 until coordsArray.length()) {
                        val point = coordsArray.getJSONArray(i)
                        val lng = point.getDouble(0)
                        val lat = point.getDouble(1)
                        resultPoints.add(GeoPoint(lat, lng))
                    }
                    if (resultPoints.isNotEmpty()) {
                        return@withContext resultPoints
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Fallback to direct waypoints if network or OSRM error occurs
        return@withContext waypoints.map { GeoPoint(it.first, it.second) }
    }
}
