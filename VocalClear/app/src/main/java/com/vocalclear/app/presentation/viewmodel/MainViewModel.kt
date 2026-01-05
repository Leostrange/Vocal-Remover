package com.vocalclear.app.presentation.viewmodel

import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocalclear.app.domain.model.*
import com.vocalclear.app.domain.repository.AudioRepository
import com.vocalclear.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Main UI state for the app
 */
data class MainUiState(
    val selectedFile: AudioFile? = null,
    val processingConfig: ProcessingConfig = ProcessingConfig(),
    val processingState: ProcessingState = ProcessingState.IDLE,
    val progress: ProcessingProgress = ProcessingProgress(ProcessingStage.IDLE, 0, ""),
    val resultFile: File? = null,
    val errorMessage: String? = null,
    val recentFiles: List<AudioFile> = emptyList(),

    // Vocal Section Editor State
    val vocalSections: List<VocalSection> = emptyList(),
    val vocalFile: File? = null,
    val vocalDurationMs: Long = 0L,
    val waveformData: List<Float> = emptyList(),
    val editorState: SectionEditorState = SectionEditorState(),
    val selectedSection: VocalSection? = null,
    val exportedSections: List<SectionExportResult> = emptyList(),
    val isSectionEditorActive: Boolean = false
)

/**
 * Processing state enum
 */
enum class ProcessingState {
    IDLE,
    LOADING,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * ViewModel for main screen with vocal section cutting support
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val selectAudioFileUseCase: SelectAudioFileUseCase,
    private val processAudioUseCase: ProcessAudioUseCase,
    private val cleanupCacheUseCase: CleanupCacheUseCase,
    private val cutVocalSectionUseCase: CutVocalSectionUseCase,
    private val getAudioDurationUseCase: GetAudioDurationUseCase,
    private val generateWaveformUseCase: GenerateWaveformUseCase,
    private val exportVocalSectionsUseCase: ExportVocalSectionsUseCase,
    private val autoDetectSectionsUseCase: AutoDetectSectionsUseCase,
    private val createManualSectionUseCase: CreateManualSectionUseCase,
    private val createSectionsArchiveUseCase: CreateSectionsArchiveUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ==================== File Selection ====================

