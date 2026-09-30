package dev.groig.routing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import btools.routingapp.IBRouterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val BROUTER_PACKAGE = "btools.routingapp"
private const val BROUTER_SERVICE = "btools.routingapp.BRouterService"

class BRouterException(message: String, val fatal: Boolean = false) : Exception(message)

enum class Profile(val id: String, val label: String) {
    Trekking("trekking", "Trekking"),
    Fast("fastbike", "Fast"),
    Safety("safety", "Quiet"),
}

fun isBRouterInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(BROUTER_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** A bound connection to BRouter's routing service. Close it when done. */
class BRouterSession private constructor(
    private val context: Context,
    private val connection: ServiceConnection,
    private val service: IBRouterService,
) : AutoCloseable {

    /** Routes through [points] (start, vias..., end) and returns the GPX. */
    suspend fun route(points: List<LatLon>, profile: Profile): String = withContext(Dispatchers.IO) {
        val params = Bundle().apply {
            putDoubleArray("lats", points.map { it.lat }.toDoubleArray())
            putDoubleArray("lons", points.map { it.lon }.toDoubleArray())
            putString("profile", profile.id)
            putString("v", "bicycle")
            putString("fast", if (profile == Profile.Fast) "1" else "0")
            putString("trackFormat", "gpx")
            putString("maxRunningTime", "60")
        }
        val result = try {
            service.getTrackFromParams(params)
        } catch (e: Exception) {
            throw BRouterException("BRouter crashed: ${e.message}", fatal = true)
        }
        when {
            result == null -> throw BRouterException("BRouter returned nothing")
            result.trimStart().startsWith("<") -> result
            else -> throw BRouterException(result.trim())
        }
    }

    override fun close() {
        runCatching { context.unbindService(connection) }
    }

    companion object {
        suspend fun open(context: Context): BRouterSession {
            val app = context.applicationContext
            if (!isBRouterInstalled(app)) {
                throw BRouterException("BRouter is not installed", fatal = true)
            }
            var connection: ServiceConnection? = null
            return try {
                withTimeout(10_000) {
                    suspendCancellableCoroutine { cont ->
                        val conn = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                                if (cont.isActive) {
                                    cont.resume(BRouterSession(app, this, IBRouterService.Stub.asInterface(binder)))
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName) {}

                            override fun onBindingDied(name: ComponentName) {
                                if (cont.isActive) {
                                    cont.resumeWithException(BRouterException("BRouter service died", fatal = true))
                                }
                            }
                        }
                        connection = conn
                        val intent = Intent().setClassName(BROUTER_PACKAGE, BROUTER_SERVICE)
                        if (!app.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                            cont.resumeWithException(
                                BRouterException("Could not bind to BRouter's service", fatal = true),
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                connection?.let { runCatching { app.unbindService(it) } }
                throw if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    BRouterException("Timed out connecting to BRouter", fatal = true)
                } else {
                    e
                }
            }
        }
    }
}
