package com.shrey.mockdrive.net

import com.shrey.mockdrive.geo.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val name: String, val point: LatLng)

/**
 * Free OpenStreetMap services, no API key: OSRM for road routes, Nominatim for search.
 * Both public servers ask for light use and an identifying User-Agent.
 */
object RouteApi {
    private const val USER_AGENT = "MockDrive/0.1 (Android location test tool)"

    suspend fun drivingRoute(stops: List<LatLng>): List<LatLng> = withContext(Dispatchers.IO) {
        val coords = stops.joinToString(";") { "${it.lng},${it.lat}" }
        parseOsrm(get("https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson"))
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        parseNominatim(get("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=$q"))
    }

    fun parseOsrm(body: String): List<LatLng> {
        val json = JSONObject(body)
        val code = json.optString("code")
        if (code != "Ok") throw IOException(json.optString("message", "No road route ($code)"))
        val coords = json.getJSONArray("routes").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        return List(coords.length()) { i ->
            val c = coords.getJSONArray(i)
            LatLng(c.getDouble(1), c.getDouble(0))
        }
    }

    fun parseNominatim(body: String): List<Place> {
        val arr = JSONArray(body)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Place(o.getString("display_name"), LatLng(o.getString("lat").toDouble(), o.getString("lon").toDouble()))
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // OSRM returns 400 with a JSON reason (e.g. NoRoute); let the parser surface it.
            if (code !in 200..299 && !body.trimStart().startsWith("{")) throw IOException("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
