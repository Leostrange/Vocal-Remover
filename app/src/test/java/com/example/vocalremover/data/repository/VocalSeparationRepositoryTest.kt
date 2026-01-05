package com.example.vocalremover.data.repository

import android.content.Context
import com.example.vocalremover.domain.*
import com.example.vocalremover.data.ffmpeg.FFmpegRunner
import com.example.vocalremover.data.ffmpeg.EnhancedCommandsBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.File

/**
 * Unit tests for VocalSeparationRepository
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VocalSeparationRepositoryTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockFFmpegRunner: FFmpegRunner

    @Mock
    private lateinit var mockCommandsBuilder: EnhancedCommandsBuilder

    private lateinit var repository: VocalSeparationRepositoryImpl

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = VocalSeparationRepositoryImpl(
            context = mockContext,
            ffmpegRunner = mockFFmpegRunner,
            commandsBuilder = mockCommandsBuilder
        )
    }

    @Test
    fun `validateAudioFile should return success for valid file`() = runTest(testDispatcher) {
        // Given
        val validFile = mock<File>()
        `when`(validFile.exists()).thenReturn(true)
        `when`(validFile.length()).thenReturn(1024L)
        
        // Mock FFmpegRunner to return successful metadata
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success("Duration: 00:03:30.00"))
        `when`(mockCommandsBuilder.buildMetadataCommand(validFile)).thenReturn("-i test")

        // When
        val result = repository.validateAudioFile(validFile)

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrNull()
        assertNotNull(metadata)
        assertEquals(validFile, metadata?.file)
    }

    @Test
    fun `validateAudioFile should return failure for non-existent file`() = runTest(testDispatcher) {
        // Given
        val nonExistentFile = mock<File>()
        `when`(nonExistentFile.exists()).thenReturn(false)

        // When
        val result = repository.validateAudioFile(nonExistentFile)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("does not exist") == true)
    }

    @Test
    fun `validateAudioFile should return failure for empty file`() = runTest(testDispatcher) {
        // Given
        val emptyFile = mock<File>()
        `when`(emptyFile.exists()).thenReturn(true)
        `when`(emptyFile.length()).thenReturn(0L)

        // When
        val result = repository.validateAudioFile(emptyFile)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun `getAudioMetadata should parse FFmpeg output correctly`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("test.mp3")
        val ffmpegOutput = """
            Input #0, mp3, from 'test.mp3':
              Duration: 00:03:30.00, start: 0.000000, bitrate: 320 kb/s
              Stream #0.0: Audio: aac, 44100 Hz, stereo
            Metadata:
              title           : Test Song
        """.trimIndent()
        
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success(ffmpegOutput))
        `when`(mockCommandsBuilder.buildMetadataCommand(audioFile)).thenReturn("-i test.mp3")

        // When
        val result = repository.getAudioMetadata(audioFile)

        // Then
        assertTrue(result.isSuccess)
        val metadata = result.getOrNull()
        assertNotNull(metadata)
        assertEquals(210.0, metadata?.duration, 0.1) // 3 minutes 30 seconds
        assertEquals("aac", metadata?.format)
        assertEquals(44100, metadata?.sampleRate)
        assertEquals(2, metadata?.channels)
        assertEquals(320, metadata?.bitrate)
    }

    @Test
    fun `separateVocals with basic filter should execute successfully`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val outputFile = File("output.wav")
        val config = ProcessingConfig(vocalSeparationModel = VocalSeparationModel.BASIC_FILTER)
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(5_000_000L) // 5MB
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success("Success"))
        `when`(mockCommandsBuilder.buildVocalIsolationCommand(
            any(File::class.java),
            any(File::class.java),
            eq(VocalSeparationModel.BASIC_FILTER)
        )).thenReturn("-y -i input.mp3 -af \"filter\" output.wav")

        val progressStates = mutableListOf<ProcessingState>()
        val progressCallback: (ProcessingState) -> Unit = { progressStates.add(it) }

        // When
        val result = repository.separateVocals(audioFile, config, progressCallback)

        // Then
        assertTrue(result.isSuccess)
        val separationResult = result.getOrNull()
        assertNotNull(separationResult)
        assertEquals("Basic Filter", separationResult?.modelUsed)
        assertEquals(0.6f, separationResult?.qualityScore, 0.1f)
        
        // Verify progress updates
        assertTrue(progressStates.any { it is ProcessingState.Processing && it.step == ProcessingStep.SEPARATING })
    }

    @Test
    fun `separateVocals should handle FFmpeg errors gracefully`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val config = ProcessingConfig(vocalSeparationModel = VocalSeparationModel.SPELEETER_2STEM)
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(5_000_000L)
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(
            Result.failure(Exception("FFmpeg error: Invalid input"))
        )

        val progressCallback: (ProcessingState) -> Unit = { }

        // When
        val result = repository.separateVocals(audioFile, config, progressCallback)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is VocalSeparationError)
        assertEquals(ErrorType.FFMPEG_ERROR, (error as VocalSeparationError).type)
    }

    @Test
    fun `separateVocals with spleeter model should use enhanced filtering`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val outputFile = File("output.wav")
        val config = ProcessingConfig(vocalSeparationModel = VocalSeparationModel.SPELEETER_2STEM)
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(10_000_000L) // 10MB
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success("Success"))
        `when`(mockCommandsBuilder.buildVocalIsolationCommand(
            any(File::class.java),
            any(File::class.java),
            eq(VocalSeparationModel.SPELEETER_2STEM)
        )).thenReturn("-y -i input.mp3 -af \"aecho filter\" output.wav")

        val progressCallback: (ProcessingState) -> Unit = { }

        // When
        val result = repository.separateVocals(audioFile, config, progressCallback)

        // Then
        assertTrue(result.isSuccess)
        val separationResult = result.getOrNull()
        assertNotNull(separationResult)
        assertEquals("Spleeter 2-Stems", separationResult?.modelUsed)
        assertEquals(0.75f, separationResult?.qualityScore, 0.1f)
    }

    @Test
    fun `separateVocals should apply enhancements when enabled`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val config = ProcessingConfig(
            vocalSeparationModel = VocalSeparationModel.BASIC_FILTER,
            enableAudioEnhancement = true,
            enableNormalization = true,
            enableNoiseReduction = true
        )
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(5_000_000L)
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success("Success"))
        `when`(mockCommandsBuilder.buildVocalIsolationCommand(
            any(File::class.java),
            any(File::class.java),
            any(VocalSeparationModel::class.java)
        )).thenReturn("-y -i input.mp3 -af \"filter\" output.wav")
        `when`(mockCommandsBuilder.buildEnhancementCommand(
            any(File::class.java),
            any(File::class.java),
            any(ProcessingConfig::class.java)
        )).thenReturn("-y -i output.wav -af \"enhancement\" enhanced.wav")

        val progressCallback: (ProcessingState) -> Unit = { }

        // When
        val result = repository.separateVocals(audioFile, config, progressCallback)

        // Then
        assertTrue(result.isSuccess)
        val separationResult = result.getOrNull()
        assertNotNull(separationResult)
        // Enhancement should improve quality score
        assertTrue(separationResult?.qualityScore ?: 0f > 0.6f)
    }

    @Test
    fun `separateVocals should skip enhancements when disabled`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val config = ProcessingConfig(
            vocalSeparationModel = VocalSeparationModel.BASIC_FILTER,
            enableAudioEnhancement = false
        )
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(5_000_000L)
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success("Success"))
        `when`(mockCommandsBuilder.buildVocalIsolationCommand(
            any(File::class.java),
            any(File::class.java),
            any(VocalSeparationModel::class.java)
        )).thenReturn("-y -i input.mp3 -af \"filter\" output.wav")

        val progressCallback: (ProcessingState) -> Unit = { }

        // When
        val result = repository.separateVocals(audioFile, config, progressCallback)

        // Then
        assertTrue(result.isSuccess)
        val separationResult = result.getOrNull()
        assertNotNull(separationResult)
        // Should have basic quality score
        assertEquals(0.6f, separationResult?.qualityScore, 0.1f)
    }

    @Test
    fun `different models should have different quality scores`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val config = ProcessingConfig()
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(5_000_000L)
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenReturn(Result.success("Success"))
        `when`(mockCommandsBuilder.buildVocalIsolationCommand(
            any(File::class.java),
            any(File::class.java),
            any(VocalSeparationModel::class.java)
        )).thenReturn("-y -i input.mp3 -af \"filter\" output.wav")

        // Test different models
        val models = listOf(
            VocalSeparationModel.BASIC_FILTER,
            VocalSeparationModel.SPELEETER_2STEM,
            VocalSeparationModel.DEMUCS_4STEM,
            VocalSeparationModel.LALAL_5STEM
        )

        val qualityScores = models.map { model ->
            val modelConfig = config.copy(vocalSeparationModel = model)
            val progressCallback: (ProcessingState) -> Unit = { }
            val result = repository.separateVocals(audioFile, modelConfig, progressCallback)
            result.getOrNull()?.qualityScore ?: 0f
        }

        // Then
        assertTrue(qualityScores[0] < qualityScores[1]) // Basic < Spleeter
        assertTrue(qualityScores[1] < qualityScores[2]) // Spleeter < Demucs
        assertTrue(qualityScores[2] < qualityScores[3]) // Demucs < LALAL
    }

    @Test
    fun `repository should handle exceptions during processing`() = runTest(testDispatcher) {
        // Given
        val audioFile = File("input.mp3")
        val config = ProcessingConfig()
        
        `when`(audioFile.exists()).thenReturn(true)
        `when`(audioFile.length()).thenReturn(5_000_000L)
        `when`(mockFFmpegRunner.executeCommand(anyString())).thenThrow(RuntimeException("Unexpected error"))

        val progressCallback: (ProcessingState) -> Unit = { }

        // When
        val result = repository.separateVocals(audioFile, config, progressCallback)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is VocalSeparationError)
        assertEquals(ErrorType.PROCESSING_ERROR, (error as VocalSeparationError).type)
    }
}