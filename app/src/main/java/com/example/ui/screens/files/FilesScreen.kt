package com.example.ui.screens.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FileCard
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.MoveFileDialog
import com.example.ui.components.RenameDialog
import com.example.ui.components.UploadProgressCard
import com.example.ui.theme.KawachPrimary
import com.example.ui.theme.KawachPrimaryDark
import com.example.ui.viewmodel.KawachViewModel
import com.example.ui.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: KawachViewModel,
    onConnectTelegram: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val files by viewModel.filteredFiles.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.activeUploadProgress.collectAsState()
    val activeUploadName by viewModel.activeUploadName.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Dialog states
    var selectedFileForDetails by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForDelete by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForRename by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForMove by remember { mutableStateOf<CloudFile?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Dropdown menus
    var showSortMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

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
                    Text(
                        text = "Files",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshFiles() },
                        enabled = !isRefreshing && authState is TelegramAuthState.Authenticated,
                        modifier = Modifier.testTag("files_refresh_button")
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

                    IconButton(
                        onClick = { viewModel.toggleGridView() },
                        modifier = Modifier.testTag("toggle_view_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) "List view" else "Grid view"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (authState is TelegramAuthState.Authenticated) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = KawachPrimaryDark,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("files_upload_fab")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Upload File")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search files in Saved Messages...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = KawachPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KawachPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("file_search_input")
            )

            // Folders Horizontal Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All Files" Chip
                FolderChip(
                    name = "All Files",
                    isSelected = selectedFolderId == "all",
                    onClick = { viewModel.setSelectedFolder("all") }
                )

                folders.forEach { folder ->
                    Spacer(modifier = Modifier.width(8.dp))
                    FolderChip(
                        name = "${folder.name} (${folder.fileCount})",
                        isSelected = selectedFolderId == folder.id,
                        onClick = { viewModel.setSelectedFolder(folder.id) }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Add Folder Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clickable { showCreateFolderDialog = true }
                        .testTag("add_folder_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Folder",
                            modifier = Modifier.size(16.dp),
                            tint = KawachPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Folder",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Controls Row: Category Filter and Sort
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Filter Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable { showCategoryMenu = true }
                            .testTag("category_filter_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp), tint = KawachPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedCategory.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        FileCategory.values().forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = {
                                    viewModel.setSelectedCategory(category)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                // Sort Dropdown
                Box {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable { showSortMenu = true }
                            .testTag("sort_filter_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp), tint = KawachPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sortOption.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.values().forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    viewModel.setSortOption(sort)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Upload Progress if active
            if (isUploading) {
                UploadProgressCard(
                    fileName = activeUploadName ?: "Uploading...",
                    progress = uploadProgress,
                    isUploading = isUploading,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Files Content: List, Grid, or Empty State
            if (files.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Folder,
                    title = if (searchQuery.isNotEmpty()) "No matching files" else "No files in this folder",
                    description = if (searchQuery.isNotEmpty()) "Try searching for a different name" else "Upload documents, photos, audio, or archives to your Telegram Saved Messages.",
                    actionButtonText = if (authState is TelegramAuthState.Authenticated) "Upload File" else "Connect Telegram",
                    onActionClick = {
                        if (authState is TelegramAuthState.Authenticated) {
                            filePickerLauncher.launch("*/*")
                        } else {
                            onConnectTelegram()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    actionTag = "files_empty_state_action"
                )
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.messageId }) { file ->
                        FileCard(
                            file = file,
                            isGrid = true,
                            onClick = { selectedFileForDetails = file },
                            onDownload = { viewModel.downloadFile(file) },
                            onOpen = { viewModel.openFile(context, file) },
                            onShare = { viewModel.shareFile(context, file) },
                            onRename = { selectedFileForRename = file },
                            onMove = { selectedFileForMove = file },
                            onDelete = { selectedFileForDelete = file }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(files, key = { it.messageId }) { file ->
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
    }

    // Dialogs
    selectedFileForDetails?.let { file ->
        val folderName = folders.find { it.id == file.folderId }?.name ?: "All Files"
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
            folders = folders,
            currentFolderId = file.folderId,
            onDismiss = { selectedFileForMove = null },
            onFolderSelected = { folderId ->
                viewModel.moveFile(file, folderId)
                selectedFileForMove = null
            }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                viewModel.createFolder(folderName)
                showCreateFolderDialog = false
            }
        )
    }
}

@Composable
private fun FolderChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) KawachPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("folder_chip_${name.take(6)}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) Color.White else KawachPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
