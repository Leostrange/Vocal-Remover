package com.example.vocalremover.usecase

import com.example.vocalremover.data.SilencedetectParser
import com.example.vocalremover.domain.Segment
import java.io.File

class BuildSegmentsUseCase(
    private val silenceParser: SilencedetectParser
) {
    fun invoke(ffmpegOutput: String): List<Segment> {
        return silenceParser.parse(ffmpegOutput)
    }
}
