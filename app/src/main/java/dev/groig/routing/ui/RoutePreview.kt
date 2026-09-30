package dev.groig.routing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.groig.routing.LatLon
import dev.groig.routing.LocalFrame
import dev.groig.routing.Vec
import kotlin.math.min

/** A map-less sketch of the route: the track, its via points, and A and B. */
@Composable
fun RoutePreview(
    track: List<LatLon>,
    vias: List<LatLon>,
    start: LatLon,
    end: LatLon,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val startColor = colors.primary
    val endColor = colors.secondary
    val grid = colors.outlineVariant
    val casing = colors.surfaceContainerLowest
    val viaColor = colors.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

    val projected = remember(track, vias, start, end) {
        val frame = LocalFrame(start)
        Projected(
            track = track.map(frame::toVec),
            vias = vias.map(frame::toVec),
            start = frame.toVec(start),
            end = frame.toVec(end),
        )
    }

    Canvas(modifier) {
        val gap = 24.dp.toPx()
        var y = gap / 2
        while (y < size.height) {
            var x = gap / 2
            while (x < size.width) {
                drawCircle(grid, radius = 1.2.dp.toPx(), center = Offset(x, y))
                x += gap
            }
            y += gap
        }

        val all = projected.track + projected.vias + projected.start + projected.end
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        val pad = 28.dp.toPx()
        val spanX = (maxX - minX).coerceAtLeast(1.0)
        val spanY = (maxY - minY).coerceAtLeast(1.0)
        val scale = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun at(v: Vec) = Offset(
            (offX + (v.x - minX) * scale).toFloat(),
            (size.height - offY - (v.y - minY) * scale).toFloat(), // north is up
        )

        if (projected.track.size >= 2) {
            val path = Path().apply {
                val first = at(projected.track.first())
                moveTo(first.x, first.y)
                projected.track.drop(1).forEach { at(it).let { p -> lineTo(p.x, p.y) } }
            }
            // Run the gradient from A to the far end of the ride, so loops get one too.
            val a = at(projected.start)
            val far = projected.track.map(::at).maxBy { (it - a).getDistance() }
            val brush = if ((far - a).getDistance() > 1f) {
                Brush.linearGradient(listOf(startColor, endColor), start = a, end = far)
            } else {
                Brush.linearGradient(listOf(startColor, startColor))
            }
            val round = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, casing, style = round)
            drawPath(path, brush, style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        projected.vias.forEach {
            drawCircle(casing, radius = 6.dp.toPx(), center = at(it))
            drawCircle(viaColor, radius = 3.5.dp.toPx(), center = at(it))
        }

        fun pin(v: Vec, color: Color, label: String) {
            val c = at(v)
            drawCircle(casing, radius = 14.dp.toPx(), center = c)
            drawCircle(color, radius = 11.dp.toPx(), center = c)
            val text = measurer.measure(label, labelStyle)
            drawText(
                text,
                topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f),
            )
        }
        if ((at(projected.end) - at(projected.start)).getDistance() < 20.dp.toPx()) {
            pin(projected.start, startColor, "A")
        } else {
            pin(projected.end, endColor, "B")
            pin(projected.start, startColor, "A")
        }
    }
}

private class Projected(val track: List<Vec>, val vias: List<Vec>, val start: Vec, val end: Vec)
