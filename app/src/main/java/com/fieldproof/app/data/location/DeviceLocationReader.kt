package com.fieldproof.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.fieldproof.app.domain.model.LocationFix

/**
 * Reads the last known location from the platform LocationManager.
 * Values are device-reported and may be stale, mocked, or missing.
 */
class DeviceLocationReader(private val context: Context) {
    fun lastKnown(): LocationFix? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val best = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time } ?: return null
        return LocationFix(
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = if (best.hasAccuracy()) best.accuracy else null,
            recordedAtEpochMs = best.time,
            provider = best.provider,
        )
    }
}
