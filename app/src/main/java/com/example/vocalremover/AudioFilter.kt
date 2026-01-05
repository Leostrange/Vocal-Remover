package com.example.vocalremover

import java.io.Serializable

/**
 * Базовый класс для аудио фильтров
 */
sealed class AudioFilter(val name: String) : Serializable {
    abstract fun toFFmpegCommand(): String
}

/**
 * Фильтр высоких частот (High-pass filter)
 */
data class HighPass(val frequency: Double) : AudioFilter("HighPass") {
    override fun toFFmpegCommand(): String = "highpass=f=$frequency"
}

/**
 * Фильтр низких частот (Low-pass filter)
 */
data class LowPass(val frequency: Double) : AudioFilter("LowPass") {
    override fun toFFmpegCommand(): String = "lowpass=f=$frequency"
}

/**
 * Полосовой фильтр (Band-pass filter)
 */
data class BandPass(val frequency: Double, val width: Double) : AudioFilter("BandPass") {
    override fun toFFmpegCommand(): String = "bandpass=f=$frequency:w=$width"
}

/**
 * Эквалайзер
 */
data class Equalizer(
    val frequency: Double,
    val width: Double,
    val gain: Double
) : AudioFilter("Equalizer") {
    override fun toFFmpegCommand(): String = "equalizer=f=$frequency:width_type=h:width=$width:g=$gain"
}

/**
 * Компрессор динамического диапазона
 */
data class Compressor(
    val attacks: Double = 0.1,
    val decays: Double = 0.8,
    val points: String = "-80/-80|-45/-15|-27/-9|0/-7|20/-7"
) : AudioFilter("Compressor") {
    override fun toFFmpegCommand(): String = "compand=attacks=$attacks:decays=$decays:points=$points"
}

/**
 * Лимитер
 */
data class Limiter(
    val level: Double = -1.0,
    val limit: Double = -0.1
) : AudioFilter("Limiter") {
    override fun toFFmpegCommand(): String = "alimiter=level_in=$level:level_out=$limit"
}

/**
 * Реверб
 */
data class Reverb(
    val roomSize: Double = 0.5,
    val damping: Double = 0.5,
    val wetLevel: Double = 0.3
) : AudioFilter("Reverb") {
    override fun toFFmpegCommand(): String = "aecho=0.8:0.88:60:0.4"
}

/**
 * Хорус эффект
 */
data class Chorus(
    val inGain: Double = 0.4,
    val outGain: Double = 0.4,
    val delays: String = "50|200|100",
    val decays: String = "0.4|0.25|0.3",
    val speeds: String = "1.5|1.9|2.3",
    val depths: String = "2|2|3"
) : AudioFilter("Chorus") {
    override fun toFFmpegCommand(): String = "chorus=0.5:0.9:50:0.4:0.25:2"
}

/**
 * Дилэй
 */
data class Delay(
    val delayTime: Double = 0.5,
    val feedback: Double = 0.3,
    val wetLevel: Double = 0.4
) : AudioFilter("Delay") {
    override fun toFFmpegCommand(): String = "aecho=$delayTime:$feedback:1000:$wetLevel"
}

/**
 * Нормализация громкости
 */
data class Normalize(
    val targetLevel: Double = -23.0,
    val truePeak: Boolean = false
) : AudioFilter("Normalize") {
    override fun toFFmpegCommand(): String = "loudnorm=I=$targetLevel"
}

/**
 * Класс для композиции фильтров
 */
class FilterChain(private val filters: MutableList<AudioFilter> = mutableListOf()) {
    
    fun addFilter(filter: AudioFilter) {
        filters.add(filter)
    }
    
    fun removeFilter(filter: AudioFilter) {
        filters.remove(filter)
    }
    
    fun clearFilters() {
        filters.clear()
    }
    
    fun toFFmpegCommand(): String {
        if (filters.isEmpty()) return ""
        val filterCommands = filters.map { it.toFFmpegCommand() }
        return "-af \"${filterCommands.joinToString(",")}\""
    }
    
    fun size(): Int = filters.size
    
    fun isEmpty(): Boolean = filters.isEmpty()
    
    fun getFilters(): List<AudioFilter> = filters.toList()
}

/**
 * Предустановленные фильтры для разных типов обработки
 */
object FilterPresets {
    
    // Предустановки для вокала
    val vocalEnhancement = FilterChain(mutableListOf(
        HighPass(80.0),
        LowPass(8000.0),
        Equalizer(1000.0, 200.0, 3.0),
        Compressor()
    ))
    
