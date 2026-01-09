package com.example.vocalremover.domain

enum class ProcessingStep {
    IDLE,
    ANALYZING,
    SEPARATING,
    SLICING,
    ZIPPING,
    COMPLETED,
    ERROR
}
