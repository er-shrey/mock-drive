package com.shrey.mockdrive

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationServices

/**
 * Feeds one fake fix to every place an app might read location from:
 *  - LocationManager GPS, network and (API 31+) fused test providers;
 *  - Google Play services' FusedLocationProviderClient, via mock mode.
 * Needs this app chosen under Developer options > Select mock location app.
 */
class MockLocationPusher(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val providers = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }
    private val active = mutableListOf<String>()
    private var fusedMock = false

    /** Throws SecurityException if this app is not the selected mock location app. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start() {
        for (name in providers) {
            runCatching { lm.removeTestProvider(name) }
            try {
                lm.addTestProvider(
                    name, false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE,
                )
                lm.setTestProviderEnabled(name, true)
                active += name
            } catch (e: SecurityException) {
                stop()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Test provider $name unavailable", e)
            }
        }
        try {
            fused.setMockMode(true)
                .addOnSuccessListener { fusedMock = true }
                .addOnFailureListener { Log.w(TAG, "Fused mock mode failed", it) }
        } catch (e: Exception) {
            Log.w(TAG, "Play services fused provider unavailable", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun push(lat: Double, lng: Double, speedMps: Float, bearing: Float) {
        for (name in active) {
            runCatching { lm.setTestProviderLocation(name, build(name, lat, lng, speedMps, bearing)) }
                .onFailure { Log.w(TAG, "push $name failed", it) }
        }
        if (fusedMock) {
            runCatching { fused.setMockLocation(build("fused", lat, lng, speedMps, bearing)) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        for (name in active) runCatching { lm.removeTestProvider(name) }
        active.clear()
        if (fusedMock) runCatching { fused.setMockMode(false) }
        fusedMock = false
    }

    private fun build(provider: String, lat: Double, lng: Double, speedMps: Float, bearing: Float) =
        Location(provider).apply {
            latitude = lat
            longitude = lng
            altitude = 180.0
            accuracy = 4f
            speed = speedMps
            this.bearing = bearing
            verticalAccuracyMeters = 6f
            speedAccuracyMetersPerSecond = 0.5f
            bearingAccuracyDegrees = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            extras = Bundle().apply { putInt("satellites", 12) }
        }

    companion object {
        private const val TAG = "MockDrive"

        fun isSelectedMockApp(context: Context): Boolean = try {
            context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}
