package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.CloudFile

@Entity(
    tableName = "cloud_files",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "folderId"]),
        Index(value = ["telegramFileId"])
    ]
)
data class FileEntity(
    @PrimaryKey val messageId: Long,
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
    val kawachSignature: String = ""
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
            kawachTag = kawachSignature
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
                kawachSignature = file.kawachTag
            )
        }
    }
}
