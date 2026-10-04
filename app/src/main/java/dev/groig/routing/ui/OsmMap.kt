package dev.groig.routing.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.groig.routing.GeneratedRoute
import dev.groig.routing.LatLon
import dev.groig.routing.haversine
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private var osmdroidReady = false

private fun setUpOsmdroid(context: Context) {
    if (osmdroidReady) return
    Configuration.getInstance().apply {
        // The OSM tile servers require an identifying user agent.
        userAgentValue = context.packageName
        val base = File(context.cacheDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
    }
    osmdroidReady = true
}

/** Flips lightness but keeps hues, then dims: a dark map that still reads like OSM. */
private val DarkTiles = run {
    val k = 0.82f
    ColorMatrixColorFilter(
        floatArrayOf(
            0.402f * k, -1.174f * k, -0.228f * k, 0f, 255f * k,
            -0.598f * k, -0.174f * k, -0.228f * k, 0f, 255f * k,
            -0.598f * k, -1.174f * k, 0.772f * k, 0f, 255f * k,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

fun LatLon.toGeoPoint() = GeoPoint(lat, lon)

/** An OpenStreetMap view tied to the screen's lifecycle. */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val map = remember {
        setUpOsmdroid(context.applicationContext)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            setHorizontalMapRepetitionEnabled(false)
            setVerticalMapRepetitionEnabled(false)
            setMinZoomLevel(3.0)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // Keep drags on the map instead of scrolling the list around it.
            setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    map.overlayManager.tilesOverlay.setColorFilter(if (dark) DarkTiles else null)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    return map
}

/** Zooms to fit [points] now, or once the map has been laid out. */
fun MapView.fit(points: List<GeoPoint>, paddingPx: Int) {
    if (points.isEmpty()) return
    val box = BoundingBox.fromGeoPointsSafe(points)
    val apply = {
        if (points.size == 1 || box.latitudeSpan < 1e-4 && box.longitudeSpanWithDateLine < 1e-4) {
            controller.setZoom(15.0)
            controller.setCenter(points.first())
        } else {
            zoomToBoundingBox(box, false, paddingPx)
        }
    }
    if (width > 0 && height > 0) apply() else addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
}

/** The generated ride drawn over OpenStreetMap tiles. */
@Composable
fun RouteMap(route: GeneratedRoute, modifier: Modifier = Modifier) {
    val map = rememberMapView()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val line = colors.secondary
    val casing = colors.surfaceContainerLowest
    val startColor = colors.primary
    val endColor = colors.secondary
    val viaColor = colors.onSurfaceVariant

    LaunchedEffect(route, line) {
        val density = context.resources.displayMetrics.density
        val track = route.track.map { it.pos.toGeoPoint() }
        map.overlays.clear()
        map.overlays += Polyline(map).apply {
            setPoints(track)
            outlinePaint.apply {
                color = casing.toArgb()
                strokeWidth = 9 * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            infoWindow = null
        }
        map.overlays += Polyline(map).apply {
            setPoints(track)
            outlinePaint.apply {
                color = line.toArgb()
                strokeWidth = 5 * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            infoWindow = null
        }
        route.vias.forEach { via ->
            map.overlays += staticMarker(map, via.toGeoPoint(), dot(context, viaColor, casing, 7f))
        }
        val loop = haversine(route.start, route.end) < 100
        if (!loop) map.overlays += staticMarker(map, route.end.toGeoPoint(), pin(context, "B", endColor, casing))
        map.overlays += staticMarker(map, route.start.toGeoPoint(), pin(context, "A", startColor, casing))
        map.fit(track + route.vias.map { it.toGeoPoint() }, (36 * density).toInt())
        map.invalidate()
    }

    AndroidView(factory = { map }, modifier = modifier)
}

private fun staticMarker(map: MapView, at: GeoPoint, drawable: Drawable) = Marker(map).apply {
    position = at
    icon = drawable
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    setOnMarkerClickListener { _, _ -> true }
}

/** A round badge with a letter, like the A and B badges in the point list. */
fun pin(context: Context, label: String, fill: Color, ring: Color): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (30 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    paint.color = ring.toArgb()
    canvas.drawCircle(r, r, r, paint)
    paint.color = fill.toArgb()
    canvas.drawCircle(r, r, r - 2.5f * density, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 14 * density
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(label, r, r - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun dot(context: Context, fill: Color, ring: Color, radiusDp: Float): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (radiusDp * 2 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    paint.color = ring.toArgb()
    canvas.drawCircle(r, r, r, paint)
    paint.color = fill.toArgb()
    canvas.drawCircle(r, r, r * 0.55f, paint)
    return BitmapDrawable(context.resources, bitmap)
}
