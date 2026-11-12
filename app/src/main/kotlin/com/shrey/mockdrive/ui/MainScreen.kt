package com.shrey.mockdrive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.mockdrive.DriveState
import com.shrey.mockdrive.DriveStatus
import com.shrey.mockdrive.MainViewModel
import com.shrey.mockdrive.PlanUi
import kotlin.math.roundToInt

private val WARPS = listOf(1, 2, 5, 10, 30)

@Composable
fun MainScreen(
    vm: MainViewModel,
    onStart: () -> Unit,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onOpenDevOptions: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val status by DriveState.status.collectAsStateWithLifecycle()
    val speed by DriveState.speedMps.collectAsStateWithLifecycle()
    val warp by DriveState.timeWarp.collectAsStateWithLifecycle()
    val error by DriveState.error.collectAsStateWithLifecycle()
    var followCar by rememberSaveable { mutableStateOf(true) }
    var localFit by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            stops = ui.stops, route = ui.route, car = status, followCar = followCar,
            fitKey = ui.fitKey + localFit, focus = ui.focus, focusKey = ui.focusKey,
            onTap = vm::addStop, modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth()) {
            if (status == null) SearchBox(ui, vm)
            if (!ui.mockAllowed) {
                Spacer(Modifier.height(8.dp))
                Banner("MockDrive is not the mock location app yet.", "Fix", onOpenDevOptions)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Banner(it, "OK") { DriveState.error.value = null }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Row(Modifier.align(Alignment.End).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.route.size >= 2) {
                    SmallFloatingActionButton(onClick = { followCar = false; localFit++ }) {
                        Icon(Icons.Default.FitScreen, "Show whole route")
                    }
                }
                if (status != null) {
                    SmallFloatingActionButton(
                        onClick = { followCar = !followCar },
                        containerColor = if (followCar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    ) {
                        Icon(Icons.Default.MyLocation, "Follow car",
                            tint = if (followCar) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge.copy(
                    bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                    bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                ),
                tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    if (status == null) PlanPanel(ui, vm, speed, warp, onStart)
                    else DrivePanel(status!!, ui.metric, speed, warp, vm, onPauseToggle, onStop)
                }
            }
        }
    }

    if (ui.showMockHelp) {
        AlertDialog(
            onDismissRequest = { vm.setMockHelp(false) },
            title = { Text("Allow MockDrive to fake location") },
            text = {
                Text(
                    "Android only accepts fake GPS from the app chosen in Developer options.\n\n" +
                        "1. Open Developer options (if missing: Settings › About phone › tap Build number 7 times).\n" +
                        "2. Tap \"Select mock location app\" and choose MockDrive.\n" +
                        "3. Come back and press Start again.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.setMockHelp(false); onOpenDevOptions() }) { Text("Open Developer options") } },
            dismissButton = { TextButton(onClick = { vm.setMockHelp(false) }) { Text("Later") } },
        )
    }
}

@Composable
private fun SearchBox(ui: PlanUi, vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Surface(shape = MaterialTheme.shapes.large, shadowElevation = 6.dp) {
        Column {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search a place to add as next stop") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    when {
                        ui.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        query.isNotEmpty() -> IconButton(onClick = { query = ""; vm.dismissResults() }) { Icon(Icons.Default.Clear, "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
            ui.searchError?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            if (ui.results.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(ui.results) { place ->
                        Text(
                            place.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable {
                                focus.clearFocus(); query = ""; vm.pickPlace(place)
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PlanPanel(ui: PlanUi, vm: MainViewModel, speed: Float, warp: Int, onStart: () -> Unit) {
    val stopsText = when (ui.stops.size) {
        0 -> "Tap the map to set the START point"
        1 -> "Start set. Tap again for the DESTINATION — or Start now to hold this spot"
        2 -> "Start → Destination"
        else -> "Start → ${ui.stops.size - 2} stop(s) → Destination"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stopsText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = vm::undoStop, enabled = ui.stops.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo last point") }
        IconButton(onClick = vm::clear, enabled = ui.stops.isNotEmpty()) { Icon(Icons.Default.Clear, "Clear route") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Follow roads", Modifier.weight(1f))
        Switch(checked = ui.followRoads, onCheckedChange = vm::setFollowRoads)
    }
    when {
        ui.routing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("  Finding road route…")
        }
        ui.routeError != null -> Text(ui.routeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        ui.stops.size >= 2 -> Text(
            "${distance(ui.routeM, ui.metric)} · about ${duration(ui.routeM / (speed * warp))} at ${speedText(speed, ui.metric)}" +
                if (warp > 1) " (${warp}× time warp)" else "",
            fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.height(8.dp))
    SpeedControls(speed, warp, ui.metric, vm)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onStart,
        enabled = ui.stops.size == 1 || (ui.stops.size >= 2 && !ui.routing && ui.route.size >= 2),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(Icons.Default.PlayArrow, null)
        Text(if (ui.stops.size == 1) "  Fake location here" else "  Start driving")
    }
}

@Composable
private fun DrivePanel(
    s: DriveStatus, metric: Boolean, speed: Float, warp: Int, vm: MainViewModel,
    onPauseToggle: () -> Unit, onStop: () -> Unit,
) {
    val title = when {
        s.totalM == 0.0 -> "Holding fake location"
        s.arrived -> "Arrived — holding at destination"
        s.paused -> "Paused — parked at current spot"
        else -> "Driving at ${speedText(s.speedMps, metric)}"
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (s.totalM > 0) {
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { (s.traveledM / s.totalM).toFloat() }, modifier = Modifier.fillMaxWidth())
        val left = s.totalM - s.traveledM
        Text(
            "${distance(s.traveledM, metric)} of ${distance(s.totalM, metric)}" +
                if (!s.arrived && speed > 0) " · ${duration(left / (speed * warp))} left" else "",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        "%.5f, %.5f · heading %d°".format(s.lat, s.lng, s.bearing.roundToInt()),
        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
    )
    if (s.totalM > 0) {
        Spacer(Modifier.height(8.dp))
        SpeedControls(speed, warp, metric, vm)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.totalM > 0 && !s.arrived) {
            OutlinedButton(onClick = onPauseToggle, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(if (s.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                Text(if (s.paused) "  Resume" else "  Pause")
            }
        }
        Button(
            onClick = onStop, modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Default.Stop, null)
            Text("  Stop")
        }
    }
}

@Composable
private fun SpeedControls(speedMps: Float, warp: Int, metric: Boolean, vm: MainViewModel) {
    val unit = if (metric) DriveState.KMH else DriveState.MPH
    val max = if (metric) 200f else 120f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Speed", Modifier.width(56.dp))
        Slider(
            value = (speedMps / unit).coerceIn(0f, max), onValueChange = { vm.setSpeed(it.roundToInt() * unit) },
            valueRange = 0f..max, modifier = Modifier.weight(1f),
        )
        Text(
            "${(speedMps / unit).roundToInt()} ${if (metric) "km/h" else "mph"}",
            Modifier.width(96.dp).clickable { vm.setMetric(!metric) }.padding(start = 8.dp),
            fontWeight = FontWeight.Medium,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Time warp", style = MaterialTheme.typography.bodySmall)
        WARPS.forEach { x ->
            FilterChip(selected = warp == x, onClick = { vm.setTimeWarp(x) }, label = { Text("$x×") })
        }
    }
}

private fun distance(m: Double, metric: Boolean) =
    if (metric) "%.1f km".format(m / 1000) else "%.1f mi".format(m / 1609.344)

private fun speedText(mps: Float, metric: Boolean) =
    if (metric) "${(mps / DriveState.KMH).roundToInt()} km/h" else "${(mps / DriveState.MPH).roundToInt()} mph"

private fun duration(seconds: Double): String {
    if (!seconds.isFinite()) return "—"
    val min = (seconds / 60).roundToInt()
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}
