package com.moooo_works.letsgogps.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.model.SubscriptionOffer
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.billing.RewardedAdManager
import com.moooo_works.letsgogps.data.health.HealthConnectAvailability
import com.moooo_works.letsgogps.data.backup.BackupManager
import com.moooo_works.letsgogps.data.backup.ImportPreview

sealed interface ProSectionState {
    object Free : ProSectionState
    data class AdUnlocked(val remainingMillis: Long, val watchAdEnabled: Boolean) : ProSectionState
    object Subscribed : ProSectionState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val settingsRepository: SettingsRepository,
    private val mockEngine: LocationMockEngine,
    private val proRepository: ProRepository,
    private val systemHealthCheck: com.moooo_works.letsgogps.domain.healthcheck.SystemHealthCheck,
    private val rewardedAdManager: RewardedAdManager,
    private val backupManager: BackupManager,
    private val healthConnectAvailability: HealthConnectAvailability,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val isProActive: StateFlow<Boolean> = proRepository.isProActive

    /** Play Billing subscription offer: localized recurring price + trial
     *  eligibility. Null until ProductDetails has loaded; UI falls back to a
     *  no-price, no-trial render in that window. */
    val subscriptionOffer: StateFlow<SubscriptionOffer?> = proRepository.subscriptionOffer

    /**
     * Injectable time source for tests. When non-null, replaces the default
     * 60-second ticker. Set this BEFORE first access to [proSection] (which is
     * lazy) so the override is picked up.
     */
    @VisibleForTesting
    internal var nowFlowOverride: Flow<Long>? = null

    private val nowTick: Flow<Long> by lazy {
        nowFlowOverride ?: flow {
            while (true) {
                emit(System.currentTimeMillis())
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    val proSection: StateFlow<ProSectionState> by lazy {
        combine(
            proRepository.isAdFreeActive,
            proRepository.adUnlockExpiryMillis,
            nowTick
        ) { adFree, expiry, now ->
            when {
                adFree -> ProSectionState.Subscribed
                expiry > now -> {
                    val remaining = expiry - now
                    ProSectionState.AdUnlocked(
                        remainingMillis = remaining,
                        watchAdEnabled = remaining < 18 * 3600_000L
                    )
                }
                else -> ProSectionState.Free
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProSectionState.Free)
    }

    fun watchRewardedAd(activity: Activity, onUnavailable: () -> Unit = {}) {
        rewardedAdManager.showAd(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    proRepository.grantAdUnlockHours(6)
                }
            },
            onUnavailable = onUnavailable
        )
    }

    private val _showProUpgrade = MutableStateFlow(false)
    val showProUpgrade: StateFlow<Boolean> = _showProUpgrade.asStateFlow()

    fun dismissProUpgrade() { _showProUpgrade.value = false }

    fun requestProUpgrade() { _showProUpgrade.value = true }

    fun launchBillingFlow(activity: Activity) {
        proRepository.launchBillingFlow(activity)
        dismissProUpgrade()
    }

    private val _mockPermissionStatus = MutableStateFlow<MockPermissionStatus>(MockPermissionStatus.NotAllowed)
    val mockPermissionStatus: StateFlow<MockPermissionStatus> = _mockPermissionStatus.asStateFlow()

    private val _healthCheckState = MutableStateFlow<com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState?>(null)
    val healthCheckState: StateFlow<com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState?> = _healthCheckState.asStateFlow()

    private val _showHealthCheck = MutableStateFlow(false)
    val showHealthCheck: StateFlow<Boolean> = _showHealthCheck.asStateFlow()

    fun openHealthCheck() {
        _healthCheckState.value = systemHealthCheck.refresh()
        _showHealthCheck.value = true
    }

    fun refreshHealthCheck() {
        _healthCheckState.value = systemHealthCheck.refresh()
    }

    fun dismissHealthCheck() {
        _showHealthCheck.value = false
    }

    val altitude = settingsRepository.observeAltitude()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15.0)

    val randomAltitude = settingsRepository.observeRandomAltitude()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val coordinateJitter = settingsRepository.observeCoordinateJitter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val routeCornerSlowdown = settingsRepository.observeRouteCornerSlowdown()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clipboardHintEnabled = settingsRepository.observeClipboardHintEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enableTimezoneCheck = settingsRepository.observeEnableTimezoneCheck()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setEnableTimezoneCheck(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEnableTimezoneCheck(enabled) }
    }

    // ── Health Connect 步數同步 ───────────────────────────────────────────

