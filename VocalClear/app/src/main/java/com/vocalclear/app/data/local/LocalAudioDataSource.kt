package com.vocalclear.app.data.local

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.vocalclear.app.domain.model.AudioFile
import com.vocalclear.app.domain.repository.AudioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source for audio file operations
 */
@Singleton
class LocalAudioDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "audio_cache").also { it.mkdirs() }
    }

    suspend fun loadAudioFile(uri: Uri): Result<AudioFile> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var fileName = "audio_${System.currentTimeMillis()}"
            var fileSize = 0L

            // Query file metadata
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Get duration using MediaMetadataRetriever
            val duration = getAudioDuration(uri)

            val mimeType = contentResolver.getType(uri) ?: "audio/*"

            Result.success(
                AudioFile(
                    uri = uri,
                    name = fileName,
                    size = fileSize,
                    mimeType = mimeType,
                    duration = duration
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getAudioDuration(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getInputStream(uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun copyToCache(uri: Uri, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot open file"))

            val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val outputFile = File(cacheDir, sanitizedName)

            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFromCache(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, fileName)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getCachedFiles(): List<File> {
        return cacheDir.listFiles()?.toList() ?: emptyList()
    }

    fun clearCache(): Boolean {
        return try {
            cacheDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }
}
