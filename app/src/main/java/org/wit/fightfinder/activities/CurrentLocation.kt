package org.wit.fightfinder.activities

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.wit.fightfinder.models.Coordinates
import kotlin.coroutines.resume

private const val FIX_TIMEOUT_MS = 35_000L
private const val MAX_FIX_AGE_MS = 30_000L

internal suspend fun currentLocation(context: Context): Coordinates? {
    val fine = context.checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
    if (!fine && context.checkSelfPermission(ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java)
    if (!manager.isLocationEnabled) return null

    val sources = mutableListOf<suspend () -> Coordinates?>()
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
        sources.add { playServicesFix(context, fine) }
    }
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (fine) add(LocationManager.GPS_PROVIDER)
    }
    for (provider in providers) {
        if (manager.isProviderEnabled(provider)) {
            sources.add { platformFix(context, manager, provider) }
        }
    }
    return firstAvailableFix(sources, FIX_TIMEOUT_MS)
}

internal suspend fun firstAvailableFix(
    sources: List<suspend () -> Coordinates?>,
    timeoutMillis: Long
): Coordinates? = withTimeoutOrNull(timeoutMillis) {
    val results = Channel<Coordinates?>(sources.size)
    val jobs = sources.map { source -> launch { results.send(source()) } }
    try {
        repeat(sources.size) {
            val point = results.receive()
            if (point != null) return@withTimeoutOrNull point
        }
        null
    } finally {
        jobs.forEach { it.cancel() }
    }
}

// Both APIs are called after the foreground permission check. Handle revocation during a request.
@SuppressLint("MissingPermission")
private suspend fun playServicesFix(context: Context, fine: Boolean): Coordinates? =
    suspendCancellableCoroutine { result ->
        val token = CancellationTokenSource()
        result.invokeOnCancellation { token.cancel() }
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request, token.token)
                .addOnCompleteListener { task ->
                    val point = if (task.isSuccessful) task.result?.recentCoordinates() else null
                    if (result.isActive) result.resume(point)
                }
        } catch (_: SecurityException) {
            result.resume(null)
        }
    }

@SuppressLint("MissingPermission")
private suspend fun platformFix(context: Context, manager: LocationManager, provider: String): Coordinates? =
    suspendCancellableCoroutine { result ->
        val signal = CancellationSignal()
        result.invokeOnCancellation { signal.cancel() }
        try {
            val cached = manager.getLastKnownLocation(provider)?.recentCoordinates()
            if (cached != null) {
                result.resume(cached)
            } else {
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { fix ->
                    if (result.isActive) result.resume(fix?.recentCoordinates())
                }
            }
        } catch (_: SecurityException) {
            result.resume(null)
        } catch (_: IllegalArgumentException) {
            // A provider can disappear between the availability check and request.
            result.resume(null)
        }
    }

private fun Location.recentCoordinates(): Coordinates? {
    val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
    return if (ageNanos in 0..MAX_FIX_AGE_MS * 1_000_000L) Coordinates(latitude, longitude) else null
}
