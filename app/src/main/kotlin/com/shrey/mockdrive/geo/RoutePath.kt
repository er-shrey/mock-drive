package com.shrey.mockdrive.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** One simulated GPS fix: position plus heading in degrees clockwise from north. */
data class Fix(val lat: Double, val lng: Double, val bearing: Float)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDeg(a: LatLng, b: LatLng): Float {
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}

/**
 * A polyline you can sample by distance travelled along it. Used both for road routes
 * from OSRM and straight-line routes between tapped points. A single point is valid:
 * it simply holds that position.
 */
class RoutePath(input: List<LatLng>) {
    val points: List<LatLng> = input.fold(mutableListOf()) { acc, p ->
        if (acc.isEmpty() || acc.last() != p) acc.add(p)
        acc
    }
    private val cumulative = DoubleArray(points.size)
    private val segBearing = FloatArray(maxOf(points.size - 1, 0))

    init {
        require(points.isNotEmpty()) { "A route needs at least one point" }
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + Geo.distanceM(points[i - 1], points[i])
            segBearing[i - 1] = Geo.bearingDeg(points[i - 1], points[i])
        }
    }

    val totalM: Double get() = cumulative.last()

    fun at(distanceM: Double): Fix {
        if (points.size == 1) return Fix(points[0].lat, points[0].lng, 0f)
        val d = distanceM.coerceIn(0.0, totalM)
        val found = cumulative.binarySearch(d)
        val i = (if (found >= 0) found else -found - 2).coerceIn(0, points.size - 2)
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen > 0) (d - cumulative[i]) / segLen else 0.0
        val a = points[i]
        val b = points[i + 1]
        return Fix(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t, segBearing[i])
    }
}
