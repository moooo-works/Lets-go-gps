package com.moooo_works.letsgogps.data.healthcheck

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckItem
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState
import com.moooo_works.letsgogps.domain.healthcheck.ItemStatus
import com.moooo_works.letsgogps.domain.healthcheck.SystemHealthCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemHealthCheckImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : SystemHealthCheck {

    override fun refresh(): HealthCheckState {
        val items = HealthCheckItem.values().associateWith(::evaluate)
        return HealthCheckState(items)
    }

    private fun evaluate(item: HealthCheckItem): ItemStatus = when (item) {
        HealthCheckItem.NotificationPermission -> evaluateNotification()
        HealthCheckItem.LocationPermission -> evaluateLocationPermission()
        HealthCheckItem.GpsEnabled -> evaluateGpsEnabled()
        HealthCheckItem.DeveloperMode -> evaluateDeveloperMode()
        HealthCheckItem.MockAppSelected -> evaluateMockAppSelected()
        HealthCheckItem.BatteryOptimizationExempt -> evaluateBatteryExempt()
    }

    private fun evaluateNotification(): ItemStatus {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return ItemStatus.NotApplicable
        return if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) ItemStatus.Passed
        else ItemStatus.Failed
    }

    private fun evaluateLocationPermission(): ItemStatus {
        val fine = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return if (fine || coarse) ItemStatus.Passed else ItemStatus.Failed
    }

    private fun isPermissionGranted(permission: String): Boolean {
        // Direct Context API instead of ContextCompat: avoids TextUtils dep
        // which breaks JVM unit tests, and works on every supported SDK
        // (minSdk = 26).
        return context.checkPermission(permission, Process.myPid(), Process.myUid()) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun evaluateGpsEnabled(): ItemStatus {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ItemStatus.Failed
        return try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) ItemStatus.Passed
            else ItemStatus.Failed
        } catch (_: Exception) {
            ItemStatus.Failed
        }
    }

    private fun evaluateDeveloperMode(): ItemStatus {
        val enabled = try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (_: Exception) {
            // Rare ROMs throw SettingNotFoundException — fail open here so the
            // user can still attempt to mock; AppOps will catch app-not-selected.
            true
        }
        return if (enabled) ItemStatus.Passed else ItemStatus.Failed
    }

    private fun evaluateMockAppSelected(): ItemStatus {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return ItemStatus.Failed
        val mode = try {
            if (sdkInt >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            }
        } catch (_: Exception) {
            return ItemStatus.Failed
        }
        return if (mode == AppOpsManager.MODE_ALLOWED) ItemStatus.Passed else ItemStatus.Failed
    }

    private fun evaluateBatteryExempt(): ItemStatus {
        if (sdkInt < Build.VERSION_CODES.M) return ItemStatus.NotApplicable
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return ItemStatus.Failed
        return if (pm.isIgnoringBatteryOptimizations(context.packageName)) ItemStatus.Passed
        else ItemStatus.Failed
    }
}
