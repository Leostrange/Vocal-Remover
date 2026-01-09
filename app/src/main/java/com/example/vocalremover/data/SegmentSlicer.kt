package com.example.vocalremover.data

import com.example.vocalremover.domain.Segment
import java.io.File

class SegmentSlicer(
    private val ffmpegRunner: FfmpegRunner,
    private val commandsBuilder: CommandsBuilder
) {
    suspend fun slice(inputFile: File, segments: List<Segment>, outputDir: File): List<File> {
        val outputFiles = mutableListOf<File>()
        outputDir.mkdirs()

        segments.forEachIndexed { index, segment ->
            val outputFile = File(outputDir, "segment_${index}_${segment.label}.mp3")
            val duration = segment.end - segment.start
            val command = commandsBuilder.buildSliceCommand(
                inputFile.absolutePath,
                segment.start,
                duration,
                outputFile.absolutePath
            )
            ffmpegRunner.execute(command)
            outputFiles.add(outputFile)
        }
        return outputFiles
    }
}
