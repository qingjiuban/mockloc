package com.mockloc.assistant.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mockloc.assistant.ui.guide.GuideScreen
import com.mockloc.assistant.ui.home.HomeScreen
import com.mockloc.assistant.ui.home.HomeViewModel
import com.mockloc.assistant.ui.map.MapPickerScreen
import com.mockloc.assistant.ui.places.PlacesScreen
import com.mockloc.assistant.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "首页")
    data object Places : Screen("places", "常用地点")
    data object Guide : Screen("guide", "使用指南")
    data object Settings : Screen("settings", "设置")
    data object MapPicker : Screen("map_picker", "地图选点")
}

val bottomNavItems = listOf(
    Screen.Home to Icons.Default.Home,
    Screen.Places to Icons.Default.Favorite,
    Screen.Guide to Icons.Default.Info,
    Screen.Settings to Icons.Default.Settings,
)

@Composable
fun AppNavigation(viewModel: HomeViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { (screen, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenMapPicker = {
                        navController.navigate(Screen.MapPicker.route)
                    },
                )
            }
            composable(Screen.Places.route) {
                PlacesScreen(viewModel = viewModel)
            }
            composable(Screen.Guide.route) {
                GuideScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(Screen.MapPicker.route) {
                MapPickerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
