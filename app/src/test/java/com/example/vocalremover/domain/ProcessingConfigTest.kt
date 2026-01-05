package com.example.vocalremover.domain

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ProcessingConfig
 */
class ProcessingConfigTest {

    @Test
    fun `ProcessingConfig should have default values`() {
        val config = ProcessingConfig()
        
        assertEquals(VocalSeparationModel.BASIC_FILTER, config.vocalSeparationModel)
        assertEquals(-30.0, config.silenceThresholdDb, 0.01)
        assertEquals(0.5, config.minSilenceDurationSec, 0.01)
        assertEquals(0.3, config.minSegmentDurationSec, 0.01)
        assertEquals(AudioFormat.WAV, config.outputFormat)
        assertEquals(0.05, config.trimPaddingSec, 0.01)
        assertTrue(config.enableNoiseReduction)
        assertTrue(config.enableNormalization)
        assertTrue(config.enableAudioEnhancement)
        assertEquals(192, config.outputBitrate)
        assertEquals(44100, config.sampleRate)
        assertEquals(2, config.channels)
        assertEquals(ProcessingMode.LOCAL, config.processingMode)
        assertNull(config.serverConfig)
    }

    @Test
    fun `ProcessingConfig should allow custom values`() {
        val config = ProcessingConfig(
            vocalSeparationModel = VocalSeparationModel.SPELEETER_2STEM,
            silenceThresholdDb = -40.0,
            minSilenceDurationSec = 1.0,
            outputFormat = AudioFormat.MP3,
            outputBitrate = 320,
            enableAudioEnhancement = false
        )
        
        assertEquals(VocalSeparationModel.SPELEETER_2STEM, config.vocalSeparationModel)
        assertEquals(-40.0, config.silenceThresholdDb, 0.01)
        assertEquals(1.0, config.minSilenceDurationSec, 0.01)
        assertEquals(AudioFormat.MP3, config.outputFormat)
        assertEquals(320, config.outputBitrate)
        assertFalse(config.enableAudioEnhancement)
    }

    @Test
    fun `VocalSeparationModel should have correct properties`() {
        val model = VocalSeparationModel.SPELEETER_2STEM
        
        assertEquals("2-Stems (Spleeter)", model.displayName)
        assertEquals("Vocals + Music separation", model.description)
        assertEquals(2, model.stems)
        assertEquals(Quality.MEDIUM, model.quality)
        assertEquals(ProcessingTime.FAST, model.processingTime)
        assertEquals("spleeter/2stem", model.modelPath)
    }

    @Test
    fun `VocalSeparationModel getByDisplayName should work correctly`() {
        val model = VocalSeparationModel.getByDisplayName("4-Stems (Demucs)")
        assertEquals(VocalSeparationModel.DEMUCS_4STEM, model)
        
        val invalidModel = VocalSeparationModel.getByDisplayName("Invalid Model")
        assertNull(invalidModel)
    }

    @Test
    fun `Quality enum should have correct display names`() {
        assertEquals("Low", Quality.LOW.displayName)
        assertEquals("Medium", Quality.MEDIUM.displayName)
        assertEquals("High", Quality.HIGH.displayName)
        assertEquals("Very High", Quality.VERY_HIGH.displayName)
    }

    @Test
    fun `ProcessingTime enum should have correct properties`() {
        assertEquals("Instant", ProcessingTime.INSTANT.displayName)
        assertEquals(1..3, ProcessingTime.INSTANT.estimatedSeconds)
        
        assertEquals("Fast", ProcessingTime.FAST.displayName)
        assertEquals(5..15, ProcessingTime.FAST.estimatedSeconds)
    }

    @Test
    fun `ServerConfig should handle authentication types`() {
        val serverConfig = ServerConfig(
            url = "https://api.example.com",
            apiKey = "test-key",
            authenticationType = AuthenticationType.API_KEY
        )
        
        assertEquals("https://api.example.com", serverConfig.url)
        assertEquals("test-key", serverConfig.apiKey)
        assertEquals(AuthenticationType.API_KEY, serverConfig.authenticationType)
        assertEquals(100L * 1024 * 1024, serverConfig.maxFileSize)
    }

    @Test
    fun `AudioFormat should have correct properties`() {
        assertEquals("wav", AudioFormat.WAV.extension)
        assertEquals("audio/wav", AudioFormat.WAV.mimeType)
        assertEquals("WAV (Uncompressed)", AudioFormat.WAV.displayName)
        
        assertEquals("mp3", AudioFormat.MP3.extension)
        assertEquals("audio/mpeg", AudioFormat.MP3.mimeType)
        assertEquals("MP3 (Compressed)", AudioFormat.MP3.displayName)
    }
}