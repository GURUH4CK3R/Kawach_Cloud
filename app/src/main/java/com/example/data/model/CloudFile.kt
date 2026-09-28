package com.example.data.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory(val label: String) {
    ALL("All"),
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    ARCHIVES("Archives"),
    OTHER("Other")
}

data class CloudFile(
    val messageId: Long,
    val telegramFileId: Int,
    val remoteFileId: String = "",
    val name: String,
    val size: Long,
    val mimeType: String,
    val uploadDate: Long,
    val folderId: String = "root",
    val localPath: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val isDownloaded: Boolean = false,
    val kawachTag: String = "",
    val thumbnailPath: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(size)

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            return sdf.format(Date(uploadDate * 1000L))
        }

    val category: FileCategory
        get() {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = mimeType.lowercase(Locale.ROOT)
            return when {
                mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic", "heif", "avif") -> FileCategory.IMAGES
                mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts", "flv") -> FileCategory.VIDEOS
                mime.startsWith("audio/") || ext in listOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "wma") -> FileCategory.AUDIO
                mime == "application/pdf" || ext == "pdf" ||
                    mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") || mime.startsWith("text/") ||
                    ext in listOf("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv", "epub", "md", "json", "xml") -> FileCategory.DOCUMENTS
                mime.contains("zip") || mime.contains("tar") || mime.contains("compressed") ||
                    ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> FileCategory.ARCHIVES
                else -> FileCategory.OTHER
            }
        }

    val isImage: Boolean
        get() = category == FileCategory.IMAGES

    val isVideo: Boolean
        get() = category == FileCategory.VIDEOS

    val hasLocalFile: Boolean
        get() = isDownloaded || (!localPath.isNullOrBlank() && (localPath.startsWith("content://") || File(localPath).exists()))

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, index.toDouble())
            return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
        }
    }
}

data class CloudFolder(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis() / 1000L,
    val fileCount: Int = 0
)
