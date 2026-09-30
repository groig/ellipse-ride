package dev.groig.routing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Pass(val attempt: Int, val pass: Int, val lengthMeters: Double?, val error: String? = null)

data class GeneratedRoute(
    val gpx: String,
    val start: LatLon,
    val end: LatLon,
    val track: List<LatLon>,
    val vias: List<LatLon>,
    val lengthMeters: Double,
    val ascendMeters: Int?,
    val targetMeters: Double,
    val passes: Int,
) {
    val deviation: Double get() = (lengthMeters - targetMeters) / targetMeters
    val withinTolerance: Boolean get() = abs(deviation) <= RouteGenerator.TOLERANCE
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
        private const val MAX_ATTEMPTS = 4
        private const val MAX_PASSES = 8
        private const val LOOP_THRESHOLD_M = 100.0
        private const val INITIAL_ROAD_FACTOR = 1.3
    }

    /**
     * Picks random via points on an ellipse around [start] and [end], routes
     * through them with BRouter and rescales the ellipse until the route is
     * within 5% of [targetMeters]. Each attempt uses fresh random via points;
     * the closest route found is returned if none hit the tolerance.
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

        for (attempt in 1..MAX_ATTEMPTS) {
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
                    break
                }
                val parsed = parseGpx(gpx)
                val length = parsed.lengthMeters
                onPass(Pass(attempt, pass, length))

                val route = GeneratedRoute(
                    gpx = gpx,
                    start = start,
                    end = end,
                    track = parsed.points,
                    vias = vias.map(frame::toLatLon),
                    lengthMeters = length,
                    ascendMeters = parsed.ascendMeters,
                    targetMeters = targetMeters,
                    passes = passes,
                )
                if (best == null || abs(route.deviation) < abs(best.deviation)) best = route
                if (route.withinTolerance) return route.copy(passes = passes)

                if (length <= 0.0) break
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
