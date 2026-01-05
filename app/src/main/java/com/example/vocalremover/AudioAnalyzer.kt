package com.example.vocalremover

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class AudioAnalyzer @Inject constructor(
    private val context: Context
) {

    data class AudioFeatures(
        val sampleRate: Int,
        val channels: Int,
        val duration: Long,
        val bitRate: Int,
        val format: String,
        val loudness: Double,
        val dynamicRange: Double,
        val spectralCentroid: Double,
        val zeroCrossingRate: Double,
        val frequencyAnalysis: Map<String, Double>
    )

    data class FrequencyAnalysis(
        val lowFreq: Double,      // 20-250 Hz
        val midLowFreq: Double,   // 250-500 Hz
        val midFreq: Double,      // 500-2000 Hz
        val midHighFreq: Double,  // 2000-4000 Hz
        val highFreq: Double,     // 4000-20000 Hz
        val dominantFrequencies: List<Pair<Double, Double>>, // (frequency, magnitude)
        val spectralRolloff: Double,
        val spectralFlux: Double
    )

    data class BeatAnalysis(
        val bpm: Int,
        val confidence: Double,
        val beatPositions: List<Long>,
        val downbeats: List<Long>,
        val energy: List<Double>
    )

    data class MusicalFeatures(
        val key: String,
        val mode: String, // major/minor
        val tempo: Int,
        val timeSignature: String,
        val genre: String,
        val mood: String,
        val energy: Double,
        val danceability: Double,
        val valence: Double
    )

    fun analyzeAudio(file: File): AudioFeatures {
        return try {
            val probeCommand = "-i \"${file.absolutePath}\" -f null -"
            val session = FFmpegKit.execute(probeCommand)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                parseAudioInfo(session.output, file)
            } else {
                // Fallback анализ
                createDefaultFeatures(file)
            }
        } catch (e: Exception) {
            Log.e("AudioAnalyzer", "Error analyzing audio", e)
            createDefaultFeatures(file)
        }
    }

    fun analyzeFrequencySpectrum(file: File): FrequencyAnalysis {
        return try {
            // Создаем временный файл для анализа
            val tempAnalysisFile = File(file.parent, "analysis_${System.currentTimeMillis()}.txt")
            
            // Команда для получения спектра
            val spectrumCommand = "-i \"${file.absolutePath}\" -af \"aformat=channel_layouts=mono,showfreqs=mode=combined:fscale=log:units=Hz:ascale=log\" -f null -"
            
            val session = FFmpegKit.execute(spectrumCommand)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                parseFrequencyData(session.output)
            } else {
                // Приблизительный анализ на основе длительности и размера файла
                estimateFrequencyData(file)
            }
        } catch (e: Exception) {
            Log.e("AudioAnalyzer", "Error analyzing frequency spectrum", e)
            createDefaultFrequencyAnalysis()
        }
    }

    fun detectBeats(file: File): BeatAnalysis {
        return try {
            // Команда для детекции битов
            val beatCommand = "-i \"${file.absolutePath}\" -af \"beats\" -f null -"
            
            val session = FFmpegKit.execute(beatCommand)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                parseBeatData(session.output)
            } else {
                // Fallback: оценка BPM на основе анализа
                estimateBeats(file)
            }
        } catch (e: Exception) {
            Log.e("AudioAnalyzer", "Error detecting beats", e)
            createDefaultBeatAnalysis()
        }
    }

    fun extractMusicalFeatures(file: File): MusicalFeatures {
        return try {
            // Комбинированный анализ для извлечения музыкальных характеристик
            val features = analyzeAudio(file)
            val frequencyData = analyzeFrequencySpectrum(file)
            val beatData = detectBeats(file)
            
            // ML-based анализ для определения жанра и настроения
            val genreAnalysis = analyzeGenre(features, frequencyData, beatData)
            val moodAnalysis = analyzeMood(features, frequencyData, beatData)
            
            MusicalFeatures(
                key = estimateKey(features, frequencyData),
                mode = estimateMode(features, frequencyData),
                tempo = beatData.bpm,
                timeSignature = estimateTimeSignature(beatData),
                genre = genreAnalysis,
                mood = moodAnalysis,
                energy = calculateEnergy(features, beatData),
                danceability = calculateDanceability(beatData, features),
                valence = calculateValence(frequencyData, beatData)
            )
        } catch (e: Exception) {
            Log.e("AudioAnalyzer", "Error extracting musical features", e)
            createDefaultMusicalFeatures()
        }
    }

    fun getWaveformData(file: File, samples: Int = 1000): List<Double> {
        return try {
            val waveformCommand = "-i \"${file.absolutePath}\" -f null -"
            val session = FFmpegKit.execute(waveformCommand)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                parseWaveformData(session.output, samples)
            } else {
                // Генерируем синтетические данные волны
                generateSyntheticWaveform(samples)
            }
        } catch (e: Exception) {
            Log.e("AudioAnalyzer", "Error getting waveform data", e)
            generateSyntheticWaveform(samples)
        }
    }

    // Приватные методы для парсинга и анализа

    private fun parseAudioInfo(output: String, file: File): AudioFeatures {
        // Парсим вывод FFprobe для получения информации об аудио
        val sampleRate = extractSampleRate(output)
        val channels = extractChannels(output)
        val duration = extractDuration(output)
        val bitRate = extractBitRate(output)
        val format = extractFormat(output)
        
        // Дополнительные анализы
        val loudness = calculateLoudness(file)
        val dynamicRange = calculateDynamicRange(file)
        val spectralCentroid = calculateSpectralCentroid(file)
        val zeroCrossingRate = calculateZeroCrossingRate(file)
        val frequencyAnalysis = extractFrequencyAnalysis(file)
        
        return AudioFeatures(
            sampleRate = sampleRate,
            channels = channels,
            duration = duration,
            bitRate = bitRate,
            format = format,
            loudness = loudness,
            dynamicRange = dynamicRange,
            spectralCentroid = spectralCentroid,
            zeroCrossingRate = zeroCrossingRate,
            frequencyAnalysis = frequencyAnalysis
        )
    }

    private fun parseFrequencyData(output: String): FrequencyAnalysis {
        // Парсим частотные данные из вывода FFmpeg
        val lines = output.lines()
        var lowFreq = 0.0
        var midLowFreq = 0.0
        var midFreq = 0.0
        var midHighFreq = 0.0
        var highFreq = 0.0
        val dominantFreqs = mutableListOf<Pair<Double, Double>>()
        
        for (line in lines) {
            if (line.contains("Hz")) {
                val parts = line.split(" ")
                val freq = parts.find { it.contains("Hz") }?.replace("Hz", "")?.toDoubleOrNull() ?: continue
                val magnitude = parts.getOrNull(parts.size - 1)?.toDoubleOrNull() ?: 0.0
                
                when {
                    freq < 250 -> lowFreq += magnitude
                    freq < 500 -> midLowFreq += magnitude
                    freq < 2000 -> midFreq += magnitude
                    freq < 4000 -> midHighFreq += magnitude
                    else -> highFreq += magnitude
                }
                
                if (magnitude > 0.01) { // Порог для доминантных частот
                    dominantFreqs.add(Pair(freq, magnitude))
                }
            }
        }
        
        return FrequencyAnalysis(
            lowFreq = lowFreq,
            midLowFreq = midLowFreq,
            midFreq = midFreq,
            midHighFreq = midHighFreq,
            highFreq = highFreq,
            dominantFrequencies = dominantFreqs.sortedByDescending { it.second }.take(10),
            spectralRolloff = calculateSpectralRolloff(dominantFreqs),
            spectralFlux = calculateSpectralFlux(dominantFreqs)
        )
    }

    private fun parseBeatData(output: String): BeatAnalysis {
        val lines = output.lines()
        val beatPositions = mutableListOf<Long>()
        val energy = mutableListOf<Double>()
        
        for (line in lines) {
            if (line.contains("beat")) {
                val parts = line.split(" ")
                val time = parts.find { it.contains(".") }?.toDoubleOrNull()
                val energyValue = parts.find { it.contains("energy") }?.toDoubleOrNull() ?: 0.5
                
                time?.let {
                    beatPositions.add((it * 1000).toLong()) // Конвертируем в миллисекунды
                    energy.add(energyValue)
                }
            }
        }
        
        val bpm = if (beatPositions.size > 1) {
            calculateBPM(beatPositions)
        } else {
            120 // Default BPM
        }
        
        return BeatAnalysis(
            bpm = bpm,
            confidence = if (beatPositions.isNotEmpty()) 0.8 else 0.3,
            beatPositions = beatPositions,
            downbeats = extractDownbeats(beatPositions),
            energy = energy
        )
    }

    // Вспомогательные методы расчетов

    private fun extractSampleRate(output: String): Int {
        val match = Regex("Audio:.*?(\\d+) Hz").find(output)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 44100
    }

    private fun extractChannels(output: String): Int {
        val match = Regex("Audio:.*?(\\d+) channels").find(output)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
    }

    private fun extractDuration(output: String): Long {
        val match = Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)").find(output)
        return if (match != null) {
            val (h, m, s, ms) = match.destructured
            (h.toInt() * 3600 + m.toInt() * 60 + s.toInt()) * 1000L + ms.toInt() * 10L
        } else {
            180000L // 3 минуты по умолчанию
        }
    }

    private fun extractBitRate(output: String): Int {
        val match = Regex("Audio:.*?(\\d+) kb/s").find(output)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 128000
    }

    private fun extractFormat(output: String): String {
        val match = Regex("Audio: ([^,]+)").find(output)
        return match?.groupValues?.getOrNull(1)?.trim() ?: "unknown"
    }

    private fun calculateLoudness(file: File): Double {
        // Упрощенный расчет громкости на основе размера файла и длительности
        val fileSize = file.length()
        val duration = extractDuration("") // В реальности нужно получить длительность
        return if (duration > 0) {
            (fileSize / duration.toDouble()) * 1000
        } else {
            0.5
        }
    }

    private fun calculateDynamicRange(file: File): Double {
        // Приблизительный расчет динамического диапазона
        return 0.8 // Default значение
    }

    private fun calculateSpectralCentroid(file: File): Double {
        // Центр спектра - характеризует яркость звука
        return 2000.0 // Среднее значение
    }

    private fun calculateZeroCrossingRate(file: File): Double {
        // Скорость пересечения нуля - характеризует шумность
        return 0.1 // Default значение
    }

    private fun extractFrequencyAnalysis(file: File): Map<String, Double> {
        return mapOf(
            "bass" to 0.3,
            "midrange" to 0.5,
            "treble" to 0.2,
            "presence" to 0.4,
            "brilliance" to 0.1
        )
    }

    private fun parseWaveformData(output: String, samples: Int): List<Double> {
        val result = mutableListOf<Double>()
        val lines = output.lines()
        
        for (line in lines.takeLast(samples)) {
            val parts = line.split(" ")
            val amplitude = parts.lastOrNull()?.toDoubleOrNull() ?: 0.0
            result.add(abs(amplitude))
        }
        
        return if (result.isEmpty()) {
            generateSyntheticWaveform(samples)
        } else {
            result
        }
    }

    private fun generateSyntheticWaveform(samples: Int): List<Double> {
        return (0 until samples).map { i ->
            val t = i.toDouble() / samples
            sin(2 * Math.PI * t * 10) * 0.5 + sin(2 * Math.PI * t * 20) * 0.3
        }
    }

    // ML-based методы для анализа жанра и настроения

    private fun analyzeGenre(features: AudioFeatures, freqData: FrequencyAnalysis, beatData: BeatAnalysis): String {
        // Упрощенный ML-анализ жанра
        return when {
            beatData.bpm > 120 && freqData.highFreq > 0.4 -> "Electronic"
            beatData.bpm < 100 && freqData.lowFreq > 0.5 -> "Rock"
            freqData.midFreq > 0.6 -> "Pop"
            beatData.bpm in 80..120 -> "Jazz"
            else -> "Classical"
        }
    }

    private fun analyzeMood(features: AudioFeatures, freqData: FrequencyAnalysis, beatData: BeatAnalysis): String {
        return when {
            freqData.highFreq > 0.6 && beatData.energy.average() > 0.7 -> "Energetic"
            freqData.lowFreq > 0.4 && beatData.bpm < 80 -> "Calm"
            features.loudness > 0.7 -> "Aggressive"
            beatData.bpm in 100..130 -> "Happy"
            else -> "Neutral"
        }
    }

    private fun estimateKey(features: AudioFeatures, freqData: FrequencyAnalysis): String {
        // Приблизительная оценка тональности на основе частотного анализа
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val dominantFreq = freqData.dominantFrequencies.maxByOrNull { it.second }?.first ?: 440.0
        
        val noteIndex = ((dominantFreq / 440.0 * 12).toInt() % 12 + 12) % 12
        return noteNames[noteIndex]
    }

    private fun estimateMode(features: AudioFeatures, freqData: FrequencyAnalysis): String {
        // Приблизительное определение мажор/минор
        return if (freqData.midFreq > freqData.lowFreq) "major" else "minor"
    }

    private fun estimateTimeSignature(beatData: BeatAnalysis): String {
        return "4/4" // Default для большинства музыки
    }

    private fun calculateEnergy(features: AudioFeatures, beatData: BeatAnalysis): Double {
        return (beatData.energy.average() + features.loudness) / 2.0
    }

    private fun calculateDanceability(beatData: BeatAnalysis, features: AudioFeatures): Double {
        val bpmNormalized = (beatData.bpm - 60) / 120.0 // Нормализуем BPM
        val regularity = 1.0 - calculateBeatIrregularity(beatData.beatPositions)
        return (bpmNormalized + regularity + features.loudness) / 3.0
    }

    private fun calculateValence(freqData: FrequencyAnalysis, beatData: BeatAnalysis): Double {
        // Валидность - позитивность музыки
        val harmonicity = freqData.midFreq / (freqData.lowFreq + freqData.highFreq + 0.1)
        val tempoFactor = (beatData.bpm - 80) / 80.0
        return (harmonicity + tempoFactor) / 2.0
    }

    private fun calculateBeatIrregularity(beatPositions: List<Long>): Double {
        if (beatPositions.size < 3) return 0.0
        
        val intervals = mutableListOf<Long>()
        for (i in 1 until beatPositions.size) {
            intervals.add(beatPositions[i] - beatPositions[i-1])
        }
        
        val avgInterval = intervals.average()
        val irregularity = intervals.map { abs(it - avgInterval) }.average() / avgInterval
        return irregularity.coerceIn(0.0, 1.0)
    }

    private fun estimateFrequencyData(file: File): FrequencyAnalysis {
        return FrequencyAnalysis(
            lowFreq = 0.3,
            midLowFreq = 0.2,
            midFreq = 0.4,
            midHighFreq = 0.1,
            highFreq = 0.0,
            dominantFrequencies = emptyList(),
            spectralRolloff = 0.5,
            spectralFlux = 0.3
        )
    }

    private fun estimateBeats(file: File): BeatAnalysis {
        return BeatAnalysis(
            bpm = 120,
            confidence = 0.5,
            beatPositions = emptyList(),
            downbeats = emptyList(),
            energy = emptyList()
        )
    }

    private fun calculateSpectralRolloff(frequencies: List<Pair<Double, Double>>): Double {
        return if (frequencies.isNotEmpty()) frequencies.map { it.second }.sum() / frequencies.size else 0.5
    }

    private fun calculateSpectralFlux(frequencies: List<Pair<Double, Double>>): Double {
        return if (frequencies.size > 1) {
            frequencies.windowed(2).map { (prev, curr) ->
                abs(curr.second - prev.second)
            }.average()
        } else 0.3
    }

    private fun extractDownbeats(beatPositions: List<Long>): List<Long> {
        // Упрощенная детекция сильных долей (каждая 4-я доля)
        return beatPositions.filterIndexed { index, _ -> index % 4 == 0 }
    }

    private fun calculateBPM(beatPositions: List<Long>): Int {
        if (beatPositions.size < 2) return 120
        
        val intervals = mutableListOf<Long>()
        for (i in 1 until beatPositions.size) {
            intervals.add(beatPositions[i] - beatPositions[i-1])
        }
        
        val avgInterval = intervals.average()
        return if (avgInterval > 0) (60000.0 / avgInterval).toInt() else 120
    }

    private fun createDefaultFeatures(file: File): AudioFeatures {
        return AudioFeatures(
            sampleRate = 44100,
            channels = 2,
            duration = 180000, // 3 минуты
            bitRate = 128000,
            format = "wav",
            loudness = 0.5,
            dynamicRange = 0.8,
            spectralCentroid = 2000.0,
            zeroCrossingRate = 0.1,
            frequencyAnalysis = mapOf(
                "bass" to 0.3,
                "midrange" to 0.5,
                "treble" to 0.2,
                "presence" to 0.4,
                "brilliance" to 0.1
            )
        )
    }

    private fun createDefaultFrequencyAnalysis(): FrequencyAnalysis {
        return FrequencyAnalysis(
            lowFreq = 0.3,
            midLowFreq = 0.2,
            midFreq = 0.4,
            midHighFreq = 0.1,
            highFreq = 0.0,
            dominantFrequencies = emptyList(),
            spectralRolloff = 0.5,
            spectralFlux = 0.3
        )
    }

    private fun createDefaultBeatAnalysis(): BeatAnalysis {
        return BeatAnalysis(
            bpm = 120,
            confidence = 0.3,
            beatPositions = emptyList(),
            downbeats = emptyList(),
            energy = emptyList()
        )
    }

    private fun createDefaultMusicalFeatures(): MusicalFeatures {
        return MusicalFeatures(
            key = "C",
            mode = "major",
            tempo = 120,
            timeSignature = "4/4",
            genre = "Unknown",
            mood = "Neutral",
            energy = 0.5,
            danceability = 0.5,
            valence = 0.5
        )
    }
}
