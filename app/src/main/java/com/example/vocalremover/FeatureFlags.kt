package com.example.vocalremover

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

object FeatureFlags {
    val isFfmpegAvailable: Boolean by lazy {
        val classAvailable = runCatching {
            Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        }.isSuccess

        if (!classAvailable) {
            return@lazy false
        }

        runCatching {
            val session = FFmpegKit.execute("-version")
            ReturnCode.isSuccess(session.returnCode)
        }.getOrDefault(false)
    }
}
