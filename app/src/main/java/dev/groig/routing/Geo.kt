package dev.groig.routing

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

const val EARTH_RADIUS_M = 6_371_000.0

data class LatLon(val lat: Double, val lon: Double) {
    fun format(): String = "%.5f, %.5f".format(java.util.Locale.US, lat, lon)
}

fun haversine(a: LatLon, b: LatLon): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** A point in a flat, metre-based frame. */
data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec(x * k, y * k)
    val length: Double get() = hypot(x, y)
}

/**
 * Equirectangular projection around [origin]. Plenty accurate for the tens of
 * kilometres a bike ride spans.
 */
class LocalFrame(private val origin: LatLon) {
    private val cosLat = cos(Math.toRadians(origin.lat))
    private val mPerDeg = EARTH_RADIUS_M * PI / 180.0

    fun toVec(p: LatLon) = Vec((p.lon - origin.lon) * mPerDeg * cosLat, (p.lat - origin.lat) * mPerDeg)

    fun toLatLon(v: Vec) = LatLon(origin.lat + v.y / mPerDeg, origin.lon + v.x / (mPerDeg * cosLat))
}

private val NUMBER = Regex("""-?\d+(?:\.\d+)?""")

/**
 * Accepts "52.37, 4.89", "52.37 4.89", "geo:52.37,4.89?z=12" and
 * "geo:0,0?q=52.37,4.89(Label)".
 */
fun parseLatLon(text: String): LatLon? {
    val trimmed = text.trim()
    var body = trimmed
    if (trimmed.startsWith("geo:", ignoreCase = true)) {
        val rest = trimmed.substring(4)
        val coords = rest.substringBefore('?')
        val query = rest.substringAfter("q=", "")
        body = if (coords.startsWith("0,0") && query.isNotEmpty()) query else coords
    }
    val nums = NUMBER.findAll(body).map { it.value.toDouble() }.take(2).toList()
    if (nums.size < 2) return null
    val (lat, lon) = nums
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return LatLon(lat, lon)
}
