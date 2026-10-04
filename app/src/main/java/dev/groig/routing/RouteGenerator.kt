package dev.groig.routing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Pass(
    val attempt: Int,
    val pass: Int,
    val lengthMeters: Double?,
    val error: String? = null,
    val overlap: Double? = null,
)

data class GeneratedRoute(
    val start: LatLon,
    val end: LatLon,
    val track: List<TrackPoint>,
    val vias: List<LatLon>,
    val lengthMeters: Double,
    val ascendMeters: Int?,
    val targetMeters: Double,
    /** Share of the ride on roads it already used, 0..1. */
    val overlap: Double,
    /** Metres of out-and-back detours cut from BRouter's track. */
    val trimmedMeters: Double,
    val passes: Int,
) {
    val deviation: Double get() = (lengthMeters - targetMeters) / targetMeters
    val withinTolerance: Boolean get() = abs(deviation) <= RouteGenerator.TOLERANCE

    /** Whether this route beats [other]: on target first, then fewer repeated roads. */
    fun betterThan(other: GeneratedRoute?): Boolean = when {
        other == null -> true
        withinTolerance != other.withinTolerance -> withinTolerance
        withinTolerance -> overlap < other.overlap
        else -> abs(deviation) < abs(other.deviation)
    }
}

/**
 * Via points laid out around A and B, controlled by a single size parameter.
 * The straight-line length of A -> vias -> B grows monotonically with size.
 */
private interface Shape {
    val minSize: Double
    fun vias(size: Double): List<Vec>
}

/**
 * An ellipse with A and B as its foci. [size] is the semi-major axis. Via points
 * sit at fixed parametric angles on one side, ordered from A towards B, so the
 * ride sweeps out along one half of the ellipse.
 */
private class EllipseShape(private val a: Vec, private val b: Vec, random: Random, count: Int) : Shape {
    private val center = (a + b) * 0.5
    private val c = (b - a).length / 2
    private val u = (b - a) * (1 / (2 * c))
    private val v = Vec(-u.y, u.x) * (if (random.nextBoolean()) 1.0 else -1.0)
    private val angles = spreadAngles(count, 0.0, PI, random).sortedDescending()

    override val minSize = c

    override fun vias(size: Double): List<Vec> {
        val minor = sqrt((size * size - c * c).coerceAtLeast(0.0))
        return angles.map { t -> center + u * (size * cos(t)) + v * (minor * sin(t)) }
    }
}

/** When A and B coincide: a circle through A, with vias spread around it. */
private class LoopShape(private val start: Vec, random: Random, count: Int) : Shape {
    private val heading = random.nextDouble(0.0, 2 * PI)
    private val direction = if (random.nextBoolean()) 1.0 else -1.0
    private val angles = spreadAngles(count, 0.0, 2 * PI, random, margin = 0.35)

    override val minSize = 0.0

    override fun vias(size: Double): List<Vec> {
        val center = start + Vec(cos(heading), sin(heading)) * size
        return angles.map { t ->
            val phi = heading + PI + direction * t
            center + Vec(cos(phi), sin(phi)) * size
        }
    }
}

/** [count] angles in (from, to), one per equal slice, jittered inside each slice. */
private fun spreadAngles(count: Int, from: Double, to: Double, random: Random, margin: Double = 0.2): List<Double> {
    val slice = (to - from) / count
    return List(count) { i ->
        val lo = from + slice * (i + margin)
        val hi = from + slice * (i + 1 - margin)
        random.nextDouble(lo, hi)
    }
}

