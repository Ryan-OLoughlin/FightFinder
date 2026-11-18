package org.wit.fightfinder.activities

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.wit.fightfinder.models.Coordinates
import kotlin.coroutines.resume

internal suspend fun currentLocation(context: Context): Coordinates? {
    val fine = context.checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
    if (!fine && context.checkSelfPermission(ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java)
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (fine) add(LocationManager.GPS_PROVIDER)
    }.intersect(manager.getProviders(true).toSet())
    if (providers.isEmpty()) return null

    // A first fix can take much longer indoors. Give the form a bounded wait.
    return withTimeoutOrNull(12_000L) {
        val requests = providers.associateWith { CancellationSignal() }
        try {
            suspendCancellableCoroutine { result ->
                var remaining = requests.size
                fun receive(point: Coordinates?) {
                    remaining--
                    if (result.isActive && (point != null || remaining == 0)) result.resume(point)
                }
                for ((provider, signal) in requests) {
                    try {
                        manager.getCurrentLocation(provider, signal, context.mainExecutor) { fix ->
                            receive(fix?.let { Coordinates(it.latitude, it.longitude) })
                        }
                    } catch (_: SecurityException) {
                        receive(null)
                    } catch (_: IllegalArgumentException) {
                        // A provider may disappear between enumeration and the request.
                        receive(null)
                    }
                }
            }
        } finally {
            requests.values.forEach { it.cancel() }
        }
    }
}
