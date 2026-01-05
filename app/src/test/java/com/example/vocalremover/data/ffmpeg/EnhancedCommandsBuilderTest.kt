package com.example.vocalremover.data.ffmpeg

import com.example.vocalremover.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for EnhancedCommandsBuilder
 */
class EnhancedCommandsBuilderTest {

    private val commandsBuilder = EnhancedCommandsBuilder()
    private val inputFile = File("/path/to/input.mp3")
    private val outputFile = File("/path/to/output.wav")
    private val tempDir = File("/tmp/test")

    @Test
    fun `buildVocalIsolationCommand should handle basic filter model`() {
        val command = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.BASIC_FILTER
        )
        
        assertTrue(command.contains("highpass=f=80"))
        assertTrue(command.contains("lowpass=f=8000"))
        assertTrue(command.contains("pan=mono|c0=c0+c1"))
        assertTrue(command.contains(inputFile.absolutePath))
        assertTrue(command.contains(outputFile.absolutePath))
        assertTrue(command.startsWith("-y -i"))
    }

    @Test
    fun `buildVocalIsolationCommand should handle spleeter model`() {
        val command = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.SPELEETER_2STEM
        )
        
        assertTrue(command.contains("aecho=0.8:0.88:60:0.4"))
        assertTrue(command.contains("highpass=f=120"))
        assertTrue(command.contains("lowpass=f=6000"))
        assertTrue(command.contains(inputFile.absolutePath))
        assertTrue(command.contains(outputFile.absolutePath))
    }

    @Test
    fun `buildVocalIsolationCommand should handle demucs model`() {
        val command = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.DEMUCS_4STEM
        )
        
        assertTrue(command.contains("aecho=0.9:0.9:40:0.3"))
        assertTrue(command.contains("highpass=f=100"))
        assertTrue(command.contains("lowpass=f=7000"))
        assertTrue(command.contains("compand=attacks=0.1:decays=0.8"))
    }

    @Test
    fun `buildVocalIsolationCommand should handle lalal model`() {
        val command = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.LALAL_5STEM
        )
        
        assertTrue(command.contains("aecho=0.8:0.9:50:0.35"))
        assertTrue(command.contains("highpass=f=150"))
        assertTrue(command.contains("lowpass=f=6500"))
        assertTrue(command.contains("compand=attacks=0.05:decays=0.3"))
    }

    @Test
    fun `buildSilenceDetectCommand should build correct command`() {
        val config = ProcessingConfig(
            silenceThresholdDb = -40.0,
            minSilenceDurationSec = 1.5
        )
        
        val command = commandsBuilder.buildSilenceDetectCommand(inputFile, config)
        
        assertTrue(command.contains("silencedetect=noise=-40.0dB:d=1.5"))
        assertTrue(command.contains(inputFile.absolutePath))
        assertTrue(command.contains("-f null -"))
    }

    @Test
    fun `buildEnhancementCommand should include noise reduction when enabled`() {
        val config = ProcessingConfig(enableNoiseReduction = true)
        
        val command = commandsBuilder.buildEnhancementCommand(
            inputFile,
            outputFile,
            config
        )
        
        assertTrue(command.contains("highpass=f=80"))
        assertTrue(command.contains(inputFile.absolutePath))
        assertTrue(command.contains(outputFile.absolutePath))
    }

    @Test
    fun `buildEnhancementCommand should include normalization when enabled`() {
        val config = ProcessingConfig(enableNormalization = true)
        
        val command = commandsBuilder.buildEnhancementCommand(
            inputFile,
            outputFile,
            config
        )
        
        assertTrue(command.contains("loudnorm"))
        assertTrue(command.contains("pan=mono|c0=c0+0.3*c1"))
    }

    @Test
    fun `buildEnhancementCommand should exclude disabled features`() {
        val config = ProcessingConfig(
            enableNoiseReduction = false,
            enableNormalization = false
        )
        
        val command = commandsBuilder.buildEnhancementCommand(
            inputFile,
            outputFile,
            config
        )
        
        assertFalse(command.contains("highpass=f=80"))
        assertFalse(command.contains("loudnorm"))
        assertTrue(command.contains("pan=mono|c0=c0+0.3*c1")) // Channel mixing always included
    }

    @Test
    fun `buildSliceCommand should create correct segment command`() {
        val segment = Segment(start = 10.5, end = 30.0)
        
        val command = commandsBuilder.buildSliceCommand(inputFile, segment, outputFile)
        
        assertTrue(command.contains("-ss 10.5"))
        assertTrue(command.contains("-t 19.5"))
        assertTrue(command.contains(inputFile.absolutePath))
        assertTrue(command.contains(outputFile.absolutePath))
    }

    @Test
    fun `buildConversionCommand should handle WAV format`() {
        val config = ProcessingConfig(
            outputFormat = AudioFormat.WAV,
            sampleRate = 48000,
            channels = 1
        )
        
        val command = commandsBuilder.buildConversionCommand(
            inputFile,
            outputFile,
            AudioFormat.WAV,
            config
        )
        
        assertTrue(command.contains("pcm_s16le"))
        assertTrue(command.contains("-ar 48000"))
        assertTrue(command.contains("-ac 1"))
        assertFalse(command.contains("-b:a"))
    }

    @Test
    fun `buildConversionCommand should handle MP3 format with bitrate`() {
        val config = ProcessingConfig(
            outputFormat = AudioFormat.MP3,
            outputBitrate = 320,
            sampleRate = 44100
        )
        
        val command = commandsBuilder.buildConversionCommand(
            inputFile,
            outputFile,
            AudioFormat.MP3,
            config
        )
        
        assertTrue(command.contains("libmp3lame"))
        assertTrue(command.contains("-ar 44100"))
        assertTrue(command.contains("-b:a 320k"))
    }

    @Test
    fun `buildConversionCommand should handle AAC format`() {
        val config = ProcessingConfig(
            outputFormat = AudioFormat.AAC,
            outputBitrate = 256,
            channels = 2
        )
        
        val command = commandsBuilder.buildConversionCommand(
            inputFile,
            outputFile,
            AudioFormat.AAC,
            config
        )
        
        assertTrue(command.contains("aac"))
        assertTrue(command.contains("-ac 2"))
        assertTrue(command.contains("-b:a 256k"))
    }

    @Test
    fun `buildMetadataCommand should be simple input command`() {
        val command = commandsBuilder.buildMetadataCommand(inputFile)
        
        assertEquals("-i \"${inputFile.absolutePath}\" -f null -", command)
    }

    @Test
    fun `buildAnalysisCommand should generate spectrogram`() {
        val command = commandsBuilder.buildAnalysisCommand(inputFile, outputFile)
        
        assertTrue(command.contains("showwavespic=s=1024x400"))
        assertTrue(command.contains(inputFile.absolutePath))
        assertTrue(command.contains(outputFile.absolutePath))
    }

    @Test
    fun `buildBatchSeparationCommand should handle 2-stems model`() {
        val command = commandsBuilder.buildBatchSeparationCommand(
            inputFile,
            tempDir,
            VocalSeparationModel.SPELEETER_2STEM
        )
        
        assertTrue(command.contains("vocals.wav"))
        assertTrue(command.contains("accompanying.wav"))
        assertTrue(command.contains(tempDir.absolutePath))
    }

    @Test
    fun `buildBatchSeparationCommand should handle 4-stems model`() {
        val command = commandsBuilder.buildBatchSeparationCommand(
            inputFile,
            tempDir,
            VocalSeparationModel.DEMUCS_4STEM
        )
        
        assertTrue(command.contains("vocals.wav"))
        assertTrue(command.contains("drums.wav"))
        assertTrue(command.contains("bass.wav"))
        assertTrue(command.contains("other.wav"))
    }

    @Test
    fun `buildBatchSeparationCommand should handle 5-stems model`() {
        val command = commandsBuilder.buildBatchSeparationCommand(
            inputFile,
            tempDir,
            VocalSeparationModel.LALAL_5STEM
        )
        
        assertTrue(command.contains("vocals.wav"))
        assertTrue(command.contains("drums.wav"))
        assertTrue(command.contains("bass.wav"))
        assertTrue(command.contains("piano.wav"))
        assertTrue(command.contains("other.wav"))
    }

    @Test
    fun `buildBatchSeparationCommand should fallback for unknown stems count`() {
        // This test might fail if we add new models without updating the batch command logic
        val unknownModel = VocalSeparationModel.BASIC_FILTER // Has 2 stems
        val command = commandsBuilder.buildBatchSeparationCommand(
            inputFile,
            tempDir,
            unknownModel
        )
        
        // Should fall back to basic filter command
        assertTrue(command.contains("vocal_enhanced.wav"))
    }

    @Test
    fun `command should be properly formatted with quotes for file paths`() {
        val command = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.BASIC_FILTER
        )
        
        assertTrue(command.contains("\"${inputFile.absolutePath}\""))
        assertTrue(command.contains("\"${outputFile.absolutePath}\""))
    }

    @Test
    fun `different models should produce different commands`() {
        val basicCommand = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.BASIC_FILTER
        )
        
        val spleeterCommand = commandsBuilder.buildVocalIsolationCommand(
            inputFile,
            outputFile,
            VocalSeparationModel.SPELEETER_2STEM
        )
        
        assertNotEquals(basicCommand, spleeterCommand)
        assertFalse(basicCommand.contains("aecho"))
        assertTrue(spleeterCommand.contains("aecho"))
    }
}