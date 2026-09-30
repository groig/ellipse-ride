package dev.groig.routing

private val TRKPT = Regex("""<trkpt\b([^>]*)>""")
private val LAT = Regex("""\blat\s*=\s*"(-?[\d.]+)"""")
private val LON = Regex("""\blon\s*=\s*"(-?[\d.]+)"""")
private val TRACK_LENGTH = Regex("""track-length\s*=\s*(\d+)""")
private val ASCEND = Regex("""filtered ascend\s*=\s*(-?\d+)""")
private val TRACK_NAME = Regex("""(<trk>\s*<name>)[^<]*(</name>)""")

class ParsedGpx(val points: List<LatLon>, val lengthMeters: Double, val ascendMeters: Int?)

fun parseGpx(gpx: String): ParsedGpx {
    val points = TRKPT.findAll(gpx).mapNotNull { m ->
        val attrs = m.groupValues[1]
        val lat = LAT.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
        val lon = LON.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
        if (lat != null && lon != null) LatLon(lat, lon) else null
    }.toList()
    // BRouter puts "track-length = 12345 filtered ascend = 67 ..." in a header
    // comment; fall back to summing the points if it is missing.
    val length = TRACK_LENGTH.find(gpx)?.groupValues?.get(1)?.toDoubleOrNull()
        ?: points.zipWithNext { a, b -> haversine(a, b) }.sum()
    val ascend = ASCEND.find(gpx)?.groupValues?.get(1)?.toIntOrNull()
    return ParsedGpx(points, length, ascend)
}

fun renameTrack(gpx: String, name: String): String {
    val safe = name.replace("&", "&amp;").replace("<", "&lt;")
    return TRACK_NAME.replaceFirst(gpx, "$1${Regex.escapeReplacement(safe)}$2")
}
