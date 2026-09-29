package com.kawach.cloud.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kawach.cloud.data.model.CloudFile
import com.kawach.cloud.data.model.CloudFolder
import com.kawach.cloud.data.model.FileCategory
import com.kawach.cloud.ui.theme.KawachAccent
import com.kawach.cloud.ui.theme.KawachError
import com.kawach.cloud.ui.theme.KawachOrangeDark
import com.kawach.cloud.ui.theme.KawachPrimary
import com.kawach.cloud.ui.theme.KawachPrimaryDark
import com.kawach.cloud.ui.theme.KawachSuccess

fun getCategoryIcon(category: FileCategory): ImageVector {
    return when (category) {
        FileCategory.IMAGES -> Icons.Default.Image
        FileCategory.VIDEOS -> Icons.Default.VideoFile
        FileCategory.AUDIO -> Icons.Default.AudioFile
        FileCategory.DOCUMENTS -> Icons.Default.Description
        FileCategory.ARCHIVES -> Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

fun getCategoryColor(category: FileCategory): Color {
    return when (category) {
        FileCategory.IMAGES -> Color(0xFF38BDF8)
        FileCategory.VIDEOS -> Color(0xFFF472B6)
        FileCategory.AUDIO -> Color(0xFFA78BFA)
        FileCategory.DOCUMENTS -> Color(0xFF34D399)
        FileCategory.ARCHIVES -> KawachAccent
        else -> KawachPrimary
    }
}

@Composable
fun FileThumbnail(
    file: CloudFile,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val catColor = getCategoryColor(file.category)
    val catIcon = getCategoryIcon(file.category)
    val thumbModel = file.thumbnailPath ?: if (file.category == FileCategory.IMAGES && file.hasLocalFile) file.localPath else null

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(catColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (!thumbModel.isNullOrBlank()) {
            AsyncImage(
                model = thumbModel,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
            if (file.category == FileCategory.VIDEOS) {
                Box(
                    modifier = Modifier
                        .size((size.value * 0.45f).dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size((size.value * 0.32f).dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = catIcon,
                contentDescription = null,
                tint = catColor,
                modifier = Modifier.size((size.value * 0.52f).dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCard(
    file: CloudFile,
    isGrid: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    if (isGrid) {
        val isMedia = file.isImage || file.isVideo

        if (isMedia) {
            // Big photo thumbnail card for images and videos
            GlassCard(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelect?.invoke()
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            if (onLongClick != null) {
                                onLongClick()
                            } else {
                                onToggleSelect?.invoke()
                            }
                        }
                    )
                    .testTag("file_card_grid_${file.messageId}"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                ) {
                    val thumbModel = file.thumbnailPath ?: if (file.hasLocalFile) file.localPath else null

                    if (!thumbModel.isNullOrBlank()) {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val catColor = getCategoryColor(file.category)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(catColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(file.category),
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    // Bottom gradient overlay for clear contrast
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    )

                    // Video Play Icon Badge
                    if (file.isVideo) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Selection Checkmark (Top-Left)
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) KawachPrimary else Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Selected" else "Not selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Options Menu Button (Top-Right)
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .testTag("file_menu_button_${file.messageId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FileDropdownMenu(
                            expanded = showMenu,
                            file = file,
                            onDismiss = { showMenu = false },
                            onDownload = onDownload,
                            onOpen = onOpen,
                            onShare = onShare,
                            onRename = onRename,
                            onMove = onMove,
                            onDelete = onDelete,
                            onShowDetails = onShowDetails
                        )
                    }

                    // Bottom info bar (Size pill on left, Download status on right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = file.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (file.isDownloading) {
                            CircularProgressIndicator(
                                progress = { file.downloadProgress },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else if (file.hasLocalFile) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = KawachSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Document, Archive, Audio grid card
            GlassCard(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelect?.invoke()
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            if (onLongClick != null) {
                                onLongClick()
                            } else {
                                onToggleSelect?.invoke()
                            }
                        }
                    )
                    .testTag("file_card_grid_${file.messageId}"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) KawachPrimary else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            FileThumbnail(
                                file = file,
                                size = 42.dp
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(28.dp).testTag("file_menu_button_${file.messageId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FileDropdownMenu(
                                expanded = showMenu,
                                file = file,
                                onDismiss = { showMenu = false },
                                onDownload = onDownload,
                                onOpen = onOpen,
                                onShare = onShare,
                                onRename = onRename,
                                onMove = onMove,
                                onDelete = onDelete,
                                onShowDetails = onShowDetails
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (file.isDownloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress = { file.downloadProgress },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = KawachPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${(file.downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KawachPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (file.hasLocalFile) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = KawachSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        GlassCard(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelect?.invoke()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (onLongClick != null) {
                            onLongClick()
                        } else {
                            onToggleSelect?.invoke()
                        }
                    }
                )
                .testTag("file_card_list_${file.messageId}"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) KawachPrimary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                FileThumbnail(
                    file = file,
                    size = 46.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = file.formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = file.formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (file.isDownloading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { file.downloadProgress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = KawachPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(file.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = KawachPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (file.hasLocalFile) {
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.size(32.dp).testTag("open_file_btn_${file.messageId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Open File",
                            tint = KawachSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(32.dp).testTag("download_file_btn_${file.messageId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download File",
                            tint = KawachPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp).testTag("file_options_btn_${file.messageId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FileDropdownMenu(
                        expanded = showMenu,
                        file = file,
                        onDismiss = { showMenu = false },
                        onDownload = onDownload,
                        onOpen = onOpen,
                        onShare = onShare,
                        onRename = onRename,
                        onMove = onMove,
                        onDelete = onDelete,
                        onShowDetails = onShowDetails
                    )
                }
            }
        }
    }
}

@Composable
fun FileDropdownMenu(
    expanded: Boolean,
    file: CloudFile,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (file.isImage) "View Photo" else if (file.isVideo) "Play Video" else "Open File") },
            leadingIcon = {
                Icon(
                    imageVector = if (file.isImage) Icons.Default.Image else if (file.isVideo) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null
                )
            },
            onClick = { onDismiss(); onOpen() }
        )

        if (onShowDetails != null) {
            DropdownMenuItem(
                text = { Text("Details & Info") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { onDismiss(); onShowDetails() }
            )
        }

        DropdownMenuItem(
            text = { Text(if (file.hasLocalFile) "Download to Device" else "Download") },
            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
            onClick = { onDismiss(); onDownload() }
        )

        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = { onDismiss(); onShare() }
        )

        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
            onClick = { onDismiss(); onRename() }
        )

        DropdownMenuItem(
            text = { Text("Move to Folder") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
            onClick = { onDismiss(); onMove() }
        )

        DropdownMenuItem(
            text = { Text("Delete", color = KawachError) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = KawachError) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

@Composable
fun FileDetailsDialog(
    file: CloudFile,
    folderName: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnail(file = file, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "File Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                DetailRow(label = "Size", value = file.formattedSize)
                DetailRow(label = "Date", value = file.formattedDate)
                DetailRow(label = "Folder", value = folderName)
                DetailRow(label = "MIME Type", value = file.mimeType)
                DetailRow(
                    label = "Storage",
                    value = if (file.hasLocalFile) "Saved Messages + Local Cache" else "Telegram Saved Messages"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (file.hasLocalFile || file.isImage || file.isVideo) {
                        Button(
                            onClick = { onDismiss(); onOpen() },
                            colors = ButtonDefaults.buttonColors(containerColor = KawachSuccess),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (file.isImage) "View" else if (file.isVideo) "Play" else "Open",
                                color = Color.White
                            )
                        }
                    }

                    Button(
                        onClick = { onDismiss(); onDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Download")
                    }

                    OutlinedButton(
                        onClick = { onDismiss(); onShare() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Share")
                    }

                    OutlinedButton(
                        onClick = { onDismiss(); onDelete() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KawachError),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Delete")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MoveFileDialog(
    folders: List<CloudFolder>,
    currentFolderId: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move File to Folder", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                folders.forEach { folder ->
                    val isSelected = folder.id == currentFolderId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onFolderSelected(folder.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) KawachPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isSelected) KawachPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    title: String = "Rename File",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) onConfirm(newName)
                },
                enabled = newName.isNotBlank() && newName != currentName,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("folder_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) onConfirm(folderName)
                },
                enabled = folderName.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete from Saved Messages?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Are you sure you want to permanently delete \"$fileName\"? This will remove the file from your Telegram Saved Messages.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KawachError),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
