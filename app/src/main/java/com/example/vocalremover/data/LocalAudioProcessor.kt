package com.example.vocalremover.data

import com.example.vocalremover.domain.ProcessingConfig
import com.example.vocalremover.domain.ProcessingState
import com.example.vocalremover.domain.ProcessingStep
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocalAudioProcessor(
    private val inputResolver: InputResolver,
    private val ffmpegRunner: FfmpegRunner,
    private val commandsBuilder: CommandsBuilder,
    private val silenceParser: SilencedetectParser,
    private val segmentSlicer: SegmentSlicer,
    private val zipper: Zipper
) {
    fun process(config: ProcessingConfig): Flow<ProcessingState> = flow {
        emit(ProcessingState.Processing(ProcessingStep.ANALYZING, 0f))

        try {
            val timestamp = System.currentTimeMillis()
            val inputFile = inputResolver.resolve(config.inputUri)
            val sessionDir = File(inputFile.parent, "session_$timestamp")
            if (!sessionDir.mkdirs()) throw RuntimeException("Failed to create session directory")

            // Move input file logic or just use it. InputResolver puts it in cacheDir root.
            // Let's use sessionDir for outputs.

            // 1. Detect segments (Analyze)
            emit(ProcessingState.Processing(ProcessingStep.ANALYZING, 0.2f))
            val silenceCmd = commandsBuilder.buildSilenceDetectCommand(inputFile.absolutePath)
            val silenceOutput = ffmpegRunner.execute(silenceCmd)
            val segments = silenceParser.parse(silenceOutput)

            // 2. Separate
            emit(ProcessingState.Processing(ProcessingStep.SEPARATING, 0.4f))
            val vocalsFile = File(sessionDir, "vocals.mp3")
            val instFile = File(sessionDir, "instrumental.mp3")

            val sepCmd = commandsBuilder.buildSeparationCommand(inputFile.absolutePath, vocalsFile.absolutePath, instFile.absolutePath)
            ffmpegRunner.execute(sepCmd)

            // 3. Slice Vocals
            emit(ProcessingState.Processing(ProcessingStep.SLICING, 0.7f))
            val outputDir = File(sessionDir, "segments")
            val slicedFiles = segmentSlicer.slice(vocalsFile, segments, outputDir)

            // 4. Zip
            emit(ProcessingState.Processing(ProcessingStep.ZIPPING, 0.9f))
            val zipFile = File(sessionDir, "result.zip")
            zipper.zipFiles(slicedFiles + vocalsFile + instFile, zipFile)

            emit(ProcessingState.Success(zipFile))

        } catch (e: Exception) {
            emit(ProcessingState.Error(e.message ?: "Unknown error"))
        }
    }
}
