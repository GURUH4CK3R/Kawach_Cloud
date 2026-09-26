package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.data.model.TelegramAuthState
import com.example.ui.screens.auth.ConnectTelegramScreen
import com.example.ui.screens.files.FilesScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.KawachPrimary
import com.example.ui.viewmodel.KawachViewModel

enum class AppDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    FILES("files", "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

enum class RootScreen {
    SPLASH,
    CONNECT_TELEGRAM,
    MAIN
}

@Composable
fun KawachNavigation(
    viewModel: KawachViewModel
) {
    var rootScreen by remember { mutableStateOf(RootScreen.SPLASH) }
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Listen to messages from ViewModel
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    when (rootScreen) {
        RootScreen.SPLASH -> {
            SplashScreen(
                onSplashFinished = {
                    rootScreen = if (authState is TelegramAuthState.Authenticated) {
                        RootScreen.MAIN
                    } else {
                        RootScreen.CONNECT_TELEGRAM
                    }
                }
            )
        }

        RootScreen.CONNECT_TELEGRAM -> {
            BackHandler(enabled = true) {
                // If user is already authenticated and presses back from reconnect screen, go back to main
                if (authState is TelegramAuthState.Authenticated) {
                    rootScreen = RootScreen.MAIN
                }
            }

            ConnectTelegramScreen(
                viewModel = viewModel,
                onConnected = {
                    rootScreen = RootScreen.MAIN
                }
            )
        }

        RootScreen.MAIN -> {
            BackHandler(enabled = currentDestination != AppDestination.HOME) {
                currentDestination = AppDestination.HOME
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        AppDestination.values().forEach { destination ->
                            val isSelected = currentDestination == destination
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.title
                                    )
                                },
                                label = { Text(destination.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = KawachPrimary,
                                    selectedTextColor = KawachPrimary,
                                    indicatorColor = KawachPrimary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag("nav_item_${destination.route}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedContent(
                        targetState = currentDestination,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen_transition"
                    ) { destination ->
                        when (destination) {
                            AppDestination.HOME -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToFiles = { currentDestination = AppDestination.FILES },
                                    onConnectTelegram = { rootScreen = RootScreen.CONNECT_TELEGRAM }
                                )
                            }
                            AppDestination.FILES -> {
                                FilesScreen(
                                    viewModel = viewModel,
                                    onConnectTelegram = { rootScreen = RootScreen.CONNECT_TELEGRAM }
                                )
                            }
                            AppDestination.SETTINGS -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onConnectTelegram = { rootScreen = RootScreen.CONNECT_TELEGRAM }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
