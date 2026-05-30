package com.silentlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.silentlink.app.ads.AdMobManager
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.screen.DndScreen
import com.silentlink.app.ui.screen.HomeScreen
import com.silentlink.app.ui.screen.OnboardingScreen
import com.silentlink.app.ui.screen.SettingsScreen
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.SilentLinkTheme

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val NAV_ITEMS = listOf(
    NavItem("home", "홈", Icons.Default.Home),
    NavItem("dnd", "방해금지", Icons.Default.Schedule),
    NavItem("settings", "설정", Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMobManager.initialize(this)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isOnboarded by viewModel.isOnboarded.collectAsState()

            SilentLinkTheme(appTheme = uiState.theme) {
                if (!isOnboarded) {
                    OnboardingScreen(viewModel = viewModel)
                } else {
                    MainNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val colors = MaterialTheme.colorScheme
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            NavigationBar(
                containerColor = colors.surface,
                contentColor = AccentBlue
            ) {
                NAV_ITEMS.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) AccentBlue else colors.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                color = if (selected) AccentBlue else colors.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = AccentBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { HomeScreen(viewModel = viewModel) }
                composable("dnd") { DndScreen(viewModel = viewModel) }
                composable("settings") { SettingsScreen(viewModel = viewModel) }
            }
        }
    }
}
