package com.example.vocalremover.data

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FfmpegRunner {
    suspend fun execute(command: Array<String>): String {
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val session = FFmpegKit.execute(command)
                if (ReturnCode.isSuccess(session.returnCode)) {
                    continuation.resume(session.output)
                } else {
                    continuation.resumeWithException(RuntimeException("FFmpeg failed: ${session.failStackTrace}"))
                }
            }
        }
    }
}
