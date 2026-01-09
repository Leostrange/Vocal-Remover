package com.example.vocalremover.usecase

import com.example.vocalremover.data.LocalAudioProcessor
import com.example.vocalremover.domain.ProcessingConfig
import com.example.vocalremover.domain.ProcessingState
import kotlinx.coroutines.flow.Flow

class ProcessAudioLocallyUseCase(
    private val localAudioProcessor: LocalAudioProcessor
) {
    operator fun invoke(config: ProcessingConfig): Flow<ProcessingState> {
        return localAudioProcessor.process(config)
    }
}
