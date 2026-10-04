package dev.groig.routing

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class Place(val name: String, val position: LatLon)

/** Named places, most recently used first. */
class PlaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    fun load(): List<Place> {
        val json = prefs.getString("list", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Place(o.getString("name"), LatLon(o.getDouble("lat"), o.getDouble("lon")))
            }
        }.getOrDefault(emptyList())
    }

    fun save(places: List<Place>) {
        val array = JSONArray()
        places.forEach {
            array.put(JSONObject().put("name", it.name).put("lat", it.position.lat).put("lon", it.position.lon))
        }
        prefs.edit { putString("list", array.toString()) }
    }
}

/** [place] moved (or added) to the front, replacing any place with the same name. */
fun List<Place>.bump(place: Place): List<Place> =
    listOf(place) + filterNot { it.name.equals(place.name, ignoreCase = true) }
