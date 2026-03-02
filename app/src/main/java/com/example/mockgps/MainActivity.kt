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
                onNavigateToSavedLocations = { navController.navigate("saved_locations") }
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
