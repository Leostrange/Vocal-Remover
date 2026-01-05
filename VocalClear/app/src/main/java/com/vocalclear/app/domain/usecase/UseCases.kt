package com.vocalclear.app.domain.usecase

import com.vocalclear.app.domain.model.*
import com.vocalclear.app.domain.repository.AudioRepository
import com.vocalclear.app.domain.repository.ArchiveRepository
import com.vocalclear.app.domain.repository.OfflineProcessorRepository
import com.vocalclear.app.domain.repository.OnlineProcessorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject

/**
 * Use case for selecting and loading an audio file
 */
class SelectAudioFileUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(uri: android.net.Uri): Result<AudioFile> {
        return audioRepository.loadAudioFile(uri)
    }
}

/**
 * Use case for processing audio file (offline or online)
 */
class ProcessAudioUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    private val offlineProcessor: OfflineProcessorRepository,
    private val onlineProcessor: OnlineProcessorRepository,
    private val archiveRepository: ArchiveRepository
) {
    operator fun invoke(
        audioFile: AudioFile,
        config: ProcessingConfig
    ): Flow<ProcessingProgress> = flow {
        emit(ProcessingProgress(ProcessingStage.LOADING, 0, "Preparing file..."))

        try {
            // Copy file to cache for processing
            val cachedFile = audioRepository.copyToCache(audioFile.uri, audioFile.name)
                .getOrThrow()

            when (config.mode) {
                ProcessingMode.OFFLINE -> {
                    emit(ProcessingProgress(ProcessingStage.DECODING, 10, "Loading audio data..."))

                    val outputFile = File(cachedFile.parentFile, "instrumental_${System.currentTimeMillis()}.wav")

                    emit(ProcessingProgress(ProcessingStage.PROCESSING, 30, "Removing vocals..."))

                    // Process audio with center channel cancellation
                    offlineProcessor.processAudio(
                        inputFile = cachedFile,
                        outputFile = outputFile,
                        filterStrength = config.filterStrength.multiplier
                    ) { progress, message ->
                        // Progress callback handled internally
                    }.getOrThrow()

                    emit(ProcessingProgress(ProcessingStage.ENCODING, 70, "Saving result..."))

                    // Apply additional filters if requested
                    val finalFile = outputFile

                    // Create archive
                    emit(ProcessingProgress(ProcessingStage.ARCHIVING, 90, "Creating archive..."))

                    val archiveFile = archiveRepository.createArchive(
                        files = listOf(finalFile),
                        archiveName = "vocal_clear_result_${System.currentTimeMillis()}.zip"
                    ) { _, _ -> }.getOrThrow()

                    emit(ProcessingProgress(ProcessingStage.COMPLETE, 100, "Processing complete!"))

                    // Clean up cache
                    audioRepository.deleteFromCache(cachedFile.name)
                }

                ProcessingMode.ONLINE -> {
                    emit(ProcessingProgress(ProcessingStage.LOADING, 20, "Uploading to server..."))

                    val inputStream = audioRepository.getAudioInputStream(audioFile.uri)
                        ?: throw IllegalStateException("Cannot open audio stream")

                    onlineProcessor.uploadForProcessing(
                        inputStream = inputStream,
                        fileName = audioFile.name
                    ) { progress, message ->
                        emit(ProcessingProgress(ProcessingStage.PROCESSING, progress, message))
                    }.getOrThrow()

                    emit(ProcessingProgress(ProcessingStage.COMPLETE, 100, "Server processing complete!"))
                }
            }
        } catch (e: Exception) {
            emit(ProcessingProgress(ProcessingStage.IDLE, 0, "Error: ${e.message}"))
        }
    }
}

/**
 * Use case for detecting silence in audio file
 */
class DetectSilenceUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    data class SilenceSegment(val startMs: Long, val endMs: Long, val amplitude: Float)

    suspend operator fun invoke(
        audioFile: AudioFile,
        thresholdDb: Float = -40f,
        minSilenceDurationMs: Long = 500
    ): List<SilenceSegment> {
        // Simplified silence detection
        // In a real implementation, this would analyze PCM data
        return emptyList()
    }
}

/**
 * Use case for creating ZIP archive of results
 */
class CreateArchiveUseCase @Inject constructor(
    private val archiveRepository: ArchiveRepository
) {
    suspend operator fun invoke(
        files: List<File>,
        archiveName: String
    ): Result<File> {
        return archiveRepository.createArchive(files, archiveName) { _, _ -> }
    }
}

/**
 * Use case for cleaning up temporary files
 */
class CleanupCacheUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(): Boolean {
        return try {
            audioRepository.getCachedFiles().forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }
}

// ==================== Vocal Section Cutting Use Cases ====================

/**
 * Use case for cutting a section from vocal audio file
 */
