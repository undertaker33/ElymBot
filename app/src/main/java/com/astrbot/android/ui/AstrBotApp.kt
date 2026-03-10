package com.astrbot.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import com.astrbot.android.ui.screen.BotScreen
import com.astrbot.android.ui.screen.ChatScreen
import com.astrbot.android.ui.screen.LogScreen
import com.astrbot.android.ui.screen.PersonaScreen
import com.astrbot.android.ui.screen.ProviderScreen
import com.astrbot.android.ui.screen.SettingsScreen

@Composable
fun AstrBotApp() {
    val navController = rememberNavController()
    val destinations = listOf(
        AppDestination.Chat,
        AppDestination.Bot,
        AppDestination.Providers,
        AppDestination.Personas,
        AppDestination.Logs,
        AppDestination.Settings,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Chat.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Chat.route) { ChatScreen() }
            composable(AppDestination.Bot.route) { BotScreen() }
            composable(AppDestination.Providers.route) { ProviderScreen() }
            composable(AppDestination.Personas.route) { PersonaScreen() }
            composable(AppDestination.Logs.route) { LogScreen() }
            composable(AppDestination.Settings.route) { SettingsScreen() }
        }
    }
}

private sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Chat : AppDestination("chat", "对话", Icons.Outlined.ChatBubbleOutline)
    data object Bot : AppDestination("bot", "机器人", Icons.Outlined.Home)
    data object Providers : AppDestination("providers", "模型", Icons.Outlined.Build)
    data object Personas : AppDestination("personas", "人格", Icons.Outlined.Face)
    data object Logs : AppDestination("logs", "日志", Icons.AutoMirrored.Outlined.List)
    data object Settings : AppDestination("settings", "设置", Icons.Outlined.Settings)
}
