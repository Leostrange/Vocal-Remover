package com.example.vocalremover

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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

            val copiedInput = File(workingDir, "input_any")
            copyUriToFile(inputUri, copiedInput)
            ensureNotStopped(logCollector)

            updateProgress(15, "Конвертация в WAV")
            val inputWav = File(workingDir, "input.wav")
            if (!runFfmpeg(
                    command = "-y -i ${copiedInput.absolutePath} -ac 1 -ar 44100 -vn ${inputWav.absolutePath}",
                    logCollector = logCollector
                )
            ) {
                return failureResult("Не удалось конвертировать входной файл", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(30, "Усиление вокала")
            val enhancedWav = File(workingDir, "enhanced.wav")
            if (!runFfmpeg(
                    command = "-y -i ${inputWav.absolutePath} -af \"highpass=f=200,lowpass=f=3000\" ${enhancedWav.absolutePath}",
                    logCollector = logCollector
                )
            ) {
                return failureResult("Не удалось усилить вокал", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(45, "Анализ тишины")
            val silenceLogs = StringBuilder()
            if (!runFfmpeg(
                    command = "-hide_banner -i ${enhancedWav.absolutePath} -af \"silencedetect=noise=${silenceThreshold}dB:d=$minSilenceDuration\" -f null -",
                    logCollector = logCollector,
                    logSink = silenceLogs
                )
            ) {
                return failureResult("Не удалось обнаружить тишину", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(55, "Подготовка сегментов")
            val durationSeconds = readDurationSeconds(enhancedWav)
            if (durationSeconds <= 0.0) {
                return failureResult("Не удалось определить длительность аудио", logCollector)
            }

            val silenceIntervals = parseSilenceIntervals(silenceLogs.toString())
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
            val segmentFiles = sliceSegments(enhancedWav, segments, workingDir, logCollector)
            if (segmentFiles.isEmpty()) {
                return failureResult("Сегменты не были созданы", logCollector)
            }
            ensureNotStopped(logCollector)

            updateProgress(90, "Создание ZIP архива")
            val zipFile = File(workingDir, "result.zip")
            zipSegments(segmentFiles, zipFile)
            val savedUri = saveToDownloads(zipFile) ?: return failureResult(
                "Не удалось сохранить ZIP в загрузки",
                logCollector
            )

            updateProgress(100, "Готово")
            val logTail = buildLogTail(logCollector, silenceLogs.toString())
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

    private suspend fun copyUriToFile(uri: Uri, destination: File) {
        withContext(Dispatchers.IO) {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Не удалось открыть выбранный файл")
        }
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

    private fun parseSilenceIntervals(logs: String): List<AudioSegment> {
        val intervals = mutableListOf<AudioSegment>()
        var currentStart: Double? = null
        logs.lineSequence().forEach { line ->
            when {
                line.contains("silence_start") -> {
                    currentStart = line.substringAfter("silence_start:").trim().toDoubleOrNull()
                }

                line.contains("silence_end") -> {
                    val end = line.substringAfter("silence_end:").substringBefore(" ").trim()
                        .toDoubleOrNull()
                    val start = currentStart
                    if (start != null && end != null) {
                        intervals.add(AudioSegment(start, end))
                    }
                    currentStart = null
                }
            }
        }

        return intervals.sortedBy { it.start }
    }

    private fun buildVoiceSegments(
        silenceIntervals: List<AudioSegment>,
        durationSeconds: Double,
        paddingSeconds: Double,
        minSegmentSeconds: Double
    ): List<AudioSegment> {
        if (durationSeconds <= 0) return emptyList()
        val voiceSegments = mutableListOf<AudioSegment>()

        var cursor = 0.0
        val boundedSilence = silenceIntervals.map {
            AudioSegment(
                start = max(0.0, it.start),
                end = min(durationSeconds, max(it.start, it.end))
            )
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
        workingDir: File,
        logCollector: StringBuilder
    ): List<File> {
        val result = mutableListOf<File>()
        val stepSize = if (segments.isNotEmpty()) 25 / segments.size else 0
        var progress = 65

        for ((index, segment) in segments.withIndex()) {
            if (isStopped) break
            val output = File(workingDir, "seg_${index.toString().padStart(3, '0')}.wav")
            val command =
                "-y -ss ${segment.start} -to ${segment.end} -i ${source.absolutePath} -c copy ${output.absolutePath}"
            if (runFfmpeg(command, logCollector)) {
                result.add(output)
            }
            progress += stepSize
            updateProgress(progress.coerceAtMost(90), "Нарезка сегмента ${index + 1}/${segments.size}")
        }

        return result
    }

    private suspend fun readDurationSeconds(file: File): Double {
        val session = withContext(Dispatchers.IO) {
            FFprobeKit.getMediaInformation(file.absolutePath)
        }
        val durationString = session.mediaInformation?.duration ?: return 0.0
        return durationString.toDoubleOrNull() ?: 0.0
    }

    private fun zipSegments(segmentFiles: List<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            segmentFiles.forEachIndexed { index, file ->
                if (file.exists()) {
                    val entry = ZipEntry("segment_${index.toString().padStart(3, '0')}.wav")
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
