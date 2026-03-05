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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor   = MaterialTheme.colorScheme.primary,
                selectedTextColor   = MaterialTheme.colorScheme.primary,
                indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = "地圖") },
                    label = { Text("地圖") },
                    selected = currentRoute == "map",
                    colors = navItemColors,
                    onClick = {
                        navController.navigate("map") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "位置") },
                    label = { Text("位置") },
                    selected = currentRoute == "saved_locations",
                    colors = navItemColors,
                    onClick = {
                        navController.navigate("saved_locations") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "路線") },
                    label = { Text("路線") },
                    selected = currentRoute == "routes",
                    colors = navItemColors,
                    onClick = {
                        navController.navigate("routes") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "設定") },
                    label = { Text("設定") },
                    selected = currentRoute == "settings",
                    colors = navItemColors,
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "map",
            modifier = Modifier.padding(paddingValues)
        ) {
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
                    onNavigateToSavedLocations = { },
                    onNavigateToRoutes = { },
                    onNavigateToSettings = { }
                )
            }
            composable("saved_locations") { backStackEntry ->
                val viewModel: SavedLocationsViewModel = hiltViewModel(backStackEntry)

                SavedLocationsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { },
                    onLocationSelected = { lat, lng ->
                        navController.getBackStackEntry("map").savedStateHandle.set("selectedLat", lat)
                        navController.getBackStackEntry("map").savedStateHandle.set("selectedLng", lng)
                        navController.navigate("map") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("routes") { backStackEntry ->
                val viewModel: RoutesViewModel = hiltViewModel(backStackEntry)
                RoutesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { },
                    onRouteSelected = { routeId ->
                        navController.getBackStackEntry("map").savedStateHandle.set("selectedRouteId", routeId)
                        navController.navigate("map") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable("settings") { backStackEntry ->
                val viewModel: SettingsViewModel = hiltViewModel(backStackEntry)
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { }
                )
            }
        }
    }
}
