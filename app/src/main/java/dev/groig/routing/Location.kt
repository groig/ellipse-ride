package dev.groig.routing

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Current position, or the last known one if a fix takes too long. Needs location permission. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LatLon? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { lm.isProviderEnabled(it) }
    val fresh = provider?.let {
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    lm, it, signal, ContextCompat.getMainExecutor(context),
                ) { loc -> if (cont.isActive) cont.resume(loc) }
            }
        }
    }
    val loc = fresh ?: lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    return loc?.let { LatLon(it.latitude, it.longitude) }
}
