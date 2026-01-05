package com.vocalclear.app.data.repository

import android.net.Uri
import com.vocalclear.app.data.local.ArchiveManager
import com.vocalclear.app.data.local.LocalAudioDataSource
import com.vocalclear.app.data.local.OfflineAudioProcessor
import com.vocalclear.app.data.remote.RemoteAudioProcessor
import com.vocalclear.app.domain.model.*
import com.vocalclear.app.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioRepository
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val localDataSource: LocalAudioDataSource
) : AudioRepository {

    override suspend fun loadAudioFile(uri: Uri): Result<AudioFile> {
        return localDataSource.loadAudioFile(uri)
    }

    override fun getAudioInputStream(uri: Uri): InputStream? {
        return localDataSource.getInputStream(uri)
    }

    override suspend fun copyToCache(uri: Uri, fileName: String): Result<File> {
        return localDataSource.copyToCache(uri, fileName)
    }

    override suspend fun deleteFromCache(fileName: String): Boolean {
        return localDataSource.deleteFromCache(fileName)
    }

    override fun getCachedFiles(): List<File> {
        return localDataSource.getCachedFiles()
    }
}

/**
 * Implementation of OfflineProcessorRepository
 */
@Singleton
class OfflineProcessorRepositoryImpl @Inject constructor(
    private val processor: OfflineAudioProcessor
) : OfflineProcessorRepository {

    override suspend fun processAudio(
        inputFile: File,
        outputFile: File,
        filterStrength: Float,
        onProgress: (Int, String) -> Unit
    ): Result<File> {
        return processor.processAudio(inputFile, outputFile, filterStrength, onProgress)
    }

    override suspend fun applyFilters(
        inputFile: File,
        outputFile: File,
        lowPassFreq: Int?,
        highPassFreq: Int?
    ): Result<File> {
        return if (lowPassFreq != null) {
            processor.applyLowPassFilter(inputFile, outputFile, lowPassFreq, onProgress)
        } else {
            Result.success(inputFile)
        }
    }
}

/**
 * Implementation of OnlineProcessorRepository
 */
@Singleton
class OnlineProcessorRepositoryImpl @Inject constructor(
    private val remoteProcessor: RemoteAudioProcessor
) : OnlineProcessorRepository {

    override suspend fun uploadForProcessing(
        inputStream: InputStream,
        fileName: String,
        onProgress: (Int, String) -> Unit
    ): Result<ProcessingResult> {
        return remoteProcessor.uploadAudio(inputStream, fileName, onProgress)
    }

    override suspend fun checkServerHealth(): Boolean {
        return remoteProcessor.checkHealth()
    }

    override suspend fun getAvailableModes(): List<String> {
        return remoteProcessor.getAvailableModes()
    }
}

/**
 * Implementation of ArchiveRepository
 */
@Singleton
class ArchiveRepositoryImpl @Inject constructor(
    private val archiveManager: ArchiveManager
) : ArchiveRepository {

    override suspend fun createArchive(
        files: List<File>,
        archiveName: String,
        onProgress: (Int, String) -> Unit
    ): Result<File> {
        return archiveManager.createArchive(files, archiveName, onProgress)
    }

    override suspend fun extractArchive(archiveFile: File, outputDir: File): Result<List<File>> {
        return archiveManager.extractArchive(archiveFile, outputDir) { _, _ -> }
    }
}

/**
 * Implementation of VocalSectionRepository
 */
@Singleton
class VocalSectionRepositoryImpl @Inject constructor(
    private val vocalSectionDataSource: com.vocalclear.app.data.datasource.VocalSectionDataSource
) : VocalSectionRepository {

    override suspend fun cutSection(
        inputFile: File,
        outputFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        onProgress: (Int, String) -> Unit
    ): Result<File> {
        return vocalSectionDataSource.cutSection(inputFile, outputFile, startTimeMs, endTimeMs, onProgress)
    }

    override suspend fun getAudioDuration(file: File): Long {
        return vocalSectionDataSource.getAudioDuration(file)
    }

    override suspend fun generateWaveformData(file: File, sampleCount: Int): Result<List<Float>> {
        return vocalSectionDataSource.generateWaveformData(file, sampleCount)
    }

    override suspend fun exportSections(
        inputFile: File,
        sections: List<VocalSection>,
        outputDir: File,
        onProgress: (Int, String) -> Unit
    ): Result<List<SectionExportResult>> {
        return vocalSectionDataSource.exportSections(inputFile, sections, outputDir, onProgress)
    }

    override suspend fun createSectionsArchive(
        sections: List<SectionExportResult>,
        archiveName: String
    ): Result<File> {
        return vocalSectionDataSource.createSectionsArchive(sections, archiveName)
    }
}
