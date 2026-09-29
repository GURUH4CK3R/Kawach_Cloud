package com.kawach.cloud.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class SavedDownloadResult(
    val uri: Uri,
    val finalFileName: String,
    val mimeType: String,
    val size: Long,
    val localPath: String?
)

object DownloadStorageManager {

    /**
     * Copies a completed source file into the user-facing public Android Downloads directory.
     * Uses MediaStore.Downloads for modern Android (API 29+) and public Downloads directory
     * for older versions, resolving name collisions safely with "(1)", "(2)", etc.
     */
    fun saveToPublicDownloads(
        context: Context,
        sourceFile: File,
        desiredFileName: String,
        mimeType: String
    ): SavedDownloadResult {
        if (!sourceFile.exists() || sourceFile.length() <= 0) {
            throw IOException("Source file does not exist or is empty: ${sourceFile.absolutePath}")
        }

        val cleanMimeType = mimeType.ifBlank { "application/octet-stream" }
        val cleanName = desiredFileName.trim().ifBlank { sourceFile.name }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceFile, cleanName, cleanMimeType)
        } else {
            saveViaPublicDirectory(context, sourceFile, cleanName, cleanMimeType)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): SavedDownloadResult {
        val resolver = context.contentResolver
        val safeName = resolveUniqueFileNameMediaStore(resolver, fileName)

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.SIZE, sourceFile.length())
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create download entry in Android MediaStore Downloads")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Could not open output stream for MediaStore URI: $uri")

            // Mark write completed
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            // Step 7 verification: Verify the final Downloads URI exists and has readable data
            val verifiedLength = resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L

            if (verifiedLength <= 0 && sourceFile.length() > 0) {
                throw IOException("Verification failed: MediaStore download file is 0 bytes")
            }

            return SavedDownloadResult(
                uri = uri,
                finalFileName = safeName,
                mimeType = mimeType,
                size = if (verifiedLength > 0) verifiedLength else sourceFile.length(),
                localPath = uri.toString()
            )
        } catch (e: Exception) {
            // Clean up incomplete entry on error
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {}
            throw e
        }
    }

    private fun saveViaPublicDirectory(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String
    ): SavedDownloadResult {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val safeName = resolveUniqueFileNameFile(downloadsDir, fileName)
        val targetFile = File(downloadsDir, safeName)

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        if (!targetFile.exists() || targetFile.length() <= 0) {
            throw IOException("Verification failed: Downloads file does not exist or is empty")
        }

        // Notify MediaScanner so Android Downloads and file managers reflect it immediately
        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf(mimeType),
            null
        )

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )

        return SavedDownloadResult(
            uri = uri,
            finalFileName = safeName,
            mimeType = mimeType,
            size = targetFile.length(),
            localPath = targetFile.absolutePath
        )
    }

    private fun resolveUniqueFileNameMediaStore(resolver: ContentResolver, originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var candidate = originalName
        var counter = 1

        while (isFileNameTakenInMediaStore(resolver, candidate) && counter < 500) {
            candidate = "$baseName ($counter)$extension"
            counter++
        }
        return candidate
    }

    private fun isFileNameTakenInMediaStore(resolver: ContentResolver, candidateName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(candidateName)

        return try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveUniqueFileNameFile(dir: File, originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var candidate = originalName
        var counter = 1

        while (File(dir, candidate).exists() && counter < 500) {
            candidate = "$baseName ($counter)$extension"
            counter++
        }
        return candidate
    }
}
