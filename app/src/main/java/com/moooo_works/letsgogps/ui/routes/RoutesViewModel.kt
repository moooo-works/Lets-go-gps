package com.moooo_works.letsgogps.ui.routes

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.data.backup.BackupManager
import com.moooo_works.letsgogps.data.backup.ImportPreview
import com.moooo_works.letsgogps.data.billing.RewardedAdManager
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.domain.model.SubscriptionOffer
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutesUiState(
    val routes: List<RouteSummary> = emptyList(),
    /** True when the user is multi-selecting for a batch action (currently merge). */
    val isSelectionMode: Boolean = false,
    /** Selection order is preserved — merge concatenates points in this order. */
    val selectedRouteIds: List<Int> = emptyList(),
)

sealed class RoutesEvent {
    /** Tells the screen to dismiss the rename/merge dialog after the underlying op finished. */
    object MergeFinished : RoutesEvent()
}

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val proRepository: ProRepository,
    private val rewardedAdManager: RewardedAdManager,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutesUiState())
    val uiState: StateFlow<RoutesUiState> = _uiState.asStateFlow()

    val isProActive: StateFlow<Boolean> = proRepository.isProActive
    val subscriptionOffer: StateFlow<SubscriptionOffer?> = proRepository.subscriptionOffer

    private val _showProUpgrade = MutableStateFlow(false)
    val showProUpgrade: StateFlow<Boolean> = _showProUpgrade.asStateFlow()

    fun dismissProUpgrade() { _showProUpgrade.value = false }
    fun requestProUpgrade() { _showProUpgrade.value = true }

    fun launchBillingFlow(activity: Activity) {
        proRepository.launchBillingFlow(activity)
        dismissProUpgrade()
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

    // Snapshot check at dialog-open time; no ticking flow needed here (the
    // dialog is short-lived, unlike the settings page's live countdown).
    fun watchAdEnabled(): Boolean =
        proRepository.adUnlockExpiryMillis.value - System.currentTimeMillis() < 18 * 3600_000L

    fun exportData(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        if (!proRepository.isProActive.value) {
            onResult(false, "PRO_REQUIRED")
            return
        }
        viewModelScope.launch {
            try {
                backupManager.exportToUri(uri, includeSavedLocations = false, includeRoutes = true)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
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
                onResult(true, backupManager.parseImport(uri), null)
            } catch (e: Exception) {
                onResult(false, null, e.message)
            }
        }
    }

    fun applyImportData(preview: ImportPreview, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(true, backupManager.applyImport(preview))
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    private val _events = MutableSharedFlow<RoutesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RoutesEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeRoutes().collect { routes ->
                _uiState.update { it.copy(routes = routes) }
            }
        }
    }

    fun renameRoute(routeId: Int, name: String) {
        viewModelScope.launch {
            repository.updateRouteName(routeId, name)
        }
    }

    fun deleteRoute(routeId: Int) {
        viewModelScope.launch {
            repository.deleteRoute(routeId)
        }
    }

    /** Long-press handler — enters batch mode pre-selected on the long-pressed item. */
    fun enterSelectionMode(routeId: Int) {
        _uiState.update {
            it.copy(isSelectionMode = true, selectedRouteIds = listOf(routeId))
        }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedRouteIds = emptyList()) }
    }

    /** Toggle selection. Order matters — earlier picks come first when merging. */
    fun toggleSelection(routeId: Int) {
        _uiState.update { state ->
            val current = state.selectedRouteIds
            val next = if (routeId in current) current - routeId else current + routeId
            state.copy(selectedRouteIds = next)
        }
    }

    /**
     * Concatenate points of selected routes (in selection order) into a new route.
     * Originals are kept — the user can delete them afterwards if they want.
     */
    fun mergeSelected(newName: String) {
        val ids = _uiState.value.selectedRouteIds
        if (ids.size < 2) return
        viewModelScope.launch {
            val mergedPoints = mutableListOf<RoutePoint>()
            ids.forEach { id ->
                val rwp = repository.getRouteWithPoints(id) ?: return@forEach
                rwp.points.sortedBy { it.orderIndex }.forEach { p ->
                    // orderIndex will be reassigned by insertRouteWithPoints; the
                    // routeId placeholder (0) is also overwritten there.
                    mergedPoints.add(
                        RoutePoint(
                            routeId = 0,
                            orderIndex = mergedPoints.size,
                            latitude = p.latitude,
                            longitude = p.longitude,
                            dwellSeconds = p.dwellSeconds,
                        )
                    )
                }
            }
            if (mergedPoints.isNotEmpty()) {
                repository.insertRouteWithPoints(newName.trim(), mergedPoints)
            }
            exitSelectionMode()
            _events.tryEmit(RoutesEvent.MergeFinished)
        }
    }

    /**
     * Builds a default name for the merge dialog: "A + B" or "A + B (+N more)".
     * Trims overall to keep within typical UI/ DB length conventions.
     */
    fun buildDefaultMergedName(): String {
        val state = _uiState.value
        val routesById = state.routes.associateBy { it.id }
        val names = state.selectedRouteIds.mapNotNull { routesById[it]?.name }
        if (names.isEmpty()) return ""
        return when {
            names.size <= 2 -> names.joinToString(" + ")
            else -> "${names[0]} + ${names[1]} (+${names.size - 2} more)"
        }.take(40)
    }
}