    /**
     * Select an audio file from device
     */
    fun selectFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.LOADING) }

            selectAudioFileUseCase(uri)
                .onSuccess { audioFile ->
                    _uiState.update {
                        it.copy(
                            selectedFile = audioFile,
                            processingState = ProcessingState.IDLE,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.ERROR,
                            errorMessage = "Failed to load file: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * Set vocal file for section editing (after separation)
     */
    fun setVocalFile(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                vocalFile = file,
                processingState = ProcessingState.LOADING
            }) }

            try {
                val duration = getAudioDurationUseCase(file)
                val waveform = generateWaveformUseCase(file).getOrDefault(emptyList())

                _uiState.update {
                    it.copy(
                        vocalDurationMs = duration,
                        waveformData = waveform,
                        processingState = ProcessingState.IDLE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.ERROR,
                        errorMessage = "Failed to load vocal file: ${e.message}"
                    )
                }
            }
        }
    }

    // ==================== Processing Configuration ====================

    /**
     * Update processing configuration
     */
    fun updateConfig(config: ProcessingConfig) {
        _uiState.update { it.copy(processingConfig = config) }
    }

    /**
     * Update filter strength
     */
    fun updateFilterStrength(strength: FilterStrength) {
        _uiState.update {
            it.copy(
                processingConfig = it.processingConfig.copy(filterStrength = strength)
            )
        }
    }

    /**
     * Update processing mode
     */
    fun updateMode(mode: ProcessingMode) {
        _uiState.update {
            it.copy(
                processingConfig = it.processingConfig.copy(mode = mode)
            )
        }
    }

    // ==================== Audio Processing ====================

    /**
     * Start audio processing
     */
    fun startProcessing() {
        val audioFile = _uiState.value.selectedFile ?: return
        val config = _uiState.value.processingConfig

        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.PROCESSING) }

            try {
                processAudioUseCase(audioFile, config)
                    .collect { progress ->
                        _uiState.update {
                            it.copy(progress = progress)
                        }

                        // Check if processing is complete
                        if (progress.stage == ProcessingStage.COMPLETE) {
                            _uiState.update {
                                it.copy(
                                    processingState = ProcessingState.SUCCESS,
                                    resultFile = null
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.ERROR,
                        errorMessage = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    /**
     * Cancel current processing
     */
    fun cancelProcessing() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingState = ProcessingState.IDLE,
                    progress = ProcessingProgress(ProcessingStage.IDLE, 0, "Cancelled")
                )
            }
        }
    }

    // ==================== Section Editor ====================

    /**
     * Activate section editor mode
     */
    fun activateSectionEditor() {
        _uiState.update { it.copy(isSectionEditorActive = true) }
    }

    /**
     * Deactivate section editor mode
     */
    fun deactivateSectionEditor() {
        _uiState.update {
            it.copy(
                isSectionEditorActive = false,
                editorState = SectionEditorState()
            )
        }
    }

    /**
     * Auto-detect vocal sections based on audio analysis
     */
    fun autoDetectSections() {
        val vocalFile = _uiState.value.vocalFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.LOADING) }

            try {
                val sections = autoDetectSectionsUseCase(vocalFile)
                _uiState.update {
                    it.copy(
                        vocalSections = sections,
                        processingState = ProcessingState.IDLE,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.ERROR,
                        errorMessage = "Failed to detect sections: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Add a new section manually
     */
    fun addSection(name: String, startTimeMs: Long, endTimeMs: Long) {
        val currentSections = _uiState.value.vocalSections
        val section = createManualSectionUseCase(name, startTimeMs, endTimeMs, currentSections)

        section.onSuccess { newSection ->
            _uiState.update {
                it.copy(vocalSections = it.vocalSections + newSection)
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(errorMessage = error.message)
            }
        }
    }

    /**
     * Remove a section
     */
    fun removeSection(sectionId: String) {
        _uiState.update {
            it.copy(
                vocalSections = it.vocalSections.filter { section -> section.id != sectionId },
                selectedSection = if (it.selectedSection?.id == sectionId) null else it.selectedSection
            )
        }
    }

    /**
     * Select a section for editing/preview
     */
    fun selectSection(section: VocalSection?) {
        _uiState.update {
            it.copy(
                selectedSection = section,
                editorState = it.editorState.copy(
                    currentSectionId = section?.id,
                    previewPositionMs = section?.startTimeMs ?: 0L
                )
            )
        }
    }

    /**
     * Update section time range
     */
    fun updateSectionTime(sectionId: String, startTimeMs: Long, endTimeMs: Long) {
        _uiState.update { state ->
            state.copy(
                vocalSections = state.vocalSections.map { section ->
                    if (section.id == sectionId) {
                        section.copy(startTimeMs = startTimeMs, endTimeMs = endTimeMs)
                    } else {
                        section
                    }
                }
            )
        }
    }

    /**
     * Update section name
     */
    fun updateSectionName(sectionId: String, name: String) {
        _uiState.update { state ->
            state.copy(
                vocalSections = state.vocalSections.map { section ->
                    if (section.id == sectionId) {
                        section.copy(name = name)
                    } else {
                        section
                    }
                }
            )
        }
    }

    /**
     * Export a single section
     */
    fun exportSection(section: VocalSection) {
        val vocalFile = _uiState.value.vocalFile ?: return
        val outputDir = File(context.cacheDir, "sections")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, "${section.name}.wav")

        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.PROCESSING) }

            cutVocalSectionUseCase(
                inputFile = vocalFile,
                outputFile = outputFile,
                startTimeMs = section.startTimeMs,
                endTimeMs = section.endTimeMs,
                onProgress = { progress, message ->
                    _uiState.update {
                        it.copy(progress = ProcessingProgress(ProcessingStage.PROCESSING, progress, message))
                    }
                }
            ).onSuccess { file ->
                val exportResult = SectionExportResult(
                    sectionId = section.id,
                    sectionName = section.name,
                    outputFile = file,
                    durationMs = section.endTimeMs - section.startTimeMs,
                    success = true
                )
                _uiState.update {
                    it.copy(
                        exportedSections = it.exportedSections + exportResult,
                        processingState = ProcessingState.IDLE,
                        progress = ProcessingProgress(ProcessingStage.COMPLETE, 100, "Section exported")
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.ERROR,
                        errorMessage = "Export failed: ${error.message}"
                    )
                }
            }
        }
    }

    /**
     * Export all sections
     */
    fun exportAllSections() {
        val vocalFile = _uiState.value.vocalFile ?: return
        val sections = _uiState.value.vocalSections
        if (sections.isEmpty()) return

        val outputDir = File(context.cacheDir, "sections")
        if (!outputDir.exists()) outputDir.mkdirs()

        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.PROCESSING) }

            exportVocalSectionsUseCase(
                inputFile = vocalFile,
                sections = sections,
                outputDir = outputDir,
                onProgress = { progress, message ->
                    _uiState.update {
                        it.copy(progress = ProcessingProgress(ProcessingStage.PROCESSING, progress, message))
                    }
                }
            ).onSuccess { results ->
                _uiState.update {
                    it.copy(
                        exportedSections = results,
                        processingState = ProcessingState.IDLE,
                        progress = ProcessingProgress(ProcessingStage.COMPLETE, 100, "All sections exported")
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.ERROR,
                        errorMessage = "Export failed: ${error.message}"
                    )
                }
            }
        }
    }

    /**
     * Create archive with all exported sections
     */
    fun createSectionsArchive() {
        val exportedSections = _uiState.value.exportedSections
        if (exportedSections.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.PROCESSING) }

            createSectionsArchiveUseCase(
                sections = exportedSections,
                archiveName = "vocal_sections_${System.currentTimeMillis()}"
            ).onSuccess { archiveFile ->
                _uiState.update {
                    it.copy(
                        resultFile = archiveFile,
                        processingState = ProcessingState.SUCCESS,
                        progress = ProcessingProgress(ProcessingStage.COMPLETE, 100, "Archive created")
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.ERROR,
                        errorMessage = "Archive creation failed: ${error.message}"
                    )
                }
            }
        }
    }

    /**
     * Clear all sections
     */
    fun clearAllSections() {
        _uiState.update {
            it.copy(
                vocalSections = emptyList(),
                selectedSection = null,
                exportedSections = emptyList()
            )
        }
    }

    // ==================== Editor Playback ====================

    /**
     * Set preview position
     */
    fun setPreviewPosition(positionMs: Long) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(previewPositionMs = positionMs)
            )
        }
    }

    /**
     * Toggle playback state
     */
    fun togglePlayback() {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(isPlaying = !it.editorState.isPlaying)
            )
        }
    }

    /**
     * Toggle loop mode
     */
    fun toggleLoopMode() {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(isLooping = !it.editorState.isLooping)
            )
        }
    }

    /**
     * Update volume
     */
    fun updateVolume(volume: Float) {
        _uiState.update {
            it.copy(
                editorState = it.editorState.copy(volume = volume.coerceIn(0f, 1f))
            )
        }
    }

    /**
     * Select/deselect section for batch operations
     */
    fun toggleSectionSelection(sectionId: String) {
        _uiState.update { state ->
            val currentSelection = state.editorState.selectedSections
            val newSelection = if (sectionId in currentSelection) {
                currentSelection - sectionId
            } else {
                currentSelection + sectionId
            }
            state.copy(
                editorState = state.editorState.copy(selectedSections = newSelection)
            )
        }
    }

    // ==================== General ====================

    /**
     * Reset to initial state
     */
    fun reset() {
        viewModelScope.launch {
            cleanupCacheUseCase()
            _uiState.update {
                MainUiState()
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
