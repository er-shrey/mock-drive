package com.shrey.mockdrive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrey.mockdrive.geo.LatLng
import com.shrey.mockdrive.geo.RoutePath
import com.shrey.mockdrive.net.Place
import com.shrey.mockdrive.net.RouteApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanUi(
    val stops: List<LatLng> = emptyList(),
    val route: List<LatLng> = emptyList(),
    val routeM: Double = 0.0,
    val followRoads: Boolean = true,
    val routing: Boolean = false,
    val routeError: String? = null,
    val metric: Boolean = false,
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val mockAllowed: Boolean = true,
    val showMockHelp: Boolean = false,
    /** Bumped to ask the map to fit the whole route on screen. */
    val fitKey: Int = 0,
    val focus: LatLng? = null,
    val focusKey: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = RouteStore(app)
    val ui = MutableStateFlow(PlanUi())
    private var routeJob: Job? = null
    private var searchJob: Job? = null

    init {
        store.load()?.let { s ->
            DriveState.speedMps.value = s.speedMps
            DriveState.timeWarp.value = s.timeWarp
            ui.value = PlanUi(
                stops = s.stops, route = s.route, routeM = lengthOf(s.route),
                followRoads = s.followRoads, metric = s.metric, fitKey = 1,
            )
        }
    }

    fun addStop(p: LatLng) {
        if (DriveState.status.value != null) return
        ui.update { it.copy(stops = it.stops + p, results = emptyList()) }
        reroute()
    }

    fun undoStop() {
        ui.update { it.copy(stops = it.stops.dropLast(1)) }
        reroute()
    }

    fun clear() {
        routeJob?.cancel()
        ui.update { it.copy(stops = emptyList(), route = emptyList(), routeM = 0.0, routeError = null, routing = false) }
        persist()
    }

    fun setFollowRoads(on: Boolean) {
        ui.update { it.copy(followRoads = on) }
        reroute()
    }

    fun setMetric(on: Boolean) {
        ui.update { it.copy(metric = on) }
        persist()
    }

    fun setSpeed(mps: Float) {
        DriveState.speedMps.value = mps
        persist()
    }

    fun setTimeWarp(x: Int) {
        DriveState.timeWarp.value = x
        persist()
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            ui.update { it.copy(results = emptyList(), searching = false, searchError = null) }
            return
        }
        searchJob = viewModelScope.launch {
            ui.update { it.copy(searching = true, searchError = null) }
            try {
                val found = RouteApi.search(query)
                ui.update { it.copy(results = found, searching = false, searchError = if (found.isEmpty()) "No places found" else null) }
            } catch (e: Exception) {
                ui.update { it.copy(searching = false, searchError = "Search failed: ${e.message}") }
            }
        }
    }

    fun pickPlace(p: Place) {
        ui.update { it.copy(focus = p.point, focusKey = it.focusKey + 1) }
        addStop(p.point)
    }

    fun dismissResults() = ui.update { it.copy(results = emptyList(), searchError = null) }

    fun refreshMockAllowed() {
        val allowed = MockLocationPusher.isSelectedMockApp(getApplication())
        ui.update { it.copy(mockAllowed = allowed, showMockHelp = it.showMockHelp && !allowed) }
    }

    fun setMockHelp(show: Boolean) = ui.update { it.copy(showMockHelp = show) }

    /** The path to drive, or null while a road route is still loading or failed. */
    fun drivePath(): RoutePath? {
        val s = ui.value
        return when {
            s.stops.isEmpty() -> null
            s.stops.size == 1 -> RoutePath(s.stops)
            s.routing || s.route.size < 2 -> null
            else -> RoutePath(s.route)
        }
    }

    private fun reroute() {
        routeJob?.cancel()
        val stops = ui.value.stops
        if (stops.size < 2 || !ui.value.followRoads) {
            ui.update { it.copy(route = stops, routeM = lengthOf(stops), routing = false, routeError = null) }
            persist()
            return
        }
        routeJob = viewModelScope.launch {
            delay(250) // coalesce quick taps / undo presses
            ui.update { it.copy(routing = true, routeError = null) }
            try {
                val route = RouteApi.drivingRoute(stops)
                ui.update { it.copy(route = route, routeM = lengthOf(route), routing = false, fitKey = it.fitKey + 1) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.update {
                    it.copy(route = emptyList(), routeM = 0.0, routing = false,
                        routeError = "Road route failed: ${e.message}. Turn off \"Follow roads\" to drive straight lines.")
                }
            }
            persist()
        }
    }

    private fun persist() {
        val s = ui.value
        store.save(SavedPlan(s.stops, s.route, s.followRoads, DriveState.speedMps.value, s.metric, DriveState.timeWarp.value))
    }

    private fun lengthOf(points: List<LatLng>) = if (points.isEmpty()) 0.0 else RoutePath(points).totalM
}
