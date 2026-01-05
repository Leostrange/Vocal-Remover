package com.vocalclear.app.domain.repository

import com.vocalclear.app.domain.model.AudioFile
import com.vocalclear.app.domain.model.ProcessingConfig
import com.vocalclear.app.domain.model.ProcessingResult
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream

/**
 * Repository interface for audio file operations
 */
interface AudioRepository {
    /**
     * Load audio file metadata from URI
     */
    suspend fun loadAudioFile(uri: android.net.Uri): Result<AudioFile>

    /**
     * Read audio data from URI as InputStream
     */
    fun getAudioInputStream(uri: android.net.Uri): InputStream?

    /**
     * Copy audio file to app's cache directory
     */
    suspend fun copyToCache(uri: android.net.Uri, fileName: String): Result<File>

    /**
     * Delete file from cache
     */
    suspend fun deleteFromCache(fileName: String): Boolean

    /**
     * Get list of cached files
     */
    fun getCachedFiles(): List<File>
}

/**
 * Repository interface for offline audio processing
 */
interface OfflineProcessorRepository {
    /**
     * Process audio file using algorithmic center channel cancellation
     * Returns the instrumental track (vocals removed)
     */
    suspend fun processAudio(
        inputFile: File,
        outputFile: File,
        filterStrength: Float,
        onProgress: (Int, String) -> Unit
    ): Result<File>

    /**
     * Apply additional audio filters
     */
    suspend fun applyFilters(
        inputFile: File,
        outputFile: File,
        lowPassFreq: Int? = null,
        highPassFreq: Int? = null
    ): Result<File>
}

/**
 * Repository interface for online API processing
 */
interface OnlineProcessorRepository {
    /**
     * Upload audio file to server for AI processing
     */
    suspend fun uploadForProcessing(
        inputStream: InputStream,
        fileName: String,
        onProgress: (Int, String) -> Unit
    ): Result<ProcessingResult>

    /**
     * Check server health/status
     */
    suspend fun checkServerHealth(): Boolean

    /**
     * Get list of available processing modes from server
     */
    suspend fun getAvailableModes(): List<String>
}

/**
 * Repository interface for ZIP file creation
 */
interface ArchiveRepository {
    /**
     * Create ZIP archive from files
     */
    suspend fun createArchive(
        files: List<File>,
        archiveName: String,
        onProgress: (Int, String) -> Unit
    ): Result<File>

    /**
     * Extract ZIP archive
     */
    suspend fun extractArchive(archiveFile: File, outputDir: File): Result<List<File>>
}

// ==================== Vocal Section Cutting Interfaces ====================

/**
 * Repository interface for vocal section operations
 */
interface VocalSectionRepository {
    /**
     * Cut a section from vocal audio file
     */
    suspend fun cutSection(
        inputFile: File,
        outputFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        onProgress: (Int, String) -> Unit
    ): Result<File>

    /**
     * Get audio duration in milliseconds
     */
    suspend fun getAudioDuration(file: File): Long

    /**
     * Generate waveform data for visualization
     */
    suspend fun generateWaveformData(
        file: File,
        sampleCount: Int = 200
    ): Result<List<Float>>

    /**
     * Export multiple sections to individual files
     */
    suspend fun exportSections(
        inputFile: File,
        sections: List<com.vocalclear.app.domain.model.VocalSection>,
        outputDir: File,
        onProgress: (Int, String) -> Unit
    ): Result<List<com.vocalclear.app.domain.model.SectionExportResult>>

    /**
     * Create archive with all exported sections
     */
    suspend fun createSectionsArchive(
        sections: List<com.vocalclear.app.domain.model.SectionExportResult>,
        archiveName: String
    ): Result<File>
}
