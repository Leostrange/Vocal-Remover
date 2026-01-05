package com.example.vocalremover

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.vocalremover.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ProcessingViewModel by viewModels()
    private val workManager by lazy { WorkManager.getInstance(this) }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                persistUriPermission(uri)
                viewModel.updateSelection(uri, resolveFileName(uri))
            }
        }

    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startProcessingInternal()
            } else {
                Toast.makeText(
                    this,
                    "Для сохранения в Загрузки нужно разрешение на запись",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeState()
        observeWork()
    }

    private fun setupListeners() {
        binding.selectFileButton.setOnClickListener { filePicker.launch(arrayOf("audio/*")) }
        binding.startProcessingButton.setOnClickListener { startProcessing() }
        binding.cancelProcessingButton.setOnClickListener {
            workManager.cancelUniqueWork(AudioProcessWorker.WORK_NAME)
        }
        binding.openZipButton.setOnClickListener { viewModel.uiState.value.resultUri?.let(::openZip) }
        binding.shareZipButton.setOnClickListener { viewModel.uiState.value.resultUri?.let(::shareZip) }
        binding.copyLogButton.setOnClickListener { copyLogToClipboard() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.progress = state.progressPercent
                    binding.progressPercent.text = "${state.progressPercent}%"
                    binding.progressStep.text = state.stepMessage

                    binding.statusText.text = state.statusMessage
                    binding.selectedFileText.text =
                        if (state.selectedUri != null) state.selectedName else "Файл не выбран"

                    binding.startProcessingButton.isEnabled = state.selectedUri != null && !state.isProcessing
                    binding.cancelProcessingButton.isVisible = state.isProcessing

                    binding.openZipButton.isVisible = state.resultUri != null
                    binding.shareZipButton.isVisible = state.resultUri != null

                    binding.errorText.isVisible = state.errorMessage != null
                    binding.errorText.text = state.errorMessage.orEmpty()
                    binding.copyLogButton.isVisible = state.logTail?.isNotBlank() == true
                }
            }
        }
    }

    private fun observeWork() {
        workManager.getWorkInfosForUniqueWorkLiveData(AudioProcessWorker.WORK_NAME)
            .observe(this) { infos ->
                viewModel.updateFromWorkInfo(infos.firstOrNull())
            }
    }

    private fun startProcessing() {
        if (viewModel.uiState.value.isProcessing) {
            Toast.makeText(this, "Обработка уже запущена", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        startProcessingInternal()
    }

    private fun startProcessingInternal() {
        val selectedUri = viewModel.uiState.value.selectedUri
        if (selectedUri == null) {
            Toast.makeText(this, "Сначала выберите аудио файл", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.clearResult()

        val threshold = parseDouble(binding.thresholdInput.text.toString(), -35.0)
        val minSilence = parseDouble(binding.minSilenceInput.text.toString(), 0.35)
        val minSegment = parseDouble(binding.minSegmentInput.text.toString(), 1.0)
        val padding = parseDouble(binding.paddingInput.text.toString(), 0.15)

        val requestData = AudioProcessWorker.buildInputData(
            audioUri = selectedUri,
            silenceThreshold = threshold,
            minSilence = minSilence,
            minSegment = minSegment,
            padding = padding
        )

        val workRequest = OneTimeWorkRequestBuilder<AudioProcessWorker>()
            .setInputData(requestData)
            .addTag(AudioProcessWorker.WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            AudioProcessWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        Toast.makeText(this, "Обработка запущена в фоне", Toast.LENGTH_SHORT).show()
    }

    private fun parseDouble(value: String, fallback: Double): Double {
        return value.replace(",", ".").toDoubleOrNull() ?: fallback
    }

    private fun resolveFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: "audio_file"
    }

    private fun persistUriPermission(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Игнорируем, если невозможно закрепить разрешение
        }
    }

    private fun openZip(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Открыть ZIP")) }
            .onFailure {
                Toast.makeText(this, "Не удалось открыть ZIP: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun shareZip(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Поделиться ZIP")) }
            .onFailure {
                Toast.makeText(this, "Не удалось поделиться: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun copyLogToClipboard() {
        val log = viewModel.uiState.value.logTail.orEmpty()
        if (log.isBlank()) return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FFmpeg log", log))
        Toast.makeText(this, "Лог скопирован", Toast.LENGTH_SHORT).show()
    }
}
