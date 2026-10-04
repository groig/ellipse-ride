package dev.groig.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQualityTest {
    private val frame = LocalFrame(LatLon(52.0, 5.0))

    /** A track through the given (x, y) metre corners, with a point every [step] metres. */
    private fun track(vararg corners: Pair<Double, Double>, step: Double = 10.0): List<TrackPoint> {
        val out = mutableListOf<TrackPoint>()
        for (i in 0 until corners.size - 1) {
            val a = Vec(corners[i].first, corners[i].second)
            val b = Vec(corners[i + 1].first, corners[i + 1].second)
            val n = maxOf(1, ((b - a).length / step).toInt())
            for (k in 0 until n) out += TrackPoint(frame.toLatLon(a + (b - a) * (k.toDouble() / n)), 10.0)
        }
        val last = corners.last()
        out += TrackPoint(frame.toLatLon(Vec(last.first, last.second)), 10.0)
        return out
    }

    @Test
    fun cutsADeadEndSpur() {
        // 1 km east, 400 m north up a dead end and back, then 1 km further east.
        val ride = track(0.0 to 0.0, 1000.0 to 0.0, 1000.0 to 400.0, 1000.0 to 0.0, 2000.0 to 0.0)
        val trimmed = trimSpurs(ride)
        assertEquals(2800.0, trackLength(ride), 5.0)
        assertEquals(2000.0, trackLength(trimmed), 25.0)
    }

    @Test
    fun keepsARealLoop() {
        // A square block ridden once is not a spur.
        val ride = track(0.0 to 0.0, 500.0 to 0.0, 500.0 to 500.0, 0.0 to 500.0, 0.0 to 0.0)
        assertEquals(trackLength(ride), trackLength(trimSpurs(ride)), 1.0)
        assertTrue(overlapRatio(ride) < 0.05)
    }

    @Test
    fun keepsAStraightRide() {
        val ride = track(0.0 to 0.0, 3000.0 to 0.0)
        assertEquals(ride.size, trimSpurs(ride).size)
        assertEquals(0.0, overlapRatio(ride), 0.01)
    }

    @Test
    fun countsRoadRiddenTwice() {
        // Out 2 km east, a 1 km loop, then the same 2 km back: about 40% of the ride repeats.
        val ride = track(
            0.0 to 0.0, 2000.0 to 0.0, 2000.0 to 250.0, 2250.0 to 250.0, 2250.0 to 0.0, 2000.0 to 0.0, 0.0 to 0.0,
        )
        val overlap = overlapRatio(ride)
        assertTrue("overlap was $overlap", overlap in 0.3..0.5)
    }

    @Test
    fun gpxRoundTrip() {
        val ride = track(0.0 to 0.0, 1000.0 to 0.0)
        val parsed = parseTrack(writeGpx(ride, "Test & <ride>"))
        assertEquals(ride.size, parsed.size)
        assertEquals(trackLength(ride), trackLength(parsed), 1.0)
        assertEquals(10.0, parsed.first().ele!!, 0.01)
    }

    @Test
    fun readsBRouterStyleTrackPoints() {
        val gpx = """
            <trkseg>
             <trkpt lon="5.000000" lat="52.000000"><ele>3.5</ele></trkpt>
             <trkpt lon="5.001000" lat="52.000000"><ele>9.0</ele></trkpt>
             <trkpt lon="5.002000" lat="52.000000"/>
            </trkseg>
        """.trimIndent()
        val points = parseTrack(gpx)
        assertEquals(3, points.size)
        assertEquals(5.001, points[1].pos.lon, 1e-9)
        assertNull(points[2].ele)
        assertEquals(5, trackAscend(points))
    }

    @Test
    fun parsesCoordinates() {
        assertEquals(LatLon(52.37, 4.89), parseLatLon("52.37, 4.89"))
        assertEquals(LatLon(52.37, 4.89), parseLatLon("geo:52.37,4.89?z=12"))
        assertEquals(LatLon(52.37, 4.89), parseLatLon("geo:0,0?q=52.37,4.89(Home)"))
        assertNull(parseLatLon("somewhere"))
        assertNotNull(parseLatLon("-33.9 18.4"))
    }
}
