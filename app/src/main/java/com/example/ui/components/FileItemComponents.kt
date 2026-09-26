package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudFile
import com.example.data.model.CloudFolder
import com.example.data.model.FileCategory
import com.example.ui.theme.KawachAccent
import com.example.ui.theme.KawachError
import com.example.ui.theme.KawachOrangeDark
import com.example.ui.theme.KawachPrimary
import com.example.ui.theme.KawachPrimaryDark
import com.example.ui.theme.KawachSuccess

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
fun FileCard(
    file: CloudFile,
    isGrid: Boolean = false,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val catColor = getCategoryColor(file.category)
    val catIcon = getCategoryIcon(file.category)

    if (isGrid) {
        GlassCard(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .testTag("file_card_grid_${file.messageId}"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(catColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = catIcon,
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(20.dp)
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
                            onDelete = onDelete
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
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
                        CircularProgressIndicator(
                            progress = { file.downloadProgress },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = KawachPrimary
                        )
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
    } else {
        GlassCard(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .testTag("file_card_list_${file.messageId}"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

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
                    CircularProgressIndicator(
                        progress = { file.downloadProgress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = KawachPrimary
                    )
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
                        onDelete = onDelete
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
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (file.hasLocalFile) {
            DropdownMenuItem(
                text = { Text("Open File") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                onClick = { onDismiss(); onOpen() }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                onClick = { onDismiss(); onDownload() }
            )
        }

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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(getCategoryColor(file.category).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(file.category),
                        contentDescription = null,
                        tint = getCategoryColor(file.category),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
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
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (file.hasLocalFile) {
                        Button(
                            onClick = { onDismiss(); onOpen() },
                            colors = ButtonDefaults.buttonColors(containerColor = KawachSuccess),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Open", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { onDismiss(); onDownload() },
                            colors = ButtonDefaults.buttonColors(containerColor = KawachPrimaryDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Download", color = Color.White)
                        }
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
