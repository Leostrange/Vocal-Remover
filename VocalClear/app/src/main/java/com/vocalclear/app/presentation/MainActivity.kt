package com.vocalclear.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vocalclear.app.R
import com.vocalclear.app.domain.model.ProcessingMode
import com.vocalclear.app.presentation.ui.sections.VocalSectionEditor
import com.vocalclear.app.presentation.ui.theme.VocalClearTheme
import com.vocalclear.app.presentation.viewmodel.MainUiState
import com.vocalclear.app.presentation.viewmodel.MainViewModel
import com.vocalclear.app.presentation.viewmodel.ProcessingState

/**
 * Главная активность приложения VocalClear.
 * Содержит UI на Jetpack Compose для выбора аудиофайла,
 * переключения режимов обработки, редактора секций вокала.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VocalClearTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VocalClearApp(viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Главный composable приложения с верхней панелью и снackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocalClearApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: Открыть настройки */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        MainContent(
            viewModel = viewModel,
            uiState = uiState,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Основной контент экрана.
 */
@Composable
fun MainContent(
    viewModel: MainViewModel,
    uiState: MainUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showModeDialog by remember { mutableStateOf(false) }
    var showSectionEditor by remember { mutableStateOf(false) }

    // Файловый picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectFile(it)
            showModeDialog = true
        }
    }

    // Проверка разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioPickerLauncher.launch("audio/*")
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppDescription()

        Spacer(modifier = Modifier.height(8.dp))

        FileSelectionCard(
            uiState = uiState,
            onSelectFile = {
                if (hasAudioPermission(context)) {
                    audioPickerLauncher.launch("audio/*")
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            },
            onClearFile = { viewModel.reset() }
        )

        ModeSelectionCard(
            uiState = uiState,
            onModeChanged = { mode -> viewModel.updateMode(mode) }
        )

        ProcessingCard(
            uiState = uiState,
            onStartProcessing = { viewModel.startProcessing() },
            onOpenSectionEditor = {
                showSectionEditor = true
            }
        )

        // Кнопка открытия редактора секций, если вокал готов
        if (uiState.processingState == ProcessingState.SUCCESS && uiState.vocalFile != null) {
            OutlinedButton(
                onClick = { showSectionEditor = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_section_editor))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Диалог выбора режима
    if (showModeDialog) {
        ModeSelectionDialog(
            onDismiss = { showModeDialog = false },
            onModeSelected = { mode ->
                viewModel.updateMode(mode)
                showModeDialog = false
            }
        )
    }

    // Полноэкранный редактор секций
    if (showSectionEditor && uiState.vocalFile != null) {
        SectionEditorBottomSheet(
            onDismiss = { showSectionEditor = false },
            viewModel = viewModel,
            uiState = uiState
        )
    }
}

/**
 * Карточка с описанием приложения.
 */
@Composable
fun AppDescription() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Карточка для выбора входного аудиофайла.
 */
@Composable
fun FileSelectionCard(
    uiState: MainUiState,
    onSelectFile: () -> Unit,
    onClearFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.input_file),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.selectedFile != null) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.selectedFile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(onClick = onClearFile) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onSelectFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.select_audio_file))
                }
            }
        }
    }
}

/**
 * Карточка выбора режима обработки.
 */
@Composable
fun ModeSelectionCard(
    uiState: MainUiState,
    onModeChanged: (ProcessingMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.processing_mode),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.processingConfig.mode == ProcessingMode.OFFLINE,
                    onClick = { onModeChanged(ProcessingMode.OFFLINE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.offline_mode))
                }

                SegmentedButton(
                    selected = uiState.processingConfig.mode == ProcessingMode.ONLINE,
                    onClick = { onModeChanged(ProcessingMode.ONLINE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(stringResource(R.string.online_mode))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (uiState.processingConfig.mode) {
                    ProcessingMode.OFFLINE -> stringResource(R.string.offline_description)
                    ProcessingMode.ONLINE -> stringResource(R.string.online_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Карточка управления процессом обработки.
 */
@Composable
fun ProcessingCard(
    uiState: MainUiState,
    onStartProcessing: () -> Unit,
    onOpenSectionEditor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.processing),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.processingState) {
                ProcessingState.LOADING -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.progress.message.ifEmpty { "Загрузка..." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                ProcessingState.PROCESSING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.progress.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.progress.percentage > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.progress.percentage / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.progress.percentage}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ProcessingState.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vocal_extracted),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onOpenSectionEditor,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.open_section_editor))
                        }
                        FilledTonalButton(
                            onClick = onStartProcessing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Повторить")
                        }
                    }
                }

                ProcessingState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "Ошибка",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onStartProcessing) {
                        Text("Повторить")
                    }
                }

                ProcessingState.IDLE -> {
                    Text(
                        text = if (uiState.selectedFile != null) {
                            stringResource(R.string.ready_to_process)
                        } else {
                            stringResource(R.string.select_file_first)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartProcessing,
                        enabled = uiState.selectedFile != null
                    ) {
                        Text(stringResource(R.string.start_processing))
                    }
                }
            }
        }
    }
}

/**
 * Нижний лист с редактором секций вокала.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionEditorBottomSheet(
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    uiState: MainUiState
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        VocalSectionEditor(
            vocalDurationMs = uiState.vocalDurationMs,
            waveformData = uiState.waveformData,
            sections = uiState.vocalSections,
            selectedSection = uiState.selectedSection,
            editorState = uiState.editorState,
            onAddSection = { name, startMs, endMs ->
                viewModel.addSection(name, startMs, endMs)
            },
            onRemoveSection = { sectionId ->
                viewModel.removeSection(sectionId)
            },
            onSelectSection = { section ->
                viewModel.selectSection(section)
            },
            onUpdateSection = { sectionId, startMs, endMs ->
                viewModel.updateSectionTime(sectionId, startMs, endMs)
            },
            onExportSection = { section ->
                viewModel.exportSection(section)
            },
            onExportAllSections = {
                viewModel.exportAllSections()
            },
            onAutoDetectSections = {
                viewModel.autoDetectSections()
            },
            onClearAllSections = {
                viewModel.clearAllSections()
            },
            onPreviewPositionChange = { positionMs ->
                viewModel.setPreviewPosition(positionMs)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
    }
}

/**
 * Диалог выбора режима обработки.
 */
@Composable
fun ModeSelectionDialog(
    onDismiss: () -> Unit,
    onModeSelected: (ProcessingMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.select_mode)) },
        text = {
            Column {
                Text(text = stringResource(R.string.mode_selection_message))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = false,
                        onClick = { onModeSelected(ProcessingMode.OFFLINE) }
                    )
                    Text(
                        text = stringResource(R.string.offline_mode),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = false,
                        onClick = { onModeSelected(ProcessingMode.ONLINE) }
                    )
                    Text(
                        text = stringResource(R.string.online_mode),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onModeSelected(ProcessingMode.OFFLINE) }) {
                Text(stringResource(R.string.offline_mode))
            }
        },
        dismissButton = {
            TextButton(onClick = { onModeSelected(ProcessingMode.ONLINE) }) {
                Text(stringResource(R.string.online_mode))
            }
        }
    )
}

/**
 * Проверка наличия разрешения на чтение аудиофайлов.
 */
private fun hasAudioPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