class CutVocalSectionUseCase @Inject constructor(
    private val vocalSectionRepository: com.vocalclear.app.domain.repository.VocalSectionRepository
) {
    suspend operator fun invoke(
        inputFile: File,
        outputFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        onProgress: (Int, String) -> Unit
    ): Result<File> {
        return vocalSectionRepository.cutSection(
            inputFile = inputFile,
            outputFile = outputFile,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            onProgress = onProgress
        )
    }
}

/**
 * Use case for getting audio duration
 */
class GetAudioDurationUseCase @Inject constructor(
    private val vocalSectionRepository: com.vocalclear.app.domain.repository.VocalSectionRepository
) {
    suspend operator fun invoke(file: File): Long {
        return vocalSectionRepository.getAudioDuration(file)
    }
}

/**
 * Use case for generating waveform data for visualization
 */
class GenerateWaveformUseCase @Inject constructor(
    private val vocalSectionRepository: com.vocalclear.app.domain.repository.VocalSectionRepository
) {
    suspend operator fun invoke(file: File, sampleCount: Int = 200): Result<List<Float>> {
        return vocalSectionRepository.generateWaveformData(file, sampleCount)
    }
}

/**
 * Use case for exporting multiple vocal sections
 */
class ExportVocalSectionsUseCase @Inject constructor(
    private val vocalSectionRepository: com.vocalclear.app.domain.repository.VocalSectionRepository
) {
    suspend operator fun invoke(
        inputFile: File,
        sections: List<VocalSection>,
        outputDir: File,
        onProgress: (Int, String) -> Unit
    ): Result<List<SectionExportResult>> {
        return vocalSectionRepository.exportSections(inputFile, sections, outputDir, onProgress)
    }
}

/**
 * Use case for creating archive from exported sections
 */
class CreateSectionsArchiveUseCase @Inject constructor(
    private val vocalSectionRepository: com.vocalclear.app.domain.repository.VocalSectionRepository
) {
    suspend operator fun invoke(
        sections: List<SectionExportResult>,
        archiveName: String
    ): Result<File> {
        return vocalSectionRepository.createSectionsArchive(sections, archiveName)
    }
}

/**
 * Use case for auto-detecting vocal sections based on silence
 */
class AutoDetectSectionsUseCase @Inject constructor(
    private val vocalSectionRepository: com.vocalclear.app.domain.repository.VocalSectionRepository
) {
    suspend operator fun invoke(
        file: File,
        silenceThresholdDb: Float = -40f,
        minSectionDurationMs: Long = 1000,
        maxSectionDurationMs: Long = 30000
    ): List<VocalSection> {
        // Generate waveform for analysis
        val waveform = vocalSectionRepository.generateWaveformData(file, 1000).getOrDefault(emptyList())

        val sections = mutableListOf<VocalSection>()
        val threshold = dbToAmplitude(silenceThresholdDb)

        var sectionStart: Long? = null
        val samplesPerMs = waveform.size.toLong() / vocalSectionRepository.getAudioDuration(file)

        waveform.forEachIndexed { index, amplitude ->
            val timeMs = (index / samplesPerMs).toLong()
            val isAboveSilence = amplitude > threshold

            if (isAboveSilence && sectionStart == null) {
                sectionStart = timeMs
            } else if (!isAboveSilence && sectionStart != null) {
                val sectionEnd = timeMs
                val duration = sectionEnd - sectionStart

                if (duration >= minSectionDurationMs && duration <= maxSectionDurationMs) {
                    sections.add(
                        VocalSection(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "Section ${sections.size + 1}",
                            startTimeMs = sectionStart,
                            endTimeMs = sectionEnd
                        )
                    )
                }
                sectionStart = null
            }
        }

        // Handle last section if file ends with audio
        sectionStart?.let { start ->
            val duration = vocalSectionRepository.getAudioDuration(file) - start
            if (duration >= minSectionDurationMs) {
                sections.add(
                    VocalSection(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "Section ${sections.size + 1}",
                        startTimeMs = start,
                        endTimeMs = vocalSectionRepository.getAudioDuration(file)
                    )
                )
            }
        }

        return sections.ifEmpty {
            // If no sections detected, return entire file as one section
            listOf(
                VocalSection(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "Full Track",
                    startTimeMs = 0,
                    endTimeMs = vocalSectionRepository.getAudioDuration(file)
                )
            )
        }
    }

    private fun dbToAmplitude(db: Float): Float {
        return kotlin.math.pow(10f, db / 20f)
    }
}

/**
 * Use case for creating sections from manual time selection
 */
class CreateManualSectionUseCase @Inject constructor() {
    operator fun invoke(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        existingSections: List<VocalSection>
    ): Result<VocalSection> {
        if (startTimeMs >= endTimeMs) {
            return Result.failure(IllegalArgumentException("Start time must be less than end time"))
        }
        if (startTimeMs < 0) {
            return Result.failure(IllegalArgumentException("Start time cannot be negative"))
        }

        return Result.success(
            VocalSection(
                id = java.util.UUID.randomUUID().toString(),
                name = name.ifBlank { "Section ${existingSections.size + 1}" },
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
        )
    }
}
