package dev.groig.routing

import java.util.Locale

data class TrackPoint(val pos: LatLon, val ele: Double?)

private val TRKPT = Regex("""<trkpt\b([^>]*?)(?:/>|>(.*?)</trkpt>)""", RegexOption.DOT_MATCHES_ALL)
private val LAT = Regex("""\blat\s*=\s*"(-?[\d.]+)"""")
private val LON = Regex("""\blon\s*=\s*"(-?[\d.]+)"""")
private val ELE = Regex("""<ele>\s*(-?[\d.]+)\s*</ele>""")

fun parseTrack(gpx: String): List<TrackPoint> = TRKPT.findAll(gpx).mapNotNull { m ->
    val attrs = m.groupValues[1]
    val lat = LAT.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
    val lon = LON.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
    val ele = ELE.find(m.groupValues[2])?.groupValues?.get(1)?.toDoubleOrNull()
    if (lat != null && lon != null) TrackPoint(LatLon(lat, lon), ele) else null
}.toList()

fun trackLength(points: List<TrackPoint>): Double = points.zipWithNext { a, b -> haversine(a.pos, b.pos) }.sum()

/** Total climb, ignoring wiggles smaller than [threshold] metres. */
fun trackAscend(points: List<TrackPoint>, threshold: Double = 5.0): Int? {
    val eles = points.mapNotNull { it.ele }
    if (eles.size < 2) return null
    var climb = 0.0
    var low = eles.first()
    for (e in eles) {
        if (e < low) {
            low = e
        } else if (e - low >= threshold) {
            climb += e - low
            low = e
        }
    }
    return climb.toInt()
}

fun writeGpx(points: List<TrackPoint>, name: String): String {
    val safe = name.replace("&", "&amp;").replace("<", "&lt;")
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Ellipse Ride\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append(" <trk>\n  <name>").append(safe).append("</name>\n  <trkseg>\n")
        for (p in points) {
            append("   <trkpt lat=\"").append(String.format(Locale.US, "%.6f", p.pos.lat))
            append("\" lon=\"").append(String.format(Locale.US, "%.6f", p.pos.lon)).append('"')
            if (p.ele != null) {
                append("><ele>").append(String.format(Locale.US, "%.1f", p.ele)).append("</ele></trkpt>\n")
            } else {
                append("/>\n")
            }
        }
        append("  </trkseg>\n </trk>\n</gpx>\n")
    }
}
