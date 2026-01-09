package com.example.vocalremover.domain

import android.net.Uri

data class ProcessingConfig(
    val inputUri: Uri,
    val bitrate: Int = 192,
    val format: String = "mp3"
)
