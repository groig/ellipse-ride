package dev.groig.routing

import kotlin.math.floor

/*
 * BRouter finds good roads between the points it's given, but random via points
 * can still make silly rides: a via point at the end of a dead end means riding
 * in and straight back out, and legs to and from a via point often share a road.
 * These helpers find both, so the generator can cut the first and avoid the second.
 */

private const val CLOSE_M = 35.0
private const val MIN_SPUR_M = 80.0
private const val MAX_SPUR_M = 8_000.0

private class Measured(points: List<TrackPoint>) {
    val frame = LocalFrame(points.first().pos)
    val v = points.map { frame.toVec(it.pos) }
    val cum = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + (v[i] - v[i - 1]).length
    }

    /** Index of the first point at or after along-track distance [d]. */
    fun indexAt(d: Double): Int {
        var lo = 0
        var hi = cum.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cum[mid] < d) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/**
 * Removes out-and-back detours: stretches that leave a point and come back to
 * it along the same road. The track is still continuous afterwards.
 */
fun trimSpurs(points: List<TrackPoint>): List<TrackPoint> {
    if (points.size < 4) return points
    val m = Measured(points)
    val total = m.cum.last()
    val out = ArrayList<TrackPoint>(points.size)
    var i = 0
    while (i < points.size) {
        out += points[i]
        var cut = -1
        var j = i + 2
        while (j < points.size) {
            val span = m.cum[j] - m.cum[i]
            if (span > MAX_SPUR_M || span > total / 2) break
            if (span >= MIN_SPUR_M && (m.v[j] - m.v[i]).length < CLOSE_M && isOutAndBack(m, i, j)) cut = j
            j++
        }
        i = if (cut > 0) cut else i + 1
    }
    return out
}

/** True when the second half of i..j retraces the first half in reverse. */
private fun isOutAndBack(m: Measured, i: Int, j: Int): Boolean {
    val span = m.cum[j] - m.cum[i]
    val samples = 12
    var matched = 0
    for (s in 1..samples) {
        val d = span / 2 * s / (samples + 1)
        val out = m.v[m.indexAt(m.cum[i] + d)]
        val back = m.v[m.indexAt(m.cum[j] - d)]
        if ((out - back).length < CLOSE_M) matched++
    }
    return matched >= samples * 0.8
}

/** Fraction of the ride (0..1) spent on road already ridden earlier in the ride. */
fun overlapRatio(points: List<TrackPoint>): Double {
    if (points.size < 2) return 0.0
    val m = Measured(points)
    val total = m.cum.last()
    if (total <= 0) return 0.0
    val step = 20.0
    val cell = 30.0
    val seen = HashMap<Long, MutableList<Pair<Vec, Double>>>()
    fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)

    var repeated = 0
    var d = 0.0
    while (d <= total) {
        val p = m.v[m.indexAt(d)]
        val cx = floor(p.x / cell).toInt()
        val cy = floor(p.y / cell).toInt()
        var hit = false
        loop@ for (dx in -1..1) for (dy in -1..1) {
            val earlier = seen[key(cx + dx, cy + dy)] ?: continue
            for ((q, at) in earlier) {
                // Ignore the road just ridden; only count coming back to it later.
                if (at < d - 250 && (p - q).length < 25) {
                    hit = true
                    break@loop
                }
            }
        }
        if (hit) repeated++
        seen.getOrPut(key(cx, cy)) { mutableListOf() } += p to d
        d += step
    }
    return (repeated * step / total).coerceAtMost(1.0)
}
