package com.example.vocalremover

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProcessingUiState(
    val selectedUri: Uri? = null,
    val selectedName: String = "",
    val isProcessing: Boolean = false,
    val progressPercent: Int = 0,
    val stepMessage: String = "Готов к обработке",
    val statusMessage: String = "Готов к обработке",
    val resultUri: Uri? = null,
    val errorMessage: String? = null,
    val logTail: String? = null
)

class ProcessingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState

    fun updateSelection(uri: Uri?, displayName: String) {
        _uiState.update {
            it.copy(
                selectedUri = uri,
                selectedName = displayName,
                statusMessage = if (uri != null) "Файл готов к обработке" else "Файл не выбран",
                resultUri = null,
                errorMessage = null,
                logTail = null,
                progressPercent = 0,
                stepMessage = "Готов к обработке"
            )
        }
    }

    fun updateFromWorkInfo(workInfo: WorkInfo?) {
        viewModelScope.launch {
            if (workInfo == null) {
                _uiState.update { it.copy(isProcessing = false) }
                return@launch
            }

            when (workInfo.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getInt(AudioProcessWorker.KEY_PROGRESS, 0)
                    val step = workInfo.progress.getString(AudioProcessWorker.KEY_STEP)
                        ?: "Подготовка..."

                    _uiState.update {
                        it.copy(
                            isProcessing = true,
                            progressPercent = progress,
                            stepMessage = step,
                            statusMessage = "Идет обработка..."
                        )
                    }
                }

                WorkInfo.State.SUCCEEDED -> {
                    val uri = workInfo.outputData.getString(AudioProcessWorker.KEY_OUTPUT_URI)
                        ?.let(Uri::parse)
                    val logTail = workInfo.outputData.getString(AudioProcessWorker.KEY_LOG_TAIL)

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            progressPercent = 100,
                            stepMessage = "Готово",
                            statusMessage = "ZIP сохранен в Загрузках",
                            resultUri = uri,
                            errorMessage = null,
                            logTail = logTail
                        )
                    }
                }

                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString(AudioProcessWorker.KEY_ERROR_MESSAGE)
                        ?: "Ошибка обработки"
                    val logTail = workInfo.outputData.getString(AudioProcessWorker.KEY_LOG_TAIL)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = error,
                            statusMessage = "Произошла ошибка",
                            logTail = logTail
                        )
                    }
                }

                WorkInfo.State.CANCELLED -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = "Обработка отменена",
                            stepMessage = "Остановлено"
                        )
                    }
                }

                WorkInfo.State.BLOCKED -> {
                    _uiState.update { it.copy(isProcessing = false, statusMessage = "Ожидание очереди задач") }
                }
            }
        }
    }

    fun setError(message: String, logTail: String? = null) {
        _uiState.update {
            it.copy(
                isProcessing = false,
                errorMessage = message,
                statusMessage = message,
                logTail = logTail
            )
        }
    }

    fun setStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message, errorMessage = null) }
    }

    fun clearResult() {
        _uiState.update {
            it.copy(
                resultUri = null,
                errorMessage = null,
                logTail = null,
                progressPercent = 0,
                stepMessage = "Готов к обработке"
            )
        }
    }
}
