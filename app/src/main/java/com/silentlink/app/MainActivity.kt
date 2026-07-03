package com.silentlink.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.silentlink.app.model.AppTheme
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.silentlink.app.ads.AdMobManager
import com.silentlink.app.service.AlarmRingService
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.screen.AlarmScreen
import com.silentlink.app.ui.screen.DndScreen
import com.silentlink.app.ui.screen.HomeScreen
import com.silentlink.app.ui.screen.OnboardingScreen
import com.silentlink.app.ui.screen.SettingsScreen
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed
import com.silentlink.app.ui.theme.SilentLinkTheme

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val NAV_ITEMS = listOf(
    NavItem("home", "홈", Icons.Default.Home),
    NavItem("alarm", "알람", Icons.Default.Alarm),
    NavItem("dnd", "방해금지", Icons.Default.Schedule),
    NavItem("settings", "설정", Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdMobManager.initialize(this)
        intent?.getStringExtra("navigate_to")?.let { viewModel.handleNotificationRoute(it) }
        addOnNewIntentListener { newIntent ->
            setIntent(newIntent)
            newIntent.getStringExtra("navigate_to")?.let { viewModel.handleNotificationRoute(it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isOnboarded by viewModel.isOnboarded.collectAsState()

            SilentLinkTheme(appTheme = uiState.theme) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        WindowCompat.getInsetsController(window, view)
                            .isAppearanceLightStatusBars = uiState.theme == AppTheme.LIGHT
                    }
                }
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
    val isAlarmRinging by AlarmRingService.isRinging.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.pendingNavRoute.collect { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
        Column(modifier = Modifier.padding(innerPadding)) {
            if (isAlarmRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DangerRed)
                        .clickable { AlarmRingService.dismiss(context) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏰", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                AlarmRingService.ringingLabel.ifEmpty { "알람" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text("탭하여 알람 끄기", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Text("해제", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(viewModel = viewModel) }
                    composable("alarm") { AlarmScreen(viewModel = viewModel) }
                    composable("dnd") { DndScreen(viewModel = viewModel) }
                    composable("settings") { SettingsScreen(viewModel = viewModel) }
                }
            }
        }
    }
}
