package com.example.mockgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mockgps.ui.map.MapScreen
import com.example.mockgps.ui.map.MapViewModel
import com.example.mockgps.ui.savedlocations.SavedLocationsScreen
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
    // Sharing MapViewModel might be tricky with simple navigation if scoping isn't handled by graph.
    // For now, MapScreen has its own ViewModel instance via hiltViewModel().
    // SavedLocationsScreen has its own ViewModel.
    // To pass result back, we use SavedStateHandle or just relying on passing arguments if we were doing that.
    // But requirement says: "Row 點擊：回 MapScreen 並置中至該座標".
    // We can pass lat/lng as route arguments to MapScreen, OR use navController.previousBackStackEntry.savedStateHandle.

    // However, MapScreen is the "home". When we go back from SavedLocationsScreen, we just popBackStack.
    // To communicate the selected location, we can set a result in savedStateHandle.

    NavHost(navController = navController, startDestination = "map") {
        composable("map") { backStackEntry ->
            // Check for returned result
            val selectedLat = backStackEntry.savedStateHandle.get<Double>("selectedLat")
            val selectedLng = backStackEntry.savedStateHandle.get<Double>("selectedLng")

            // Clear the result after reading
            if (selectedLat != null && selectedLng != null) {
                backStackEntry.savedStateHandle.remove<Double>("selectedLat")
                backStackEntry.savedStateHandle.remove<Double>("selectedLng")
                // We need to tell the ViewModel to move camera.
                // But MapScreen gets a new ViewModel instance or existing one?
                // hiltViewModel() is scoped to the NavBackStackEntry (the route).
                // "map" route entry persists while we are in "saved_locations".
                // So when we pop back, we get the same instance.
            }

            val viewModel: MapViewModel = hiltViewModel()
            // Side effect to update camera if result exists
            // We can pass this via a LaunchedEffect inside MapScreen or here.
            // Let's pass it to MapScreen as a parameter? Or better, handle it here by calling VM.

            if (selectedLat != null && selectedLng != null) {
                 viewModel.onCameraMove(com.google.android.gms.maps.model.LatLng(selectedLat, selectedLng))
            }

            MapScreen(
                viewModel = viewModel,
                onNavigateToSavedLocations = { navController.navigate("saved_locations") }
            )
        }
        composable("saved_locations") {
            SavedLocationsScreen(
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