    val stepSyncEnabled = settingsRepository.observeStepSyncEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val stepLengthMeters = settingsRepository.observeStepLengthMeters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.75)

    val stepDailyQuota = settingsRepository.observeStepDailyQuota()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20_000)

    val stepQuotaUsedToday = settingsRepository.observeStepQuotaUsedToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 僅訂閱。計次制不能用 isProActive 判斷——那個包含 ad-unlock。 */
    val isSubscriptionActive: StateFlow<Boolean> = proRepository.isSubscriptionActive

    val featureCredits: StateFlow<Int> = proRepository.featureCredits

    /**
     * Health Connect 在本裝置上的可用性。
     *
     * 每次呼叫都重新查詢，因為使用者可能在系統設定裡安裝／移除／撤銷權限後
     * 才切回來——快取住會顯示過期狀態。
     */
    fun healthConnectStatus(): HealthConnectAvailability.Status = healthConnectAvailability.status()

    val healthConnectWritePermissions: Set<String>
        get() = healthConnectAvailability.writePermissions

    fun requestHealthPermissionsContract() =
        healthConnectAvailability.requestPermissionsContract()

    private val _hasHealthPermission = MutableStateFlow(false)
    val hasHealthPermission: StateFlow<Boolean> = _hasHealthPermission.asStateFlow()

    /** 回到設定頁或授權流程結束後呼叫，重新對齊權限狀態。 */
    fun refreshHealthPermission() {
        viewModelScope.launch {
            val granted = healthConnectAvailability.hasWritePermission()
            _hasHealthPermission.value = granted
            // 權限被撤銷時自動關掉同步，避免使用者以為還在寫。
            if (!granted && stepSyncEnabled.value) {
                settingsRepository.setStepSyncEnabled(false)
            }
        }
    }

    fun openHealthConnectPlayStore() = healthConnectAvailability.openPlayStoreForProvider()

    fun openHealthConnectSettings() = healthConnectAvailability.openHealthConnectSettings()

    fun setStepSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStepSyncEnabled(enabled) }
    }

    fun setStepLengthMeters(meters: Double) {
        viewModelScope.launch { settingsRepository.setStepLengthMeters(meters) }
    }

    fun setStepDailyQuota(quota: Int) {
        viewModelScope.launch { settingsRepository.setStepDailyQuota(quota) }
    }

    fun setAltitude(value: Double) {
        viewModelScope.launch {
            settingsRepository.setAltitude(value)
        }
    }

    fun setRandomAltitude(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRandomAltitude(enabled)
        }
    }

    fun setCoordinateJitter(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCoordinateJitter(enabled)
        }
    }

    fun setRouteCornerSlowdown(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRouteCornerSlowdown(enabled)
        }
    }

    fun setClipboardHintEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardHintEnabled(enabled)
        }
    }

    init {
        refreshMockPermission()
        rewardedAdManager.preload()
    }

    fun refreshMockPermission() {
        _mockPermissionStatus.value = mockEngine.getMockPermissionStatus()
    }

    fun clearNonFavorites() {
        viewModelScope.launch {
            locationRepository.deleteNonFavorites()
        }
    }

    fun exportDataToUri(uri: Uri, includeSavedLocations: Boolean, includeRoutes: Boolean, onResult: (Boolean, String?) -> Unit) {
        if (!proRepository.isProActive.value) {
            onResult(false, "PRO_REQUIRED")
            return
        }
        viewModelScope.launch {
            try {
                backupManager.exportToUri(uri, includeSavedLocations, includeRoutes)
                withContext(Dispatchers.Main) { onResult(true, null) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message) }
            }
        }
    }

    fun parseImportData(uri: Uri, onResult: (Boolean, ImportPreview?, String?) -> Unit) {
        if (!proRepository.isProActive.value) {
            onResult(false, null, "PRO_REQUIRED")
            return
        }
        viewModelScope.launch {
            try {
                val preview = backupManager.parseImport(uri)
                withContext(Dispatchers.Main) { onResult(true, preview, null) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, null, e.message) }
            }
        }
    }

    fun applyImportData(preview: ImportPreview, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val summary = backupManager.applyImport(preview)
                withContext(Dispatchers.Main) { onResult(true, summary) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message) }
            }
        }
    }

    fun generateDiagnostics(): String {
        val pm = context.packageManager
        val versionName = try {
            val pInfo = pm.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "Unknown"
        }
        val versionCode = try {
            val pInfo = pm.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            "Unknown"
        }

        val apiLevel = Build.VERSION.SDK_INT
        val targetSdk = context.applicationInfo.targetSdkVersion

        val mockStatus = mockStateRepository.mockStatus.value.name

        val notificationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val fgsLocationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return """
            Fake GPS Diagnostics
            -------------------
            Version Name: $versionName
            Version Code: $versionCode
            API Level: $apiLevel
            Target SDK: $targetSdk
            Mock Status: $mockStatus
            Notification Perm: $notificationPerm
            FGS Location Perm: $fgsLocationPerm
        """.trimIndent()
    }
}
