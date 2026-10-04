package dev.groig.routing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OSMAND_PACKAGES = listOf("net.osmand.plus", "net.osmand")
private const val GPX_MIME = "application/gpx+xml"

private fun saveGpx(context: Context, route: GeneratedRoute): Uri {
    val km = "%.1f".format(Locale.US, route.lengthMeters / 1000)
    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
    val dir = File(context.cacheDir, "routes").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "ellipse-ride_${stamp}_${km}km.gpx")
    file.writeText(writeGpx(route.track, "Ellipse Ride $km km"))
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/** Opens the GPX in OsmAnd if installed; returns false if it is not. */
fun openInOsmAnd(context: Context, route: GeneratedRoute): Boolean {
    val uri = saveGpx(context, route)
    for (pkg in OSMAND_PACKAGES) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, GPX_MIME)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // try the next flavour
        }
    }
    return false
}

fun shareGpx(context: Context, route: GeneratedRoute) {
    val uri = saveGpx(context, route)
    val send = Intent(Intent.ACTION_SEND)
        .setType(GPX_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share route").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openStore(context: Context, pkg: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$pkg/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