class RouteGenerator(
    private val session: BRouterSession,
    private val random: Random = Random.Default,
) {
    companion object {
        const val TOLERANCE = 0.05
        private const val MAX_ATTEMPTS = 5
        private const val MAX_PASSES = 6
        private const val GOOD_OVERLAP = 0.05
        private const val LOOP_THRESHOLD_M = 100.0
        private const val INITIAL_ROAD_FACTOR = 1.3
    }

    /**
     * Picks random via points on an ellipse around [start] and [end], routes
     * through them with BRouter and rescales the ellipse until the route is
     * within 5% of [targetMeters]. Out-and-back detours are trimmed from every
     * track before it is measured. Each attempt uses fresh random via points;
     * among rides on target the one repeating the least road wins, and the
     * closest ride is returned if none hit the tolerance.
     */
    suspend fun generate(
        start: LatLon,
        end: LatLon,
        targetMeters: Double,
        profile: Profile,
        onPass: (Pass) -> Unit,
    ): GeneratedRoute {
        val frame = LocalFrame(LatLon((start.lat + end.lat) / 2, (start.lon + end.lon) / 2))
        val a = frame.toVec(start)
        val b = frame.toVec(end)
        val isLoop = (b - a).length < LOOP_THRESHOLD_M

        var best: GeneratedRoute? = null
        var lastError: BRouterException? = null
        var roadFactor = INITIAL_ROAD_FACTOR
        var passes = 0

        attempts@ for (attempt in 1..MAX_ATTEMPTS) {
            val shape: Shape = if (isLoop) {
                LoopShape(a, random, random.nextInt(2, 4))
            } else {
                val stretch = targetMeters / (b - a).length
                EllipseShape(a, b, random, if (stretch < 1.6) 1 else random.nextInt(2, 4))
            }
            val finish = if (isLoop) a else b
            var desiredCrow = targetMeters / roadFactor

            for (pass in 1..MAX_PASSES) {
                val size = solveSize(shape, a, finish, desiredCrow)
                val vias = shape.vias(size)
                val crow = crowLength(a, vias, finish)
                passes++

                val gpx = try {
                    session.route(listOf(start) + vias.map(frame::toLatLon) + end, profile)
                } catch (e: BRouterException) {
                    if (e.fatal) throw e
                    // Usually a via point in the sea or outside the downloaded
                    // segments; start over with new random points.
                    lastError = e
                    onPass(Pass(attempt, pass, null, e.message))
                    continue@attempts
                }
                val raw = parseTrack(gpx)
                if (raw.size < 2) {
                    lastError = BRouterException("BRouter returned an empty track")
                    continue@attempts
                }
                val track = trimSpurs(raw)
                val length = trackLength(track)
                val route = GeneratedRoute(
                    start = start,
                    end = end,
                    track = track,
                    vias = vias.map(frame::toLatLon),
                    lengthMeters = length,
                    ascendMeters = trackAscend(track),
                    targetMeters = targetMeters,
                    overlap = overlapRatio(track),
                    trimmedMeters = trackLength(raw) - length,
                    passes = passes,
                )
                onPass(Pass(attempt, pass, length, overlap = route.overlap))
                if (route.betterThan(best)) best = route

                if (route.withinTolerance) {
                    // A clean ride is good enough; otherwise try fresh via points
                    // and keep whichever ride repeats the least road.
                    if (route.overlap <= GOOD_OVERLAP) return route.copy(passes = passes)
                    continue@attempts
                }

                if (length <= 0.0) continue@attempts
                if (crow > 0) roadFactor = (length / crow).coerceIn(1.0, 3.0)
                // Rescale: if the ride came out 10% long, shrink the ellipse's
                // straight-line length by 10% and route again.
                val ratio = (targetMeters / length).coerceIn(0.5, 2.0)
                if (ratio < 1 && size <= shape.minSize * 1.0001) {
                    // Already routing (almost) straight from A to B and still too
                    // long: the target is shorter than the direct route.
                    return (best ?: route).copy(passes = passes)
                }
                desiredCrow *= ratio
            }
        }

        return best?.copy(passes = passes)
            ?: throw (lastError ?: BRouterException("BRouter found no route"))
    }

    private fun crowLength(start: Vec, vias: List<Vec>, end: Vec): Double =
        (listOf(start) + vias + end).zipWithNext { p, q -> (q - p).length }.sum()

    /** Bisects for the shape size whose A -> vias -> B crow-flies length is [desired]. */
    private fun solveSize(shape: Shape, start: Vec, end: Vec, desired: Double): Double {
        var lo = shape.minSize
        if (crowLength(start, shape.vias(lo), end) >= desired) return lo
        var hi = maxOf(lo * 2, desired)
        while (crowLength(start, shape.vias(hi), end) < desired) hi *= 2
        repeat(50) {
            val mid = (lo + hi) / 2
            if (crowLength(start, shape.vias(mid), end) < desired) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
}
