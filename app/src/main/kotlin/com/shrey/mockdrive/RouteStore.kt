package com.shrey.mockdrive

import android.content.Context
import com.shrey.mockdrive.geo.LatLng
import org.json.JSONArray
import org.json.JSONObject

/** The last planned route survives app restarts, so a test can be re-run in one tap. */
data class SavedPlan(
    val stops: List<LatLng>,
    val route: List<LatLng>,
    val followRoads: Boolean,
    val speedMps: Float,
    val metric: Boolean,
    val timeWarp: Int,
)

class RouteStore(context: Context) {
    private val prefs = context.getSharedPreferences("plan", Context.MODE_PRIVATE)

    fun load(): SavedPlan? = runCatching {
        val o = JSONObject(prefs.getString(KEY, null) ?: return null)
        SavedPlan(
            stops = decode(o.getJSONArray("stops")),
            route = decode(o.getJSONArray("route")),
            followRoads = o.getBoolean("followRoads"),
            speedMps = o.getDouble("speedMps").toFloat(),
            metric = o.getBoolean("metric"),
            timeWarp = o.optInt("timeWarp", 1),
        )
    }.getOrNull()

    fun save(p: SavedPlan) {
        val o = JSONObject()
            .put("stops", encode(p.stops))
            .put("route", encode(p.route))
            .put("followRoads", p.followRoads)
            .put("speedMps", p.speedMps.toDouble())
            .put("metric", p.metric)
            .put("timeWarp", p.timeWarp)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private fun encode(list: List<LatLng>) = JSONArray().apply {
        list.forEach { put(JSONArray().put(it.lat).put(it.lng)) }
    }

    private fun decode(a: JSONArray) = List(a.length()) { i ->
        a.getJSONArray(i).let { LatLng(it.getDouble(0), it.getDouble(1)) }
    }

    private companion object {
        const val KEY = "last"
    }
}
