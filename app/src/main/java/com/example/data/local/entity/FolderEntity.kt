package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.CloudFolder

@Entity(
    tableName = "cloud_folders",
    indices = [
        Index(value = ["userId"])
    ]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val userId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis() / 1000L
) {
    fun toCloudFolder(count: Int = 0): CloudFolder {
        return CloudFolder(
            id = id,
            name = name,
            createdAt = createdAt,
            fileCount = count
        )
    }
}
