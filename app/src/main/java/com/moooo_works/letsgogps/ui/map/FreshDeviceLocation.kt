package com.moooo_works.letsgogps.ui.map

import android.location.Location
import androidx.core.location.LocationCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A one-shot real fix. Never fall back to the last simulated location. */
internal suspend fun freshDeviceLocation(client: FusedLocationProviderClient): Location? =
    withTimeoutOrNull(16_000) {
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .setDurationMillis(15_000)
                .build()
            try {
                client.getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            continuation.resume(location?.takeUnless { LocationCompat.isMock(it) })
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (e: SecurityException) {
                // Explicitly handle missing runtime permission; the button explains it to the user.
                if (continuation.isActive) continuation.resumeWithException(e)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }
