package com.example.mockgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mockgps.ui.map.MapScreen
import com.example.mockgps.ui.map.MapViewModel
import com.example.mockgps.ui.savedlocations.SavedLocationsScreen
import com.example.mockgps.ui.savedlocations.SavedLocationsViewModel
import com.example.mockgps.ui.routes.RoutesScreen
import com.example.mockgps.ui.routes.RoutesViewModel
import com.google.gson.Gson
import com.example.mockgps.data.model.RoutePoint
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
            // Use hiltViewModel(backStackEntry) to properly scope to the destination
            // This ensures SavedStateHandle is correctly provided by the Navigation Component
            val viewModel: MapViewModel = hiltViewModel(backStackEntry)

            // Check for returned result from SavedLocations
            val selectedLat by backStackEntry.savedStateHandle.getStateFlow<Double?>("selectedLat", null).collectAsState()
            val selectedLng by backStackEntry.savedStateHandle.getStateFlow<Double?>("selectedLng", null).collectAsState()

            // Check for returned result from Routes
            val loadedRoutePointsJson by backStackEntry.savedStateHandle.getStateFlow<String?>("loadedRoutePoints", null).collectAsState()
            val loadedRouteSpeed by backStackEntry.savedStateHandle.getStateFlow<Double?>("loadedRouteSpeed", null).collectAsState()

            if (loadedRoutePointsJson != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<RoutePoint>>() {}.type
                val points: List<RoutePoint> = Gson().fromJson(loadedRoutePointsJson, type)
                viewModel.loadRoute(points, loadedRouteSpeed ?: 5.0)
                backStackEntry.savedStateHandle.remove<String>("loadedRoutePoints")
                backStackEntry.savedStateHandle.remove<Double>("loadedRouteSpeed")
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
                onNavigateToRoutes = { navController.navigate("routes") }
            )
        }
        composable("routes") { backStackEntry ->
            val viewModel: RoutesViewModel = hiltViewModel(backStackEntry)
            RoutesScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onRouteSelected = { route, points ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("loadedRoutePoints", Gson().toJson(points))
                    navController.previousBackStackEntry?.savedStateHandle?.set("loadedRouteSpeed", route.defaultSpeed)
                    navController.popBackStack()
                }
            )
        }
        composable("saved_locations") { backStackEntry ->
            // Use hiltViewModel(backStackEntry) here too for safety/correctness
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
    }
}
