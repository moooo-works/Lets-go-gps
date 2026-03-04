package com.example.mockgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mockgps.ui.map.MapScreen
import com.example.mockgps.ui.map.MapViewModel
import com.example.mockgps.ui.routes.RoutesScreen
import com.example.mockgps.ui.routes.RoutesViewModel
import com.example.mockgps.ui.savedlocations.SavedLocationsScreen
import com.example.mockgps.ui.savedlocations.SavedLocationsViewModel
import com.example.mockgps.ui.settings.SettingsScreen
import com.example.mockgps.ui.settings.SettingsViewModel
import com.example.mockgps.ui.theme.MockGpsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MockGpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "map") {
        composable("map") { backStackEntry ->
            val viewModel: MapViewModel = hiltViewModel(backStackEntry)

            val selectedLat by backStackEntry.savedStateHandle.getStateFlow<Double?>("selectedLat", null).collectAsState()
            val selectedLng by backStackEntry.savedStateHandle.getStateFlow<Double?>("selectedLng", null).collectAsState()
            val selectedRouteId by backStackEntry.savedStateHandle.getStateFlow<Int?>("selectedRouteId", null).collectAsState()

            LaunchedEffect(selectedRouteId) {
                val routeId = selectedRouteId ?: return@LaunchedEffect
                viewModel.loadRoute(routeId)
                backStackEntry.savedStateHandle.remove<Int>("selectedRouteId")
            }

            MapScreen(
                viewModel = viewModel,
                selectedLocation = if (selectedLat != null && selectedLng != null) {
                    com.google.android.gms.maps.model.LatLng(selectedLat!!, selectedLng!!)
                } else {
                    null
                },
                onSelectedLocationConsumed = {
                    backStackEntry.savedStateHandle.remove<Double>("selectedLat")
                    backStackEntry.savedStateHandle.remove<Double>("selectedLng")
                },
                onNavigateToSavedLocations = { navController.navigate("saved_locations") },
                onNavigateToRoutes = { navController.navigate("routes") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("saved_locations") { backStackEntry ->
            val viewModel: SavedLocationsViewModel = hiltViewModel(backStackEntry)

            SavedLocationsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onLocationSelected = { lat, lng ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedLat", lat)
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedLng", lng)
                    navController.popBackStack()
                }
            )
        }
        composable("routes") { backStackEntry ->
            val viewModel: RoutesViewModel = hiltViewModel(backStackEntry)
            RoutesScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onRouteSelected = { routeId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedRouteId", routeId)
                    navController.popBackStack()
                }
            )
        }

        composable("settings") { backStackEntry ->
            val viewModel: SettingsViewModel = hiltViewModel(backStackEntry)
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
