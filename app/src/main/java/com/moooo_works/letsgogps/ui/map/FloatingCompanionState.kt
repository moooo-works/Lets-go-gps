package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import java.util.Locale

enum class CompanionTab { SEARCH, SAVED, ROUTES, CONTROL, MAP }

enum class CompanionMessage {
    SEARCH_FAILED, INVALID_COORDINATES, SAVED, ALREADY_SAVED, SAVE_FAILED,
    NEED_TWO_POINTS, ROUTE_CHANGED, NEED_PRO, UNKNOWN_LOCATION, COPIED
}

sealed interface CompanionAction {
    data class Locate(val target: GeocodedLocation) : CompanionAction
    data class Explore(val target: GeocodedLocation) : CompanionAction
    data class UseRoute(val name: String, val points: List<LatLng>) : CompanionAction
    data class PlayRoute(val points: List<LatLng>) : CompanionAction
}

data class CompanionReplacement(
    val action: CompanionAction,
    val previousRoute: List<LatLng>,
    val revision: Long
)

data class FloatingCompanionState(
    val expanded: Boolean = false,
    val tab: CompanionTab = CompanionTab.SEARCH,
    val query: String = "",
    val searching: Boolean = false,
    val searchResults: List<GeocodedLocation> = emptyList(),
    val savedLocations: List<SavedLocation> = emptyList(),
    val folders: List<LocationFolder> = emptyList(),
    val routes: List<RouteSummary> = emptyList(),
    val folderId: Int? = null,
    val favoritesOnly: Boolean = false,
    val selectedLocation: GeocodedLocation? = null,
    val queuedLocations: List<GeocodedLocation> = emptyList(),
    val replacement: CompanionReplacement? = null,
    val message: CompanionMessage? = null,
    val dailySteps: Int = 0,
    val dailyQuota: Int = 0
)

data class FloatingCompanionActions(
    val onExpandedChange: (Boolean) -> Unit,
    val onTabChange: (CompanionTab) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onPaste: () -> Unit,
    val onSelect: (GeocodedLocation) -> Unit,
    val onFolderChange: (Int?) -> Unit,
    val onFavoritesChange: (Boolean) -> Unit,
    val onLoadRoute: (Int) -> Unit,
    val onLocate: () -> Unit,
    val onQueue: () -> Unit,
    val onApplyQueue: () -> Unit,
    val onSave: () -> Unit,
    val onConfirmReplacement: () -> Unit,
    val onCancelReplacement: () -> Unit,
    val onPlayPause: () -> Unit,
    val onStop: () -> Unit,
    val onSpeedChange: (Double) -> Unit,
    val onLoopChange: () -> Unit,
    val onExplore: () -> Unit,
    val onCopyCurrent: () -> Unit,
    val onCopySelected: () -> Unit,
    val onClose: () -> Unit,
    val onOpenApp: () -> Unit,
    val onInputFocus: (Boolean) -> Unit,
    val onWindowDrag: (Float, Float) -> Unit,
    val onWindowDragEnd: () -> Unit
)

fun formatCompanionCoordinate(location: LatLng): String =
    String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)

fun currentCompanionCoordinate(map: MapUiState): LatLng? =
    map.currentMockLocation.takeIf { map.isMocking }
