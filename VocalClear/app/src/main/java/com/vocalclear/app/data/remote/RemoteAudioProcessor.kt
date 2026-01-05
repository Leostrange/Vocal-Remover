package com.vocalclear.app.data.remote

import com.vocalclear.app.domain.model.ProcessingResult
import com.vocalclear.app.domain.model.ResultStatus
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API service interface for server-side audio processing
 */
interface VocalClearApi {
    @Multipart
    @POST("process")
    suspend fun processAudio(
        @Part file: MultipartBody.Part,
        @Part("mode") mode: okhttp3.RequestBody
    ): Response<ProcessingResponse>

    @POST("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("modes")
    suspend fun getAvailableModes(): Response<ModesResponse>
}

/**
 * Response from processing API
 */
data class ProcessingResponse(
    val success: Boolean,
    val resultUrl: String?,
    val error: String?,
    val processingTimeMs: Long
)

/**
 * Health check response
 */
data class HealthResponse(
    val status: String,
    val version: String,
    val modelsLoaded: List<String>
)

/**
 * Available modes response
 */
data class ModesResponse(
    val modes: List<String>,
    val defaultMode: String
)

/**
 * Remote data source for online processing
 */
@Singleton
class RemoteAudioProcessor @Inject constructor(
    private val api: VocalClearApi
) {
    /**
     * Upload audio file to server for processing
     */
    suspend fun uploadAudio(
        inputStream: InputStream,
        fileName: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<ProcessingResult> {
        return try {
            onProgress(10, "Uploading file...")

            // Create temp file from input stream
            val tempFile = File.createTempFile("upload_", "_$fileName")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            // Create multipart request
            val requestBody = tempFile.asRequestBody("audio/*".toMediaTypeOrNull())
            val multipart = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val modeBody = "spleeter_2stem".toRequestBody("text/plain".toMediaTypeOrNull())

            onProgress(30, "Uploaded, processing on server...")

            val response = api.processAudio(multipart, modeBody)

            // Clean up temp file
            tempFile.delete()

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    onProgress(90, "Downloading result...")

                    Result.success(
                        ProcessingResult(
                            status = ResultStatus.SUCCESS,
                            errorMessage = null,
                            processingTimeMs = body.processingTimeMs
                        )
                    )
                } else {
                    Result.success(
                        ProcessingResult(
                            status = ResultStatus.ERROR,
                            errorMessage = body?.error ?: "Unknown error"
                        )
                    )
                }
            } else {
                Result.success(
                    ProcessingResult(
                        status = ResultStatus.ERROR,
                        errorMessage = "Server error: ${response.code()}"
                    )
                )
            }
        } catch (e: Exception) {
            Result.success(
                ProcessingResult(
                    status = ResultStatus.ERROR,
                    errorMessage = e.message ?: "Network error"
                )
            )
        }
    }

    /**
     * Check if server is available
     */
    suspend fun checkHealth(): Boolean {
        return try {
            val response = api.healthCheck()
            response.isSuccessful && response.body()?.status == "ok"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get available processing modes from server
     */
    suspend fun getAvailableModes(): List<String> {
        return try {
            val response = api.getAvailableModes()
            if (response.isSuccessful) {
                response.body()?.modes ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
