package com.vocalclear.app.data.datasource

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.vocalclear.app.domain.model.SectionExportResult
import com.vocalclear.app.domain.model.VocalSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Data source for vocal section cutting operations using Android MediaCodec
 */
@Singleton
class VocalSectionDataSource @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer
        private const val WAV_HEADER_SIZE = 44
    }

    /**
     * Cut a section from an audio file and save as WAV
     */
    suspend fun cutSection(
        inputFile: File,
        outputFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        onProgress: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "Opening audio file...")

            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext Result.failure(Exception("No audio track found"))
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = audioFormat.getLong(MediaFormat.KEY_DURATION)

            // Calculate sample positions
            val startUs = startTimeMs * 1000
            val endUs = min(endTimeMs * 1000, duration)

            onProgress(30, "Extracting audio section...")

            // Extract and decode audio data
            val pcmData = extractPcmData(extractor, audioTrackIndex, audioFormat, startUs, endUs, onProgress)

            onProgress(80, "Writing WAV file...")

            // Write to WAV file
            writeWavFile(outputFile, pcmData, sampleRate, channelCount)

            extractor.release()

            onProgress(100, "Section extracted successfully")

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPcmData(
        extractor: MediaExtractor,
        trackIndex: Int,
        format: MediaFormat,
        startUs: Long,
        endUs: Long,
        onProgress: (Int, String) -> Unit
    ): ShortArray {
        extractor.selectTrack(trackIndex)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = android.media.MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmSamples = mutableListOf<Short>()
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        var totalBytesRead = 0L
        var sawInputEOS = false
        var sawOutputEOS = false
        val totalDurationUs = endUs - startUs

        while (!sawOutputEOS) {
            // Feed input
            if (!sawInputEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    inputBuffer?.let {
                        val bytesRead = extractor.readSampleData(it, 0)
                        if (bytesRead >= 0) {
                            val presentationTimeUs = extractor.sampleTime
                            if (presentationTimeUs > endUs) {
                                sawInputEOS = true
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, 0, 0,
                                    android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            } else {
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, bytesRead, presentationTimeUs, 0
                                )
                                extractor.advance()
                            }
                        } else {
                            sawInputEOS = true
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }
                    }
                }
            }

            // Drain output
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                outputBuffer?.let { buffer ->
                    if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        // Convert to PCM
                        while (buffer.remaining() > 0) {
                            if (buffer.remaining() >= 2) {
                                val sample = buffer.getShort()
                                pcmSamples.add(sample)
                            } else {
                                break
                            }
                        }

                        // Update progress
                        val progress = min(
                            30 + ((bufferInfo.presentationTimeUs - startUs) * 50 / totalDurationUs).toInt(),
                            80
                        )
                        onProgress(progress, "Extracting audio samples...")
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }

                if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.unselectTrack(trackIndex)

        return pcmSamples.toShortArray()
    }

    private fun writeWavFile(
        file: File,
        samples: ShortArray,
        sampleRate: Int,
        channelCount: Int
    ) {
        val dataSize = samples.size * 2
        val fileSize = dataSize + WAV_HEADER_SIZE - 8

        FileOutputStream(file).use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToByteArrayLE(fileSize))
            out.write("WAVE".toByteArray())

            // fmt subchunk
            out.write("fmt ".toByteArray())
            out.write(intToByteArrayLE(16)) // Subchunk1Size
            out.write(shortToByteArrayLE(1)) // AudioFormat (PCM)
            out.write(shortToByteArrayLE(channelCount.toShort()))
            out.write(intToByteArrayLE(sampleRate))
            out.write(intToByteArrayLE(sampleRate * channelCount * 2)) // ByteRate
            out.write(shortToByteArrayLE((channelCount * 2).toShort())) // BlockAlign
            out.write(shortToByteArrayLE(16)) // BitsPerSample

            // data subchunk
            out.write("data".toByteArray())
            out.write(intToByteArrayLE(dataSize))

            // Write PCM samples
            for (sample in samples) {
                out.write(shortToByteArrayLE(sample))
            }
        }
    }

    private fun intToByteArrayLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArrayLE(value: Short): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Get audio duration in milliseconds
     */
    suspend fun getAudioDuration(file: File): Long = withContext(Dispatchers.IO) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    val duration = format.getLong(MediaFormat.KEY_DURATION)
                    extractor.release()
                    return@withContext duration / 1000 // Convert to milliseconds
                }
            }
            extractor.release()
            0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Generate waveform data for visualization
     */
    suspend fun generateWaveformData(
        file: File,
        sampleCount: Int
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                extractor.release()
                return@withContext Result.failure(Exception("No audio track found"))
            }

            extractor.selectTrack(audioTrackIndex)
            val duration = audioFormat.getLong(MediaFormat.KEY_DURATION)
            val waveform = mutableListOf<Float>()

            // Sample at equal intervals
            val intervalUs = duration / sampleCount
            var currentPos = 0L

            repeat(sampleCount) {
                extractor.seekTo(currentPos, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                val bytesRead = extractor.readSampleData(buffer, 0)

                if (bytesRead > 0) {
                    buffer.flip()
                    var maxAmplitude = 0f
                    var sampleCountInBuffer = 0

                    // Read as 16-bit PCM
                    while (buffer.remaining() >= 2) {
                        val sample = buffer.short
                        val amplitude = abs(sample.toInt()) / 32768f
                        maxAmplitude = max(maxAmplitude, amplitude)
                        sampleCountInBuffer++
                    }

                    waveform.add(if (sampleCountInBuffer > 0) maxAmplitude else 0f)
                } else {
                    waveform.add(0f)
                }

                currentPos += intervalUs
            }

            extractor.release()
            Result.success(waveform)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export multiple sections to individual files
     */
    suspend fun exportSections(
        inputFile: File,
        sections: List<VocalSection>,
        outputDir: File,
        onProgress: (Int, String) -> Unit
    ): Result<List<SectionExportResult>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SectionExportResult>()

        sections.forEachIndexed { index, section ->
            val outputFile = File(outputDir, "${section.name}.wav")
            val progress = index * 100 / sections.size

            onProgress(progress, "Exporting section ${index + 1} of ${sections.size}: ${section.name}")

            val result = cutSection(
                inputFile = inputFile,
                outputFile = outputFile,
                startTimeMs = section.startTimeMs,
                endTimeMs = section.endTimeMs,
                onProgress = { p, m -> /* Detailed progress */ }
            )

            results.add(
                SectionExportResult(
                    sectionId = section.id,
                    sectionName = section.name,
                    outputFile = if (result.isSuccess) outputFile else File(""),
                    durationMs = section.endTimeMs - section.startTimeMs,
                    success = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            )
        }

        onProgress(100, "Export complete")
        Result.success(results)
    }

    /**
     * Create archive with all exported sections
     */
    suspend fun createSectionsArchive(
        sections: List<SectionExportResult>,
        archiveName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val successfulExports = sections.filter { it.success && it.outputFile.exists() }
            if (successfulExports.isEmpty()) {
                return@withContext Result.failure(Exception("No sections to archive"))
            }

            val files = successfulExports.map { it.outputFile }
            val archiveDir = File(context.cacheDir, "exports")
            if (!archiveDir.exists()) {
                archiveDir.mkdirs()
            }

            val archiveFile = File(archiveDir, "$archiveName.zip")

            // Create ZIP archive
            FileOutputStream(archiveFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    files.forEach { file ->
                        zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos, BUFFER_SIZE)
                        }
                        zos.closeEntry()
                    }
                }
            }

            Result.success(archiveFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
