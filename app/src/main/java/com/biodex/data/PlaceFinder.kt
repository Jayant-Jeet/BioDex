package com.biodex.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object PlaceFinder {
    const val PERMISSION = android.Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Returns "City, State, Country", plain coordinates when offline, or null without permission/fix. */
    suspend fun currentPlace(context: Context): String? {
        if (!hasPermission(context)) return null
        val location = findLocation(context) ?: return null
        return try {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            listOfNotNull(
                address?.locality ?: address?.subAdminArea,
                address?.adminArea,
                address?.countryName,
            ).distinct().joinToString(", ").ifBlank { null }
        } catch (exception: Exception) {
            null
        } ?: String.format(Locale.ROOT, "%.4f, %.4f", location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun findLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true)
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (cached != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || providers.isEmpty()) return cached
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                val signal = android.os.CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    providers.first(), signal, ContextCompat.getMainExecutor(context),
                ) { continuation.resume(it) }
            }
        }
    }
}
