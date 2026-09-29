package com.kawach.cloud.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kawach.cloud.data.model.CloudFile

@Entity(
    tableName = "cloud_files",
    primaryKeys = ["messageId", "userId"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "folderId"]),
        Index(value = ["telegramFileId"])
    ]
)
data class FileEntity(
    val messageId: Long,
    val userId: Long,
    val telegramFileId: Int,
    val remoteFileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val uploadDate: Long,
    val folderId: String = "root",
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val kawachSignature: String = "",
    val thumbnailPath: String? = null
) {
    fun toCloudFile(): CloudFile {
        return CloudFile(
            messageId = messageId,
            telegramFileId = telegramFileId,
            remoteFileId = remoteFileId,
            name = fileName,
            size = fileSize,
            mimeType = mimeType,
            uploadDate = uploadDate,
            folderId = folderId,
            localPath = localPath,
            isDownloaded = isDownloaded,
            kawachTag = kawachSignature,
            thumbnailPath = thumbnailPath
        )
    }

    companion object {
        fun fromCloudFile(file: CloudFile, userId: Long): FileEntity {
            return FileEntity(
                messageId = file.messageId,
                userId = userId,
                telegramFileId = file.telegramFileId,
                remoteFileId = file.remoteFileId,
                fileName = file.name,
                fileSize = file.size,
                mimeType = file.mimeType,
                uploadDate = file.uploadDate,
                folderId = file.folderId,
                localPath = file.localPath,
                isDownloaded = file.isDownloaded,
                kawachSignature = file.kawachTag,
                thumbnailPath = file.thumbnailPath
            )
        }
    }
}
