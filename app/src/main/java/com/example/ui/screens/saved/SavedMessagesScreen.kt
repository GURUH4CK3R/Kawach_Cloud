package com.example.ui.screens.saved

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudFile
import com.example.data.model.FileCategory
import com.example.data.model.TelegramAuthState
import com.example.ui.components.EmptyState
import com.example.ui.components.FileCard
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MoveFileDialog
import com.example.ui.theme.KawachAccent
import com.example.ui.theme.KawachPrimary
import com.example.ui.theme.KawachPrimaryDark
import com.example.ui.viewmodel.KawachViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessagesScreen(
    viewModel: KawachViewModel,
    onConnectTelegram: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    val savedFiles by viewModel.filteredSavedMessages.collectAsState()
    val allRawSavedFiles by viewModel.savedMessagesFiles.collectAsState()
    val isLoading by viewModel.savedMessagesLoading.collectAsState()
    val isRefreshing by viewModel.savedMessagesRefreshing.collectAsState()
    val searchQuery by viewModel.savedMessagesSearchQuery.collectAsState()
    val selectedCategory by viewModel.savedMessagesCategory.collectAsState()
    val hasMore by viewModel.savedMessagesHasMore.collectAsState()
    val errorMessage by viewModel.savedMessagesError.collectAsState()

    val selectedFileIds by viewModel.savedMessagesSelectedIds.collectAsState()
    val isSelectionMode by viewModel.isSavedMessagesSelectionMode.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var selectedFileForDetails by remember { mutableStateOf<CloudFile?>(null) }
    var selectedFileForImport by remember { mutableStateOf<CloudFile?>(null) }
    val allFolders by viewModel.allFolders.collectAsState()

    // Fetch initial list when entering screen
    LaunchedEffect(authState) {
        if (authState is TelegramAuthState.Authenticated && allRawSavedFiles.isEmpty()) {
            viewModel.fetchSavedMessages(refresh = true)
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSavedMessagesSelection()
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Pagination trigger when scrolling near bottom
    val shouldLoadMoreList by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 5
        }
    }

    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMoreList, shouldLoadMoreGrid) {
        if ((shouldLoadMoreList || shouldLoadMoreGrid) && hasMore && !isLoading && !isRefreshing) {
            viewModel.loadMoreSavedMessages()
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSavedMessagesSelection() },
                            modifier = Modifier.testTag("saved_exit_selection_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedFileIds.size} selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedFileIds.size == savedFiles.size && savedFiles.isNotEmpty()) {
                                    viewModel.clearSavedMessagesSelection()
                                } else {
                                    viewModel.selectAllSavedMessages(savedFiles)
                                }
                            },
                            modifier = Modifier.testTag("saved_select_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = if (selectedFileIds.size == savedFiles.size) "Deselect All" else "Select All"
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedFileIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable(enabled = selectedFileIds.isNotEmpty()) {
                                    viewModel.downloadSelectedSavedMessages()
                                }
                                .testTag("saved_download_selected_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Download selected",
                                    tint = if (selectedFileIds.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Download (${selectedFileIds.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedFileIds.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    )
                )
            } else {
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
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = KawachPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Saved Messages",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Telegram MTProto TDLib",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KawachPrimary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar && searchQuery.isNotBlank()) {
                                    viewModel.setSavedMessagesSearchQuery("")
                                }
                            },
                            modifier = Modifier.testTag("saved_toggle_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search Saved Messages")
                        }

                        IconButton(
                            onClick = { viewModel.fetchSavedMessages(refresh = true) },
                            enabled = !isRefreshing && authState is TelegramAuthState.Authenticated,
                            modifier = Modifier.testTag("saved_refresh_button")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = KawachPrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync Saved Messages")
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleSelectionMode() },
                            enabled = savedFiles.isNotEmpty(),
                            modifier = Modifier.testTag("saved_multiselect_button")
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = "Multi-select")
                        }

                        IconButton(
                            onClick = { viewModel.toggleGridView() },
                            modifier = Modifier.testTag("saved_toggle_view_mode_button")
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
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar (expandable)
            AnimatedVisibility(visible = showSearchBar) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSavedMessagesSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("saved_messages_search_input"),
                        placeholder = { Text("Search files in Saved Messages...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = KawachPrimary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSavedMessagesSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KawachPrimary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            // Chat Banner & Category Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Header Banner Card
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(KawachPrimary, KawachAccent)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Telegram Cloud Storage",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${allRawSavedFiles.size} files detected in chat",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KawachPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Unlimited",
                                style = MaterialTheme.typography.labelMedium,
                                color = KawachPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        FileCategory.ALL to "All",
                        FileCategory.DOCUMENTS to "Docs",
                        FileCategory.IMAGES to "Photos",
                        FileCategory.VIDEOS to "Videos",
                        FileCategory.AUDIO to "Audio",
                        FileCategory.ARCHIVES to "Archives"
                    )

                    categories.forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        val count = if (cat == FileCategory.ALL) {
                            allRawSavedFiles.size
                        } else {
                            allRawSavedFiles.count { it.category == cat }
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSavedMessagesCategory(cat) },
                            label = {
                                Text(
                                    text = if (count > 0) "$label ($count)" else label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                val icon = when (cat) {
                                    FileCategory.ALL -> Icons.Default.Cloud
                                    FileCategory.DOCUMENTS -> Icons.Default.Description
                                    FileCategory.IMAGES -> Icons.Default.Image
                                    FileCategory.VIDEOS -> Icons.Default.VideoFile
                                    FileCategory.AUDIO -> Icons.Default.AudioFile
                                    FileCategory.ARCHIVES -> Icons.Default.Archive
                                    FileCategory.OTHER -> Icons.Default.Storage
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KawachPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("saved_filter_chip_${cat.name.lowercase()}")
                        )
                    }
                }
            }

            // Main Content Area
            if (authState !is TelegramAuthState.Authenticated) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Cloud,
                        title = "Telegram Not Connected",
                        description = "Sign in to your Telegram account to fetch and browse your Saved Messages files.",
                        actionButtonText = "Connect Telegram",
                        onActionClick = onConnectTelegram,
                        actionTag = "saved_connect_telegram_btn"
                    )
                }
            } else if (savedFiles.isEmpty() && !isLoading && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Bookmark,
                        title = if (searchQuery.isNotBlank()) "No matching files" else "No Saved Messages files",
                        description = if (searchQuery.isNotBlank()) {
                            "No files in Saved Messages matched \"$searchQuery\"."
                        } else {
                            "Send or forward documents, photos, and videos to your Telegram Saved Messages chat, then pull down to sync."
                        },
                        actionButtonText = if (searchQuery.isNotBlank()) "Clear Search" else "Refresh Files",
                        onActionClick = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.setSavedMessagesSearchQuery("")
                            } else {
                                viewModel.fetchSavedMessages(refresh = true)
                            }
                        },
                        actionTag = "saved_empty_action_btn"
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isGridView) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("saved_messages_grid_view")
                        ) {
                            items(savedFiles, key = { it.messageId }) { file ->
                                val isSelected = selectedFileIds.contains(file.messageId)
                                FileCard(
                                    file = file,
                                    isGrid = true,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { viewModel.toggleSavedMessageSelection(file.messageId) },
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSavedMessageSelection(file.messageId)
                                        } else if (file.isImage) {
                                            viewModel.openImagePreview(file)
                                        } else if (file.isVideo) {
                                            viewModel.openVideoPlayer(file)
                                        } else {
                                            selectedFileForDetails = file
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSavedMessageSelection(file.messageId)
                                    },
                                    onDownload = { viewModel.downloadFile(file) },
                                    onOpen = { viewModel.openFile(context, file) },
                                    onShare = { viewModel.shareFile(context, file) },
                                    onRename = {},
                                    onMove = { selectedFileForImport = file },
                                    onDelete = {},
                                    onShowDetails = { selectedFileForDetails = file }
                                )
                            }

                            if (isLoading && !isRefreshing) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = KawachPrimary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("saved_messages_list_view")
                        ) {
                            items(savedFiles, key = { it.messageId }) { file ->
                                val isSelected = selectedFileIds.contains(file.messageId)
                                FileCard(
                                    file = file,
                                    isGrid = false,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onToggleSelect = { viewModel.toggleSavedMessageSelection(file.messageId) },
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSavedMessageSelection(file.messageId)
                                        } else if (file.isImage) {
                                            viewModel.openImagePreview(file)
                                        } else if (file.isVideo) {
                                            viewModel.openVideoPlayer(file)
                                        } else {
                                            selectedFileForDetails = file
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSavedMessageSelection(file.messageId)
                                    },
                                    onDownload = { viewModel.downloadFile(file) },
                                    onOpen = { viewModel.openFile(context, file) },
                                    onShare = { viewModel.shareFile(context, file) },
                                    onRename = {},
                                    onMove = { selectedFileForImport = file },
                                    onDelete = {},
                                    onShowDetails = { selectedFileForDetails = file },
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }

                            if (isLoading && !isRefreshing) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = KawachPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isRefreshing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = KawachPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Fetching Saved Messages...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Details Dialog
    selectedFileForDetails?.let { file ->
        FileDetailsDialog(
            file = file,
            folderName = "Saved Messages",
            onDismiss = { selectedFileForDetails = null },
            onDownload = {
                selectedFileForDetails = null
                viewModel.downloadFile(file)
            },
            onOpen = {
                selectedFileForDetails = null
                viewModel.openFile(context, file)
            },
            onShare = {
                selectedFileForDetails = null
                viewModel.shareFile(context, file)
            },
            onRename = {},
            onMove = {
                selectedFileForDetails = null
                selectedFileForImport = file
            },
            onDelete = {}
        )
    }

    // Save / Import to Kawach Cloud Folder Dialog
    selectedFileForImport?.let { file ->
        MoveFileDialog(
            folders = allFolders,
            currentFolderId = file.folderId,
            onDismiss = { selectedFileForImport = null },
            onFolderSelected = { targetFolderId ->
                selectedFileForImport = null
                viewModel.importSavedMessageToFolder(file, targetFolderId)
            }
        )
    }
}
