package com.kawach.cloud.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kawach.cloud.data.model.TelegramAuthState
import com.kawach.cloud.ui.components.ImageViewerDialog
import com.kawach.cloud.ui.components.VideoPlayerDialog
import com.kawach.cloud.ui.screens.auth.ConnectTelegramScreen
import com.kawach.cloud.ui.screens.files.FilesScreen
import com.kawach.cloud.ui.screens.home.HomeScreen
import com.kawach.cloud.ui.screens.settings.SettingsScreen
import com.kawach.cloud.ui.screens.splash.SplashScreen
import com.kawach.cloud.ui.theme.KawachPrimary
import com.kawach.cloud.ui.viewmodel.KawachViewModel

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
    val context = LocalContext.current

    val activePreviewFile by viewModel.activePreviewFile.collectAsState()
    val previewLocalFile by viewModel.previewLocalFile.collectAsState()
    val previewLoading by viewModel.previewLoading.collectAsState()
    val previewProgress by viewModel.previewProgress.collectAsState()
    val previewError by viewModel.previewError.collectAsState()

    // Listen to messages from ViewModel
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Auto-navigate to login when session is disconnected
    LaunchedEffect(authState) {
        if (authState !is TelegramAuthState.Authenticated && rootScreen == RootScreen.MAIN) {
            rootScreen = RootScreen.CONNECT_TELEGRAM
            currentDestination = AppDestination.HOME
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AppDestination.values().forEach { destination ->
                                    val isSelected = currentDestination == destination
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(24.dp))
                                            .clickable { currentDestination = destination }
                                            .testTag("nav_item_${destination.route}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                                contentDescription = destination.title,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            AnimatedVisibility(
                                                visible = isSelected,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally()
                                            ) {
                                                Row {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = destination.title,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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

    // In-app media preview (Photo Viewer & Video Player)
    activePreviewFile?.let { file ->
        if (file.isImage) {
            ImageViewerDialog(
                file = file,
                localFile = previewLocalFile,
                isLoading = previewLoading,
                progress = previewProgress,
                errorMessage = previewError,
                onDismiss = { viewModel.closePreview() },
                onRetry = { viewModel.retryPreview() },
                onDownload = { viewModel.downloadFile(file) },
                onShare = { viewModel.shareFile(context, file) }
            )
        } else if (file.isVideo) {
            VideoPlayerDialog(
                file = file,
                localFile = previewLocalFile,
                isLoading = previewLoading,
                downloadProgress = previewProgress,
                errorMessage = previewError,
                onDismiss = { viewModel.closePreview() },
                onRetry = { viewModel.retryPreview() },
                onDownload = { viewModel.downloadFile(file) },
                onShare = { viewModel.shareFile(context, file) }
            )
        }
    }
}
