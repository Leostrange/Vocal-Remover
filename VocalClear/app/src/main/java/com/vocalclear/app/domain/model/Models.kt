package com.vocalclear.app.domain.model

import android.net.Uri

/**
 * Represents an audio file selected by the user
 */
data class AudioFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val duration: Long = 0L
)

/**
 * Configuration for audio processing
 */
data class ProcessingConfig(
    val mode: ProcessingMode = ProcessingMode.OFFLINE,
    val filterStrength: FilterStrength = FilterStrength.MEDIUM,
    val outputFormat: OutputFormat = OutputFormat.WAV,
    val serverUrl: String = DEFAULT_SERVER_URL
) {
    companion object {
        const val DEFAULT_SERVER_URL = "https://api.vocalclear.example.com"
    }
}

/**
 * Processing mode selection
 */
enum class ProcessingMode {
    OFFLINE,  // On-device algorithmic processing
    ONLINE    // Server-side AI processing (Spleeter)
}

/**
 * Filter strength for offline vocal removal
 */
enum class FilterStrength(val multiplier: Float) {
    LIGHT(0.3f),    // Subtle center channel reduction
    MEDIUM(0.5f),   // Moderate reduction
    STRONG(0.7f)    // Aggressive reduction
}

/**
 * Output audio format
 */
enum class OutputFormat(val extension: String, val mimeType: String) {
    WAV("wav", "audio/wav"),
    MP3("mp3", "audio/mpeg")
}

/**
 * Result of audio processing
 */
data class ProcessingResult(
    val status: ResultStatus,
    val outputFiles: List<OutputFile> = emptyList(),
    val errorMessage: String? = null,
    val processingTimeMs: Long = 0L
)

/**
 * Status of processing operation
 */
enum class ResultStatus {
    IDLE,
    PROCESSING,
    SUCCESS,
    ERROR,
    CANCELLED
}

/**
 * Represents an output file after processing
 */
data class OutputFile(
    val file: java.io.File,
    val type: OutputFileType,
    val name: String
)

/**
 * Type of output file
 */
enum class OutputFileType {
    INSTRUMENTAL,  // Background music (vocals removed)
    VOCALS,        // Isolated vocals (if available)
    ARCHIVE        // ZIP archive containing all files
}

/**
 * Progress update during processing
 */
data class ProcessingProgress(
    val stage: ProcessingStage,
    val percentage: Int,
    val message: String
)

/**
 * Stages of audio processing
 */
enum class ProcessingStage {
    IDLE,
    LOADING,
    DECODING,
    PROCESSING,
    ENCODING,
    ARCHIVING,
    COMPLETE
}

// ==================== Vocal Section Cutting Models ====================

/**
 * Represents a vocal section/cut that can be created from separated vocals
 */
data class VocalSection(
    val id: String,
    val name: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val color: SectionColor = SectionColor.entries.random()
)

/**
 * Color for visual representation of sections in UI
 */
enum class SectionColor(val hexColor: Long) {
    RED(0xFFE57373),
    PINK(0xFFF48FB1),
    PURPLE(0xFFCE93D8),
    DEEP_PURPLE(0xFFB39DDB),
    INDIGO(0xFF9FA8DA),
    BLUE(0xFF90CAF9),
    CYAN(0xFF80DEEA),
    TEAL(0xFF80CBC4),
    GREEN(0xFFA5D6A7),
    LIME(0xFFC5E1A5),
    YELLOW(0xFFFFF59D),
    ORANGE(0xFFFFCC80)
}

/**
 * Collection of vocal sections for a single vocal track
 */
data class VocalSections(
    val vocalFileUri: Uri,
    val vocalFileName: String,
    val totalDurationMs: Long,
    val sections: List<VocalSection> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Result of section export operation
 */
data class SectionExportResult(
    val sectionId: String,
    val sectionName: String,
    val outputFile: File,
    val durationMs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * State of the section editor
 */
data class SectionEditorState(
    val isEditing: Boolean = false,
    val currentSectionId: String? = null,
    val previewPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val volume: Float = 1.0f,
    val selectedSections: Set<String> = emptySet()
)
