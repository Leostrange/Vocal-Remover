package com.example.vocalremover.usecase

import com.example.vocalremover.domain.ProcessingConfig
import com.example.vocalremover.domain.ProcessingState
import kotlinx.coroutines.flow.Flow

class ProcessAudioUseCase(
    private val localUseCase: ProcessAudioLocallyUseCase
) {
    operator fun invoke(config: ProcessingConfig, isLocal: Boolean = true): Flow<ProcessingState> {
        return localUseCase(config)
    }
}
