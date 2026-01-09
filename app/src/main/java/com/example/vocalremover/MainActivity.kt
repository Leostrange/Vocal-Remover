package com.example.vocalremover

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.vocalremover.domain.ProcessingState
import com.example.vocalremover.domain.ProcessingStep
import com.example.vocalremover.ui.theme.VocalRemoverTheme
import com.example.vocalremover.viewmodel.MainViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.processAudio(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VocalRemoverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onPickFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                        onShareFile = { file -> shareFile(file) }
                    )
                }
            }
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Result"))
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPickFile: () -> Unit,
    onShareFile: (File) -> Unit
) {
    val state by viewModel.processingState.observeAsState(initial = ProcessingState.Idle)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onPickFile,
            enabled = state is ProcessingState.Idle || state is ProcessingState.Error || state is ProcessingState.Success,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Audio File")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val s = state) {
            is ProcessingState.Idle -> {
                Text("Ready to process audio.")
            }
            is ProcessingState.Processing -> {
                Text("Processing: ${s.step.name}")
                LinearProgressIndicator(
                    progress = s.progress,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            is ProcessingState.Success -> {
                Text("Processing Complete!", color = MaterialTheme.colorScheme.primary)
                Text("Result ready.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onShareFile(s.resultFile) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Result")
                }
            }
            is ProcessingState.Error -> {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
