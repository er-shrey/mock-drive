package com.shrey.mockdrive.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shrey.mockdrive.DriveStatus
import com.shrey.mockdrive.R
import com.shrey.mockdrive.geo.LatLng
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private fun LatLng.gp() = GeoPoint(lat, lng)

@Composable
fun OsmMap(
    stops: List<LatLng>,
    route: List<LatLng>,
    car: DriveStatus?,
    followCar: Boolean,
    fitKey: Int,
    focus: LatLng?,
    focusKey: Int,
    onTap: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tap by rememberUpdatedState(onTap)
    val density = context.resources.displayMetrics.density

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(39.5, -98.35)) // continental US
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    tap(LatLng(p.latitude, p.longitude)); return true
                }
                override fun longPressHelper(p: GeoPoint) = false
            }))
        }
    }
    val routeLine = remember {
        Polyline(map).apply {
            outlinePaint.color = Color.rgb(30, 91, 216)
            outlinePaint.strokeWidth = 5 * density
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            setOnClickListener { _, _, _ -> false }
        }
    }
    val carMarker = remember {
        Marker(map).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_car)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> true }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    LaunchedEffect(stops, route) {
        val events = map.overlays.first()
        map.overlays.clear()
        map.overlays.add(events)
        if (route.size >= 2) {
            routeLine.setPoints(route.map { it.gp() })
            map.overlays.add(routeLine)
        }
        stops.forEachIndexed { i, p ->
            val color = when {
                i == 0 -> Color.rgb(46, 160, 67)
                i == stops.lastIndex -> Color.rgb(214, 48, 49)
                else -> Color.rgb(242, 153, 0)
            }
            map.overlays.add(Marker(map).apply {
                position = p.gp()
                icon = dot(color, (if (i == 0 || i == stops.lastIndex) 18 else 14) * density)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        if (car != null) map.overlays.add(carMarker)
        map.invalidate()
    }

    LaunchedEffect(car) {
        if (car == null) {
            map.overlays.remove(carMarker)
        } else {
            carMarker.position = GeoPoint(car.lat, car.lng)
            carMarker.rotation = -car.bearing // osmdroid rotates counter-clockwise
            if (carMarker !in map.overlays) map.overlays.add(carMarker)
            if (followCar) map.controller.animateTo(carMarker.position)
        }
        map.invalidate()
    }

    LaunchedEffect(fitKey) {
        if (fitKey > 0 && route.size >= 2) {
            map.post {
                // The search bar covers the top ~20% of the map and the panel the bottom ~40%,
                // so grow the box until the route lands in the visible strip between them.
                val b = BoundingBox.fromGeoPoints(route.map { it.gp() })
                val span = maxOf(b.latNorth - b.latSouth, 0.002)
                val padded = BoundingBox(
                    minOf(b.latNorth + span * 0.55, 85.0), b.lonEast,
                    maxOf(b.latSouth - span * 1.0, -85.0), b.lonWest,
                )
                map.zoomToBoundingBox(padded, false, (32 * density).toInt())
            }
        } else if (fitKey > 0 && stops.size == 1) {
            map.post { map.controller.setZoom(14.0); map.controller.setCenter(stops[0].gp()) }
        }
    }

    LaunchedEffect(focusKey) {
        if (focus != null && focusKey > 0) map.controller.animateTo(focus.gp(), 13.0, 700L)
    }

    AndroidView(factory = { map }, modifier = modifier)
}

private fun dot(color: Int, sizePx: Float) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke((sizePx / 6).toInt(), Color.WHITE)
    setSize(sizePx.toInt(), sizePx.toInt())
}
