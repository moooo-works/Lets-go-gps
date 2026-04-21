package com.moooo_works.letsgogps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moooo_works.letsgogps.ui.map.MapScreen
import com.moooo_works.letsgogps.ui.map.MapViewModel
import com.moooo_works.letsgogps.ui.map.SearchViewModel
import com.moooo_works.letsgogps.ui.routes.RoutesScreen
import com.moooo_works.letsgogps.ui.routes.RoutesViewModel
import com.moooo_works.letsgogps.ui.savedlocations.SavedLocationsScreen
import com.moooo_works.letsgogps.ui.savedlocations.SavedLocationsViewModel
import com.moooo_works.letsgogps.ui.settings.SettingsScreen
import com.moooo_works.letsgogps.ui.settings.SettingsViewModel
import com.moooo_works.letsgogps.ui.theme.MockGpsTheme
import android.os.LocaleList
import com.moooo_works.letsgogps.ui.theme.ThemePreference
import dagger.hilt.android.AndroidEntryPoint

fun applyLocale(base: Context): Context {
    val prefs = base.getSharedPreferences("mockgps_prefs", Context.MODE_PRIVATE)
    val langCode = prefs.getString("language_pref", "") ?: ""
    if (langCode.isEmpty()) return base
    val locale = java.util.Locale.forLanguageTag(langCode)
    val config = base.resources.configuration
    config.setLocale(locale)
    config.setLocales(LocaleList(locale))
    return base.createConfigurationContext(config)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { getSharedPreferences("mockgps_prefs", Context.MODE_PRIVATE) }
            val themePreference = remember {
                val saved = prefs.getString("theme_pref", ThemePreference.SYSTEM.name)
                mutableStateOf(ThemePreference.valueOf(saved ?: ThemePreference.SYSTEM.name))
            }
            MockGpsTheme(themePreference = themePreference.value) {
                val view = LocalView.current
                val darkTheme = when (themePreference.value) {
                    ThemePreference.DARK -> true
                    ThemePreference.LIGHT -> false
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                }
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        themePreference = themePreference.value,
                        onThemeChange = { newPref ->
                            themePreference.value = newPref
                            prefs.edit().putString("theme_pref", newPref.name).apply()
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun AppNavigation(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {}
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor   = MaterialTheme.colorScheme.primary,
                selectedTextColor   = MaterialTheme.colorScheme.primary,
                indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Place, contentDescription = stringResource(R.string.nav_map)) },
                    label = { Text(stringResource(R.string.nav_map)) },
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
                    icon = { Icon(Icons.Default.List, contentDescription = stringResource(R.string.nav_saved_locations)) },
                    label = { Text(stringResource(R.string.nav_saved_locations)) },
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
                    icon = { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.nav_routes)) },
                    label = { Text(stringResource(R.string.nav_routes)) },
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
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                    label = { Text(stringResource(R.string.nav_settings)) },
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
                val searchViewModel: SearchViewModel = hiltViewModel(backStackEntry)
                val activity = LocalContext.current as android.app.Activity

                LaunchedEffect(viewModel) {
                    viewModel.triggerReview.collect {
                        val manager = ReviewManagerFactory.create(activity)
                        manager.requestReviewFlow().addOnCompleteListener { request ->
                            if (request.isSuccessful) {
                                manager.launchReviewFlow(activity, request.result)
                            }
                        }
                    }
                }

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
                    searchViewModel = searchViewModel,
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
                    onNavigateBack = { },
                    themePreference = themePreference,
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}
