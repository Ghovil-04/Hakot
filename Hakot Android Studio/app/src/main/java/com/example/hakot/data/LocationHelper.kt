package com.example.hakot.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    fun detectUserLocation(
        context: Context,
        onLocationDetected: (lat: Double, lng: Double, locationName: String, city: String, province: String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            // Default Nasugbu, Batangas fallback if permission not granted
            onLocationDetected(14.0700, 120.6300, "Nasugbu, Batangas", "Nasugbu", "Batangas")
            return
        }

        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            @Suppress("MissingPermission")
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        processLocation(context, location, onLocationDetected)
                    } else {
                        @Suppress("MissingPermission")
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                processLocation(context, lastLoc, onLocationDetected)
                            } else {
                                checkSystemLocationManager(context, onLocationDetected)
                            }
                        }.addOnFailureListener {
                            checkSystemLocationManager(context, onLocationDetected)
                        }
                    }
                }
                .addOnFailureListener {
                    checkSystemLocationManager(context, onLocationDetected)
                }
        } catch (e: Exception) {
            e.printStackTrace()
            checkSystemLocationManager(context, onLocationDetected)
        }
    }

    private fun checkSystemLocationManager(
        context: Context,
        onLocationDetected: (lat: Double, lng: Double, locationName: String, city: String, province: String) -> Unit
    ) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            @Suppress("MissingPermission")
            val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            @Suppress("MissingPermission")
            val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val loc = gpsLoc ?: netLoc
            if (loc != null) {
                processLocation(context, loc, onLocationDetected)
            } else {
                onLocationDetected(14.0700, 120.6300, "Nasugbu, Batangas", "Nasugbu", "Batangas")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onLocationDetected(14.0700, 120.6300, "Nasugbu, Batangas", "Nasugbu", "Batangas")
        }
    }

    private fun processLocation(
        context: Context,
        location: Location,
        onLocationDetected: (lat: Double, lng: Double, locationName: String, city: String, province: String) -> Unit
    ) {
        val lat = location.latitude
        val lng = location.longitude
        reverseGeocode(context, lat, lng, onLocationDetected)
    }

    private fun reverseGeocode(
        context: Context,
        lat: Double,
        lng: Double,
        onResult: (lat: Double, lng: Double, locationName: String, city: String, province: String) -> Unit
    ) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        val city = addr.locality ?: addr.subAdminArea ?: "Nasugbu"
                        val province = addr.adminArea ?: "Batangas"
                        val formattedName = "$city, $province"
                        onResult(lat, lng, formattedName, city, province)
                    } else {
                        onResult(lat, lng, "Nasugbu, Batangas", "Nasugbu", "Batangas")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: "Nasugbu"
                    val province = addr.adminArea ?: "Batangas"
                    val formattedName = "$city, $province"
                    onResult(lat, lng, formattedName, city, province)
                } else {
                    onResult(lat, lng, "Nasugbu, Batangas", "Nasugbu", "Batangas")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(lat, lng, "Nasugbu, Batangas", "Nasugbu", "Batangas")
        }
    }
}
