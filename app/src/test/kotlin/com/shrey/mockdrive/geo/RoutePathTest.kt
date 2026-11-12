package com.shrey.mockdrive.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutePathTest {
    // Roughly 1.11 km per 0.01 degree of latitude.
    private val south = LatLng(40.00, -88.0)
    private val north = LatLng(40.01, -88.0)
    private val northEast = LatLng(40.01, -87.99)

    @Test fun singlePointHoldsPosition() {
        val p = RoutePath(listOf(south))
        assertThat(p.totalM).isEqualTo(0.0)
        assertThat(p.at(500.0)).isEqualTo(Fix(40.0, -88.0, 0f))
    }

    @Test fun totalIsSumOfSegments() {
        val p = RoutePath(listOf(south, north, northEast))
        val expected = Geo.distanceM(south, north) + Geo.distanceM(north, northEast)
        assertThat(p.totalM).isWithin(0.001).of(expected)
        assertThat(Geo.distanceM(south, north)).isWithin(5.0).of(1112.0)
    }

    @Test fun halfwayIsInterpolatedAndHeadsNorth() {
        val p = RoutePath(listOf(south, north))
        val fix = p.at(p.totalM / 2)
        assertThat(fix.lat).isWithin(1e-6).of(40.005)
        assertThat(fix.bearing).isWithin(0.5f).of(0f)
    }

    @Test fun secondLegHeadsEastAndEndClamps() {
        val p = RoutePath(listOf(south, north, northEast))
        val onLeg2 = p.at(Geo.distanceM(south, north) + 10)
        assertThat(onLeg2.bearing).isWithin(0.5f).of(90f)
        val end = p.at(p.totalM * 2)
        assertThat(end.lat).isEqualTo(northEast.lat)
        assertThat(end.lng).isEqualTo(northEast.lng)
    }

    @Test fun duplicatePointsAreDropped() {
        val p = RoutePath(listOf(south, south, north, north))
        assertThat(p.points).hasSize(2)
    }
}