    val vocalIsolation = FilterChain(mutableListOf(
        HighPass(150.0),
        LowPass(8000.0),
        Compressor(attacks = 0.05, decays = 0.3, points = "-80/-80|-60/-30|-30/-15|-20/-10|0/-7")
    ))
    
    // Предустановки для инструментов
    val drumEnhancement = FilterChain(mutableListOf(
        HighPass(60.0),
        LowPass(8000.0),
        Compressor(attacks = 0.05, decays = 0.3, points = "-80/-80|-60/-30|-30/-15|-20/-10|0/-7")
    ))
    
    val bassEnhancement = FilterChain(mutableListOf(
        HighPass(20.0),
        LowPass(250.0),
        Equalizer(60.0, 100.0, 4.0),
        Compressor(attacks = 0.1, decays = 0.8, points = "-80/-80|-60/-30|-30/-15|-20/-10|0/-7")
    ))
    
    // Предустановки для мастеринга
    val masterEnhancement = FilterChain(mutableListOf(
        HighPass(20.0),
        LowPass(20000.0),
        Equalizer(1000.0, 500.0, 1.0),
        Compressor(attacks = 0.1, decays = 0.8, points = "-80/-80|-45/-15|-27/-9|0/-7|20/-7"),
        Normalize()
    ))
    
    val rockMaster = FilterChain(mutableListOf(
        HighPass(30.0),
        Equalizer(80.0, 100.0, 2.0),
        Equalizer(2000.0, 500.0, 3.0),
        Compressor(attacks = 0.05, decays = 0.2, points = "-80/-80|-60/-20|-30/-10|-20/-5|0/-3"),
        Limiter()
    ))
    
    val popMaster = FilterChain(mutableListOf(
        HighPass(40.0),
        Equalizer(500.0, 300.0, 2.0),
        Equalizer(3000.0, 1000.0, 1.0),
        Compressor(attacks = 0.1, decays = 0.5, points = "-80/-80|-50/-20|-30/-10|-20/-5|0/-3"),
        Normalize(-16.0)
    ))
    
    // Предустановки для восстановления
    val noiseReduction = FilterChain(mutableListOf(
        HighPass(80.0),
        LowPass(8000.0)
    ))
    
    val brightnessEnhancement = FilterChain(mutableListOf(
        HighPass(200.0),
        Equalizer(5000.0, 1000.0, 2.0),
        Equalizer(10000.0, 2000.0, 1.0)
    ))
    
    val warmthEnhancement = FilterChain(mutableListOf(
        LowPass(8000.0),
        Equalizer(200.0, 200.0, 2.0),
        Equalizer(1000.0, 500.0, 1.0)
    ))
}

/**
 * Utility класс для работы с фильтрами
 */
object FilterUtils {
    
    /**
     * Создает пользовательский фильтр на основе параметров
     */
    fun createCustomFilter(
        type: String,
        parameters: Map<String, Double>
    ): AudioFilter? {
        return when (type.lowercase()) {
            "highpass" -> HighPass(parameters["frequency"] ?: 80.0)
            "lowpass" -> LowPass(parameters["frequency"] ?: 8000.0)
            "bandpass" -> BandPass(
                parameters["frequency"] ?: 1000.0,
                parameters["width"] ?: 200.0
            )
            "equalizer" -> Equalizer(
                parameters["frequency"] ?: 1000.0,
                parameters["width"] ?: 200.0,
                parameters["gain"] ?: 0.0
            )
            "compressor" -> Compressor(
                parameters["attacks"] ?: 0.1,
                parameters["decays"] ?: 0.8,
                parameters["points"] ?: "-80/-80|-45/-15|-27/-9|0/-7|20/-7"
            )
            "normalizer" -> Normalize(
                parameters["targetLevel"] ?: -23.0,
                false // normalizer не поддерживает dynamic type в Map<String, Double>
            )
            else -> null
        }
    }
    
    /**
     * Объединяет несколько фильтров в одну цепочку
     */
    fun combineFilters(vararg filterChains: FilterChain): FilterChain {
        val combined = FilterChain()
        filterChains.forEach { chain ->
            chain.getFilters().forEach { filter ->
                combined.addFilter(filter)
            }
        }
        return combined
    }
    
    /**
     * Создает фильтр на основе жанра музыки
     */
    fun createGenreFilter(genre: String): FilterChain {
        return when (genre.lowercase()) {
            "rock" -> FilterPresets.rockMaster
            "pop" -> FilterPresets.popMaster
            "classical" -> FilterPresets.masterEnhancement
            "electronic" -> FilterPresets.vocalEnhancement
            "jazz" -> FilterPresets.warmthEnhancement
            "metal" -> FilterPresets.drumEnhancement
            else -> FilterPresets.masterEnhancement
        }
    }
}
