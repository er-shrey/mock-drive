package com.shrey.mockdrive

import com.shrey.mockdrive.geo.RoutePath
import kotlinx.coroutines.flow.MutableStateFlow

/** Live position of a running drive, published by [DriveService] for the UI. */
data class DriveStatus(
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val speedMps: Float,
    val traveledM: Double,
    val totalM: Double,
    val paused: Boolean,
    val arrived: Boolean,
)

/**
 * Process-wide drive state shared by the screen and the service. Speed, time warp and
 * pause are read by the service every tick, so changing them mid-drive takes effect at once.
 */
object DriveState {
    val path = MutableStateFlow<RoutePath?>(null)
    val speedMps = MutableStateFlow(65 * MPH)
    val timeWarp = MutableStateFlow(1)
    val paused = MutableStateFlow(false)
    val status = MutableStateFlow<DriveStatus?>(null)
    val error = MutableStateFlow<String?>(null)

    const val MPH = 0.44704f
    const val KMH = 1 / 3.6f
}
