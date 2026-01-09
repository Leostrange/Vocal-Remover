package com.example.vocalremover.domain

import java.io.File

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class Processing(val step: ProcessingStep, val progress: Float) : ProcessingState()
    data class Success(val resultFile: File) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
