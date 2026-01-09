package com.example.vocalremover

data class SilenceInterval(val start: Double, val end: Double)

object SilenceDetectParser {
    fun parse(logs: String): List<SilenceInterval> {
        val intervals = mutableListOf<SilenceInterval>()
        var currentStart: Double? = null

        logs.lineSequence().forEach { line ->
            when {
                line.contains("silence_start") -> {
                    val start = line.substringAfter("silence_start:")
                        .substringBefore(" ")
                        .trim()
                        .toDoubleOrNull()
                    if (start != null) {
                        if (currentStart != null && currentStart != start) {
                            intervals.add(SilenceInterval(currentStart ?: 0.0, start))
                        }
                        currentStart = start
                    }
                }

                line.contains("silence_end") -> {
                    val end = line.substringAfter("silence_end:")
                        .substringBefore(" ")
                        .trim()
                        .toDoubleOrNull()
                    if (end != null) {
                        val start = currentStart ?: 0.0
                        intervals.add(SilenceInterval(start, end))
                    }
                    currentStart = null
                }
            }
        }

        if (currentStart != null) {
            intervals.add(SilenceInterval(currentStart ?: 0.0, Double.POSITIVE_INFINITY))
        }

        return intervals.sortedBy { it.start }
    }
}
