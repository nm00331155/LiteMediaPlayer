package com.example.litemediaplayer.navigation

import android.util.Base64
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.litemediaplayer.comic.ComicReaderScreen
import com.example.litemediaplayer.R
import com.example.litemediaplayer.comic.ComicShelfScreen
import com.example.litemediaplayer.network.NetworkBrowserScreen
import com.example.litemediaplayer.player.FolderManagerScreen
import com.example.litemediaplayer.player.PlayerPlaybackScreen
import com.example.litemediaplayer.player.PlayerScreen
import com.example.litemediaplayer.settings.SettingsScreen

enum class MainTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    COMIC("comic", R.string.tab_comic, Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
    PLAYER("player", R.string.tab_player, Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
    NETWORK("network", R.string.tab_network, Icons.Outlined.Wifi, Icons.Filled.Wifi),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val tabs = listOf(MainTab.COMIC, MainTab.PLAYER, MainTab.NETWORK, MainTab.SETTINGS)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isReaderRoute = currentDestination?.route?.startsWith("comic_reader") == true
            val isPlaybackRoute = currentDestination?.route?.startsWith("player_playback") == true
            val isFolderRoute = currentDestination?.route?.startsWith("player_folders") == true

            if (!isReaderRoute && !isPlaybackRoute && !isFolderRoute) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == tab.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(text = stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.COMIC.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainTab.COMIC.route) {
                ComicShelfScreen(
                    onOpenBook = { bookId ->
                        navController.navigate("comic_reader/$bookId")
                    }
                )
            }

            composable(MainTab.PLAYER.route) {
                PlayerScreen(
                    onPlayVideo = { videoUri ->
                        val encoded = Base64.encodeToString(
                            videoUri.toByteArray(Charsets.UTF_8),
                            Base64.URL_SAFE or Base64.NO_WRAP
                        )
                        navController.navigate("player_playback/$encoded")
                    },
                    onOpenFolderManager = {
                        navController.navigate("player_folders")
                    }
                )
            }

            composable("player_folders") {
                FolderManagerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLockSettings = {
                        navController.navigate(MainTab.SETTINGS.route)
                    }
                )
            }

            composable(
                route = "player_playback/{videoUri}",
                arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val raw = backStackEntry.arguments?.getString("videoUri") ?: return@composable
                val decodedUri = runCatching {
                    String(
                        Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP),
                        Charsets.UTF_8
                    )
                }.getOrDefault(raw)
                PlayerPlaybackScreen(
                    videoUri = decodedUri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "comic_reader/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
                ComicReaderScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                    onOpenNextBook = { nextId ->
                        navController.navigate("comic_reader/$nextId") {
                            popUpTo("comic_reader/{bookId}") {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(MainTab.NETWORK.route) {
                NetworkBrowserScreen(
                    onPlayVideo = { streamUri ->
                        val encoded = Base64.encodeToString(
                            streamUri.toByteArray(Charsets.UTF_8),
                            Base64.URL_SAFE or Base64.NO_WRAP
                        )
                        navController.navigate("player_playback/$encoded")
                    }
                )
            }

            composable(MainTab.SETTINGS.route) {
                SettingsScreen()
            }
        }
    }
}
