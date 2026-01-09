package com.example.vocalremover.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.vocalremover.data.*
import com.example.vocalremover.domain.ProcessingConfig
import com.example.vocalremover.domain.ProcessingState
import com.example.vocalremover.usecase.ProcessAudioLocallyUseCase
import com.example.vocalremover.usecase.ProcessAudioUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _processingState = MutableLiveData<ProcessingState>(ProcessingState.Idle)
    val processingState: LiveData<ProcessingState> = _processingState

    // In a real Hilt app, these would be injected
    private val commandsBuilder = CommandsBuilder()
    private val ffmpegRunner = FfmpegRunner()
    private val inputResolver = InputResolver(application)
    private val silenceParser = SilencedetectParser()
    private val segmentSlicer = SegmentSlicer(ffmpegRunner, commandsBuilder)
    private val zipper = Zipper()

    private val localAudioProcessor = LocalAudioProcessor(
        inputResolver, ffmpegRunner, commandsBuilder, silenceParser, segmentSlicer, zipper
    )
    private val processAudioLocallyUseCase = ProcessAudioLocallyUseCase(localAudioProcessor)
    private val processAudioUseCase = ProcessAudioUseCase(processAudioLocallyUseCase)

    fun processAudio(uri: Uri) {
        val config = ProcessingConfig(inputUri = uri)

        viewModelScope.launch {
            processAudioUseCase(config)
                .onEach { state ->
                    _processingState.postValue(state)
                }
                .launchIn(this)
        }
    }
}
