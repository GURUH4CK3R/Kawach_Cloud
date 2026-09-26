package com.example.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudFile
import com.example.data.model.FileCategory
import com.example.data.model.TelegramAuthState
import com.example.ui.components.ConnectionCard
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FileCard
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MoveFileDialog
import com.example.ui.components.RenameDialog
import com.example.ui.components.UploadProgressCard
import com.example.ui.theme.KawachAccent
import com.example.ui.theme.KawachPrimary
import com.example.ui.theme.KawachPrimaryDark
import com.example.ui.viewmodel.KawachViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: KawachViewModel,
    onNavigateToFiles: () -> Unit,
    onConnectTelegram: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.activeUploadProgress.collectAsState()
    val activeUploadName by viewModel.activeUploadName.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Dialog states
    var selectedFileForDetails by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForDelete by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForRename by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForMove by remember { mutableStateOf<CloudFile?>(null) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(KawachPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = KawachPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Kawach Cloud",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshFiles() },
                        enabled = !isRefreshing && authState is TelegramAuthState.Authenticated,
                        modifier = Modifier.testTag("home_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = KawachPrimary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync files")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Upload Progress Banner
            if (isUploading) {
                item {
                    UploadProgressCard(
                        fileName = activeUploadName ?: "Uploading...",
                        progress = uploadProgress,
                        isUploading = isUploading
                    )
                }
            }

            // Telegram Connection Status Card
            item {
                ConnectionCard(
                    authState = authState,
                    onConnectClick = onConnectTelegram,
                    onDisconnectClick = { viewModel.logout() },
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            // Big Upload File Glass Button
            item {
                GlassButton(
                    text = "Upload File",
                    icon = Icons.Default.CloudUpload,
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = authState is TelegramAuthState.Authenticated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    tag = "home_upload_file_button"
                )
            }

            // Storage Overview
            item {
                val totalBytes = recentFiles.sumOf { it.size }
                val totalFormatted = CloudFile.formatFileSize(totalBytes)

                Text(
                    text = "Storage Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Saved Messages Storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = totalFormatted,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = KawachPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = KawachPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${recentFiles.size} files",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KawachPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val docsCount = recentFiles.count { it.category == FileCategory.DOCUMENTS }
                            val mediaCount = recentFiles.count { it.category == FileCategory.IMAGES || it.category == FileCategory.VIDEOS }
                            val audioCount = recentFiles.count { it.category == FileCategory.AUDIO }
                            val archiveCount = recentFiles.count { it.category == FileCategory.ARCHIVES }

                            CategoryStatChip(icon = Icons.Default.Description, label = "Docs", count = docsCount, color = Color(0xFF34D399))
                            CategoryStatChip(icon = Icons.Default.Image, label = "Media", count = mediaCount, color = Color(0xFF38BDF8))
                            CategoryStatChip(icon = Icons.Default.AudioFile, label = "Audio", count = audioCount, color = Color(0xFFA78BFA))
                            CategoryStatChip(icon = Icons.Default.Archive, label = "Archives", count = archiveCount, color = KawachAccent)
                        }
                    }
                }
            }

            // Recent Files Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (recentFiles.isNotEmpty()) {
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.bodySmall,
                            color = KawachPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { onNavigateToFiles() }
                                .padding(4.dp)
                                .testTag("view_all_files_button")
                        )
                    }
                }
            }

            // Recent Files List or Empty State
            if (recentFiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.CloudUpload,
                        title = "No files yet",
                        description = "Files uploaded to Kawach Cloud are stored directly in your Telegram Saved Messages.",
                        actionButtonText = if (authState is TelegramAuthState.Authenticated) "Upload File" else "Connect Telegram",
                        onActionClick = {
                            if (authState is TelegramAuthState.Authenticated) {
                                filePickerLauncher.launch("*/*")
                            } else {
                                onConnectTelegram()
                            }
                        },
                        actionTag = "home_empty_state_action"
                    )
                }
            } else {
                items(recentFiles.take(10), key = { it.messageId }) { file ->
                    FileCard(
                        file = file,
                        isGrid = false,
                        onClick = { selectedFileForDetails = file },
                        onDownload = { viewModel.downloadFile(file) },
                        onOpen = { viewModel.openFile(context, file) },
                        onShare = { viewModel.shareFile(context, file) },
                        onRename = { selectedFileForRename = file },
                        onMove = { selectedFileForMove = file },
                        onDelete = { selectedFileForDelete = file },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        }
    }

    // Dialogs
    selectedFileForDetails?.let { file ->
        val folderName = allFolders.find { it.id == file.folderId }?.name ?: "All Files"
        FileDetailsDialog(
            file = file,
            folderName = folderName,
            onDismiss = { selectedFileForDetails = null },
            onDownload = { viewModel.downloadFile(file) },
            onOpen = { viewModel.openFile(context, file) },
            onShare = { viewModel.shareFile(context, file) },
            onRename = {
                selectedFileForDetails = null
                selectedFileForRename = file
            },
            onMove = {
                selectedFileForDetails = null
                selectedFileForMove = file
            },
            onDelete = {
                selectedFileForDetails = null
                selectedFileForDelete = file
            }
        )
    }

    selectedFileForDelete?.let { file ->
        DeleteConfirmationDialog(
            fileName = file.name,
            onDismiss = { selectedFileForDelete = null },
            onConfirm = {
                viewModel.deleteFile(file)
                selectedFileForDelete = null
            }
        )
    }

    selectedFileForRename?.let { file ->
        RenameDialog(
            currentName = file.name,
            onDismiss = { selectedFileForRename = null },
            onConfirm = { newName ->
                viewModel.renameFile(file, newName)
                selectedFileForRename = null
            }
        )
    }

    selectedFileForMove?.let { file ->
        MoveFileDialog(
            folders = allFolders,
            currentFolderId = file.folderId,
            onDismiss = { selectedFileForMove = null },
            onFolderSelected = { folderId ->
                viewModel.moveFile(file, folderId)
                selectedFileForMove = null
            }
        )
    }
}

@Composable
private fun CategoryStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
