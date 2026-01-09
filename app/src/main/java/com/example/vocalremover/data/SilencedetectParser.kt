package com.example.vocalremover.data

import com.example.vocalremover.domain.Segment
import java.io.File
import java.util.regex.Pattern

class SilencedetectParser {
    fun parse(ffmpegOutput: String): List<Segment> {
        val segments = mutableListOf<Segment>()

        val silenceStarts = mutableListOf<Float>()
        val silenceEnds = mutableListOf<Float>()

        val startPattern = Pattern.compile("silence_start: ([0-9.]+)")
        val endPattern = Pattern.compile("silence_end: ([0-9.]+)")

        val startMatcher = startPattern.matcher(ffmpegOutput)
        while (startMatcher.find()) {
            silenceStarts.add(startMatcher.group(1)?.toFloatOrNull() ?: 0f)
        }

        val endMatcher = endPattern.matcher(ffmpegOutput)
        while (endMatcher.find()) {
            silenceEnds.add(endMatcher.group(1)?.toFloatOrNull() ?: 0f)
        }

        var currentPos = 0f
        for (i in silenceStarts.indices) {
            if (silenceStarts[i] > currentPos) {
                segments.add(Segment(currentPos, silenceStarts[i], "vocal"))
            }
            if (i < silenceEnds.size) {
                currentPos = silenceEnds[i]
            }
        }

        return segments
    }
}
