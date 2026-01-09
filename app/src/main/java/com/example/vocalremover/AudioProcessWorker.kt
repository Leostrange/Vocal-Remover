package com.example.vocalremover

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

data class AudioSegment(val start: Double, val end: Double)
data class InputMetadata(
    val displayName: String,
    val sizeBytes: Long?,
    val extension: String
)

data class CopiedInput(val file: File, val metadata: InputMetadata)

class AudioProcessWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val inputUriString = inputData.getString(KEY_INPUT_URI) ?: return failureResult(
            "Входной URI не найден",
            StringBuilder()
        )

        val silenceThreshold = inputData.getDouble(KEY_SILENCE_THRESHOLD, DEFAULT_THRESHOLD_DB)
        val minSilenceDuration = inputData.getDouble(KEY_MIN_SILENCE_DURATION, DEFAULT_MIN_SILENCE)
        val minSegmentDuration = inputData.getDouble(KEY_MIN_SEGMENT_DURATION, DEFAULT_MIN_SEGMENT)
        val paddingDuration = inputData.getDouble(KEY_PADDING_DURATION, DEFAULT_PADDING)

        val logCollector = StringBuilder()
        val workingDir = File(applicationContext.cacheDir, "processing_${id}").apply { mkdirs() }

        try {
            val inputUri = Uri.parse(inputUriString)
            updateProgress(0, "Подготовка входного файла")

            val copiedInput = copyUriToFile(inputUri, workingDir)
            val displayName = copiedInput.metadata.displayName
            val sizeBytes = copiedInput.metadata.sizeBytes ?: copiedInput.file.length()
            val ffmpegAvailable = FeatureFlags.isFfmpegAvailable
            logCollector.appendLine("FFmpeg available: $ffmpegAvailable")
            Log.i(TAG, "FFmpeg available: $ffmpegAvailable")

            if (!ffmpegAvailable) {
                updateProgress(5, "SIMPLIFIED режим")
                logCollector.appendLine("Mode: SIMPLIFIED")
                val result = runSimplifiedMode(
                    inputFile = copiedInput.file,
                    metadata = copiedInput.metadata,
                    workingDir = workingDir,
                    logCollector = logCollector,
                    sizeBytes = sizeBytes
                )
                return result
            }

            updateProgress(5, "FULL режим")
            logCollector.appendLine("Mode: FULL")
            ensureNotStopped(logCollector)

            updateProgress(15, "Конвертация в WAV")
            val inputWav = File(workingDir, "input.wav")
            if (!runFfmpeg(
                    command = "-y -i ${quotePath(copiedInput.file)} -ac 1 -ar 44100 -vn ${quotePath(inputWav)}",
                    logCollector = logCollector
                )
            ) {
                return failureResult("Не удалось конвертировать входной файл", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(30, "Усиление вокала")
            val enhancedWav = File(workingDir, "enhanced.wav")
            if (!runFfmpeg(
                    command = "-y -i ${quotePath(inputWav)} -af \"highpass=f=200,lowpass=f=3000\" ${quotePath(enhancedWav)}",
                    logCollector = logCollector
                )
            ) {
                return failureResult("Не удалось усилить вокал", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(45, "Анализ тишины")
            val silenceLogs = StringBuilder()
            if (!runFfmpeg(
                    command = "-hide_banner -i ${quotePath(enhancedWav)} -af \"silencedetect=noise=${silenceThreshold}dB:d=$minSilenceDuration\" -f null -",
                    logCollector = logCollector,
                    logSink = silenceLogs
                )
            ) {
                return failureResult("Не удалось обнаружить тишину", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(55, "Подготовка сегментов")
            val durationSeconds = readDurationSeconds(enhancedWav, logCollector)
            if (durationSeconds <= 0.0) {
                return failureResult("Не удалось определить длительность аудио", logCollector)
            }

            val silenceIntervals = SilenceDetectParser.parse(silenceLogs.toString())
            val segments = buildVoiceSegments(
                silenceIntervals = silenceIntervals,
                durationSeconds = durationSeconds,
                paddingSeconds = paddingDuration,
                minSegmentSeconds = minSegmentDuration
            )

            if (segments.isEmpty()) {
                return failureResult("Не удалось сформировать сегменты", logCollector)
            }

            updateProgress(65, "Нарезка сегментов")
            val segmentsDir = File(workingDir, "segments").apply { mkdirs() }
            val segmentFiles = sliceSegments(enhancedWav, segments, segmentsDir, logCollector)
            if (segmentFiles.isEmpty()) {
                return failureResult("Сегменты не были созданы", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(90, "Создание ZIP архива")
            val zipFile = File(workingDir, "result.zip")
            val infoText = buildInfoText(
                modeLabel = "FULL",
                displayName = displayName,
                sizeBytes = sizeBytes,
                silenceThreshold = silenceThreshold,
                minSilenceDuration = minSilenceDuration,
                minSegmentDuration = minSegmentDuration,
                paddingDuration = paddingDuration,
                durationSeconds = durationSeconds,
                segmentsCount = segments.size
            )
            val logTail = buildLogTail(logCollector, silenceLogs.toString())
            zipSegments(segmentFiles, zipFile, infoText, logTail)
            val savedUri = saveToDownloads(zipFile) ?: return failureResult(
                "Не удалось сохранить ZIP в загрузки",
                logCollector
            )

            updateProgress(100, "Готово")
            return Result.success(
                workDataOf(
                    KEY_OUTPUT_URI to savedUri.toString(),
                    KEY_LOG_TAIL to logTail
                )
            )
        } catch (e: Exception) {
            return failureResult(e.message ?: "Ошибка обработки", logCollector)
        } finally {
            workingDir.deleteRecursively()
        }
    }

    private suspend fun copyUriToFile(uri: Uri, workingDir: File): CopiedInput {
        val metadata = resolveInputMetadata(uri)
        val extension = metadata.extension.ifBlank { "bin" }
        val destination = File(workingDir, "input_original.$extension")
        withContext(Dispatchers.IO) {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Не удалось открыть выбранный файл")
        }
        return CopiedInput(destination, metadata)
    }

    private suspend fun runFfmpeg(
        command: String,
        logCollector: StringBuilder,
        logSink: StringBuilder? = null
    ): Boolean {
        val session = withContext(Dispatchers.IO) {
            FFmpegKit.execute(command)
        }
        val logs = session.allLogs
            .joinToString(separator = "\n") { it.message ?: "" }
        if (logs.isNotBlank()) {
            logCollector.appendLine(logs)
            logSink?.appendLine(logs)
        }
        return ReturnCode.isSuccess(session.returnCode)
    }

    private fun buildVoiceSegments(
        silenceIntervals: List<SilenceInterval>,
        durationSeconds: Double,
        paddingSeconds: Double,
        minSegmentSeconds: Double
    ): List<AudioSegment> {
        if (durationSeconds <= 0) return emptyList()
        val voiceSegments = mutableListOf<AudioSegment>()

        var cursor = 0.0
        val boundedSilence = silenceIntervals.map { interval ->
            val start = max(0.0, interval.start)
            val rawEnd = if (interval.end.isInfinite()) durationSeconds else interval.end
            val end = min(durationSeconds, max(start, rawEnd))
            AudioSegment(start, end)
        }

        for (interval in boundedSilence) {
            if (interval.start > cursor) {
                voiceSegments.add(AudioSegment(cursor, interval.start))
            }
            cursor = max(cursor, interval.end)
        }

        if (cursor < durationSeconds) {
            voiceSegments.add(AudioSegment(cursor, durationSeconds))
        }

        if (voiceSegments.isEmpty()) {
            voiceSegments.add(AudioSegment(0.0, durationSeconds))
        }

        val padded = voiceSegments.map { segment ->
            AudioSegment(
                start = max(0.0, segment.start - paddingSeconds),
                end = min(durationSeconds, segment.end + paddingSeconds)
            )
        }.sortedBy { it.start }

        val merged = mutableListOf<AudioSegment>()
        for (segment in padded) {
            if (merged.isEmpty()) {
                merged.add(segment)
            } else {
                val last = merged.last()
                if (segment.start <= last.end) {
                    merged[merged.lastIndex] = AudioSegment(
                        start = last.start,
                        end = max(last.end, segment.end)
                    )
                } else {
                    merged.add(segment)
                }
            }
        }

        val filtered = merged.filter { (it.end - it.start) >= minSegmentSeconds }
        if (filtered.isNotEmpty()) return filtered

        return listOf(AudioSegment(0.0, min(durationSeconds, voiceSegments.lastOrNull()?.end ?: durationSeconds)))
    }

    private suspend fun sliceSegments(
        source: File,
        segments: List<AudioSegment>,
        outputDir: File,
        logCollector: StringBuilder
    ): List<File> {
        val result = mutableListOf<File>()
        val stepSize = if (segments.isNotEmpty()) 25 / segments.size else 0
        var progress = 65

        for ((index, segment) in segments.withIndex()) {
            if (isStopped) break
            val output = File(outputDir, "seg_${index.toString().padStart(3, '0')}.wav")
            val command =
                "-y -ss ${segment.start} -to ${segment.end} -i ${quotePath(source)} -c copy ${quotePath(output)}"
            if (runFfmpeg(command, logCollector)) {
                result.add(output)
            }
            progress += stepSize
            updateProgress(progress.coerceAtMost(90), "Нарезка сегмента ${index + 1}/${segments.size}")
        }

        return result
    }

    private suspend fun readDurationSeconds(file: File, logCollector: StringBuilder): Double {
        val session = withContext(Dispatchers.IO) {
            FFprobeKit.execute(
                "-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 ${quotePath(file)}"
            )
        }
        val output = session.output?.trim()
        val duration = output?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotBlank() }
            ?.toDoubleOrNull()

        if (duration != null) {
            return duration
        }

        val logs = session.allLogs.joinToString(separator = "\n") { it.message ?: "" }
        if (logs.isNotBlank()) {
            logCollector.appendLine(logs)
        }
        return logs.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.toDoubleOrNull() ?: 0.0
    }

    private fun zipSegments(
        segmentFiles: List<File>,
        zipFile: File,
        infoText: String,
        logText: String?
    ) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            val infoEntry = ZipEntry("info.txt")
            zipOut.putNextEntry(infoEntry)
            zipOut.write(infoText.toByteArray())
            zipOut.closeEntry()

            if (!logText.isNullOrBlank()) {
                val logEntry = ZipEntry("log.txt")
                zipOut.putNextEntry(logEntry)
                zipOut.write(logText.toByteArray())
                zipOut.closeEntry()
            }

            segmentFiles.forEachIndexed { index, file ->
                if (file.exists()) {
                    val entry = ZipEntry("segments/seg_${index.toString().padStart(3, '0')}.wav")
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun saveToDownloads(source: File): Uri? {
        val resolver = applicationContext.contentResolver
        val fileName = "vocal_segments_${System.currentTimeMillis()}.zip"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(
                    android.provider.MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val target = File(downloads, fileName)
            source.copyTo(target, overwrite = true)
            FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                target
            )
        }
    }

    private fun zipSimplified(
        zipFile: File,
        originalFile: File,
        originalName: String,
        infoText: String,
        logText: String?
    ) {
        val safeName = originalName.replace("/", "_")
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            val infoEntry = ZipEntry("info.txt")
            zipOut.putNextEntry(infoEntry)
            zipOut.write(infoText.toByteArray())
            zipOut.closeEntry()

            if (!logText.isNullOrBlank()) {
                val logEntry = ZipEntry("log.txt")
                zipOut.putNextEntry(logEntry)
                zipOut.write(logText.toByteArray())
                zipOut.closeEntry()
            }

            val originalEntry = ZipEntry("original/$safeName")
            zipOut.putNextEntry(originalEntry)
            originalFile.inputStream().use { input -> input.copyTo(zipOut) }
            zipOut.closeEntry()
        }
    }

    private suspend fun updateProgress(value: Int, step: String) {
        setProgress(
            workDataOf(
                KEY_PROGRESS to value,
                KEY_STEP to step
            )
        )
    }

    private fun buildLogTail(primaryLogs: StringBuilder, extraLogs: String?): String {
        val combined = StringBuilder(primaryLogs)
        if (!extraLogs.isNullOrBlank()) {
            combined.appendLine(extraLogs)
        }
        val lines = combined.lines()
        return lines.takeLast(80).joinToString(separator = "\n")
    }

    private fun ensureNotStopped(logCollector: StringBuilder) {
        if (isStopped) {
            throw IllegalStateException("Обработка остановлена пользователем: ${buildLogTail(logCollector, null)}")
        }
    }

    private fun failureResult(message: String, logs: StringBuilder): Result {
        val tail = buildLogTail(logs, null)
        return Result.failure(
            workDataOf(
                KEY_ERROR_MESSAGE to message,
                KEY_LOG_TAIL to tail
            )
        )
    }

    private fun buildInfoText(
        modeLabel: String,
        displayName: String,
        sizeBytes: Long,
        silenceThreshold: Double,
        minSilenceDuration: Double,
        minSegmentDuration: Double,
        paddingDuration: Double,
        durationSeconds: Double,
        segmentsCount: Int
    ): String {
        return buildString {
            appendLine("Mode: $modeLabel")
            appendLine("Input: $displayName")
            appendLine("Size: $sizeBytes bytes")
            appendLine("Duration: ${"%.2f".format(durationSeconds)} sec")
            appendLine("Silence threshold: $silenceThreshold dB")
            appendLine("Min silence: $minSilenceDuration sec")
            appendLine("Min segment: $minSegmentDuration sec")
            appendLine("Padding: $paddingDuration sec")
            appendLine("Segments: $segmentsCount")
        }
    }

    private fun buildSimplifiedInfoText(
        displayName: String,
        sizeBytes: Long,
        reason: String
    ): String {
        return buildString {
            appendLine("Mode: SIMPLIFIED")
            appendLine("Reason: $reason")
            appendLine("Input: $displayName")
            appendLine("Size: $sizeBytes bytes")
            appendLine("Note: FFmpeg offline processing unavailable.")
        }
    }

    private fun quotePath(file: File): String = "\"${file.absolutePath}\""

    private fun resolveInputMetadata(uri: Uri): InputMetadata {
        val resolver = applicationContext.contentResolver
        var displayName = uri.lastPathSegment ?: "audio_file"
        var sizeBytes: Long? = null

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val extension = displayName.substringAfterLast('.', "")
        return InputMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
            extension = extension
        )
    }

    private suspend fun runSimplifiedMode(
        inputFile: File,
        metadata: InputMetadata,
        workingDir: File,
        logCollector: StringBuilder,
        sizeBytes: Long
    ): Result {
        val infoText = buildSimplifiedInfoText(
            displayName = metadata.displayName,
            sizeBytes = sizeBytes,
            reason = "FFmpeg unavailable or probe failed."
        )
        val logTail = buildLogTail(logCollector, null)
        val zipFile = File(workingDir, "result.zip")
        zipSimplified(
            zipFile = zipFile,
            originalFile = inputFile,
            originalName = metadata.displayName,
            infoText = infoText,
            logText = logTail
        )
        val savedUri = saveToDownloads(zipFile) ?: return failureResult(
            "Не удалось сохранить ZIP в загрузки",
            logCollector
        )
        updateProgress(100, "Готово")
        return Result.success(
            workDataOf(
                KEY_OUTPUT_URI to savedUri.toString(),
                KEY_LOG_TAIL to logTail
            )
        )
    }

    companion object {
        const val KEY_INPUT_URI = "input_uri"
        const val KEY_SILENCE_THRESHOLD = "silence_threshold"
        const val KEY_MIN_SILENCE_DURATION = "min_silence_duration"
        const val KEY_MIN_SEGMENT_DURATION = "min_segment_duration"
        const val KEY_PADDING_DURATION = "padding_duration"
        const val KEY_PROGRESS = "progress_percent"
        const val KEY_STEP = "progress_step"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_LOG_TAIL = "log_tail"
        const val WORK_NAME = "audio_process_work"
        const val WORK_TAG = "audio_process_tag"

        private const val DEFAULT_THRESHOLD_DB = -35.0
        private const val DEFAULT_MIN_SILENCE = 0.35
        private const val DEFAULT_MIN_SEGMENT = 1.0
        private const val DEFAULT_PADDING = 0.15
        private const val TAG = "AudioProcessWorker"

        fun buildInputData(
            audioUri: Uri,
            silenceThreshold: Double,
            minSilence: Double,
            minSegment: Double,
            padding: Double
        ): Data = workDataOf(
            KEY_INPUT_URI to audioUri.toString(),
            KEY_SILENCE_THRESHOLD to silenceThreshold,
            KEY_MIN_SILENCE_DURATION to minSilence,
            KEY_MIN_SEGMENT_DURATION to minSegment,
            KEY_PADDING_DURATION to padding
        )
    }
}
