package com.vocalclear.app.presentation.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vocalclear.app.R
import com.vocalclear.app.domain.model.SectionColor
import com.vocalclear.app.domain.model.VocalSection
import com.vocalclear.app.presentation.viewmodel.SectionEditorState

/**
 * Vocal Section Editor UI Component
 * Allows users to create, edit, and export sections from vocal audio
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocalSectionEditor(
    vocalDurationMs: Long,
    waveformData: List<Float>,
    sections: List<VocalSection>,
    selectedSection: VocalSection?,
    editorState: SectionEditorState,
    onAddSection: (String, Long, Long) -> Unit,
    onRemoveSection: (String) -> Unit,
    onSelectSection: (VocalSection?) -> Unit,
    onUpdateSection: (String, Long, Long) -> Unit,
    onExportSection: (VocalSection) -> Unit,
    onExportAllSections: () -> Unit,
    onAutoDetectSections: () -> Unit,
    onClearAllSections: () -> Unit,
    onPreviewPositionChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSection by remember { mutableStateOf<VocalSection?>(null) }
    var dragState by remember { mutableStateOf<DragState?>(null) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header with actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.section_editor),
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = onAutoDetectSections,
                    enabled = sections.isEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.auto_detect)
                    )
                }
                FilledTonalIconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_section)
                    )
                }
                if (sections.isNotEmpty()) {
                    FilledTonalIconButton(onClick = onExportAllSections) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.export_all)
                        )
                    }
                    FilledTonalIconButton(onClick = onClearAllSections) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_all)
                        )
                    }
                }
            }
        }

        // Waveform visualization with sections overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Waveform
            WaveformView(
                waveformData = waveformData,
                modifier = Modifier.fillMaxSize()
            )

            // Section markers
            SectionMarkersOverlay(
                sections = sections,
                totalDurationMs = vocalDurationMs,
                selectedSectionId = selectedSection?.id,
                onSectionClick = { section -> onSelectSection(section) },
                modifier = Modifier.fillMaxSize()
            )

            // Playhead
            if (editorState.previewPositionMs > 0) {
                val playheadPosition = (editorState.previewPositionMs.toFloat() / vocalDurationMs).coerceIn(0f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val tappedPositionMs = ((offset.x / size.width) * vocalDurationMs).toLong()
                                onPreviewPositionChange(tappedPositionMs)
                            }
                            detectDragGestures { change, _ ->
                                change.consume()
                                val dragPositionMs = ((change.position.x / size.width) * vocalDurationMs).toLong()
                                onPreviewPositionChange(dragPositionMs.coerceIn(0, vocalDurationMs))
                            }
                        }
                ) {
                    val x = playheadPosition * size.width
                    drawLine(
                        color = Color.Red,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Duration info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(editorState.previewPositionMs),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime(vocalDurationMs),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Section list
        if (sections.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sections) { section ->
                    SectionListItem(
                        section = section,
                        isSelected = section.id == selectedSection?.id,
                        onClick = { onSelectSection(section) },
                        onEdit = { editingSection = section },
                        onDelete = { onRemoveSection(section.id) },
                        onExport = { onExportSection(section) }
                    )
                }
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_sections_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Add Section Dialog
    if (showAddDialog) {
        AddSectionDialog(
            maxDurationMs = vocalDurationMs,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, startMs, endMs ->
                onAddSection(name, startMs, endMs)
                showAddDialog = false
            }
        )
    }

    // Edit Section Dialog
    editingSection?.let { section ->
        EditSectionDialog(
            section = section,
            maxDurationMs = vocalDurationMs,
            onDismiss = { editingSection = null },
            onConfirm = { id, name, startMs, endMs ->
                onUpdateSection(id, startMs, endMs)
                editingSection = null
            }
        )
    }
}

/**
 * Waveform visualization component
 */
@Composable
fun WaveformView(
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    if (waveformData.isEmpty()) return

    Canvas(modifier = modifier) {
        val strokeWidth = 2f
        val stepX = size.width / waveformData.size.coerceAtLeast(1)

        val path = Path()
        val centerY = size.height / 2

        waveformData.forEachIndexed { index, amplitude ->
            val x = index * stepX
            val y = centerY - (amplitude * centerY * 0.8f)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Overlay showing section markers on waveform
 */
@Composable
fun SectionMarkersOverlay(
    sections: List<VocalSection>,
    totalDurationMs: Long,
    selectedSectionId: String?,
    onSectionClick: (VocalSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        sections.forEach { section ->
            val startX = (section.startTimeMs.toFloat() / totalDurationMs) * size.width
            val endX = (section.endTimeMs.toFloat() / totalDurationMs) * size.width
            val sectionWidth = endX - startX

            val color = if (section.id == selectedSectionId) {
                Color(section.color.hexColor).copy(alpha = 0.6f)
            } else {
                Color(section.color.hexColor).copy(alpha = 0.3f)
            }

            drawRect(
                color = color,
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(sectionWidth, size.height)
            )

            // Section border
            drawRect(
                color = Color(section.color.hexColor),
                topLeft = Offset(startX, 0f),
                size = androidx.compose.ui.geometry.Size(2f, size.height)
            )
            drawRect(
                color = Color(section.color.hexColor),
                topLeft = Offset(endX - 2, 0f),
                size = androidx.compose.ui.geometry.Size(2f, size.height)
            )
        }
    }
}

/**
 * Individual section list item
 */
@Composable
fun SectionListItem(
    section: VocalSection,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(section.color.hexColor).copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(section.color.hexColor))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Section info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTime(section.startTimeMs)} - ${formatTime(section.endTimeMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.export),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Dialog for adding a new section
 */
@Composable
fun AddSectionDialog(
    maxDurationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startMs by remember { mutableStateOf("0") }
    var endMs by remember { mutableStateOf("") }
    var startError by remember { mutableStateOf<String?>(null) }
    var endError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_section)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.section_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startMs,
                    onValueChange = {
                        startMs = it.filter { char -> char.isDigit() }
                        startError = null
                    },
                    label = { Text("${stringResource(R.string.start_time)} (${stringResource(R.string.seconds)})") },
                    isError = startError != null,
                    supportingText = startError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endMs,
                    onValueChange = {
                        endMs = it.filter { char -> char.isDigit() }
                        endError = null
                    },
                    label = { Text("${stringResource(R.string.end_time)} (${stringResource(R.string.seconds)})") },
                    isError = endError != null,
                    supportingText = endError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.max_duration_format, formatTime(maxDurationMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = (startMs.toLongOrNull() ?: 0L) * 1000
                    val end = (endMs.toLongOrNull() ?: 0L) * 1000

                    when {
                        start >= end -> {
                            endError = "End time must be greater than start time"
                        }
                        end > maxDurationMs -> {
                            endError = "End time exceeds audio duration"
                        }
                        else -> {
                            onConfirm(name.ifBlank { "Section" }, start, end)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Dialog for editing an existing section
 */
@Composable
fun EditSectionDialog(
    section: VocalSection,
    maxDurationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf(section.name) }
    var startSec by remember { mutableStateOf((section.startTimeMs / 1000).toString()) }
    var endSec by remember { mutableStateOf((section.endTimeMs / 1000).toString()) }
    var startError by remember { mutableStateOf<String?>(null) }
    var endError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_section)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.section_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startSec,
                    onValueChange = {
                        startSec = it.filter { char -> char.isDigit() }
                        startError = null
                    },
                    label = { Text("${stringResource(R.string.start_time)} (${stringResource(R.string.seconds)})") },
                    isError = startError != null,
                    supportingText = startError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endSec,
                    onValueChange = {
                        endSec = it.filter { char -> char.isDigit() }
                        endError = null
                    },
                    label = { Text("${stringResource(R.string.end_time)} (${stringResource(R.string.seconds)})") },
                    isError = endError != null,
                    supportingText = endError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = (startSec.toLongOrNull() ?: 0L) * 1000
                    val end = (endSec.toLongOrNull() ?: 0L) * 1000

                    when {
                        start >= end -> {
                            endError = "End time must be greater than start time"
                        }
                        end > maxDurationMs -> {
                            endError = "End time exceeds audio duration"
                        }
                        else -> {
                            onConfirm(section.id, name, start, end)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Helper state class for drag operations
 */
private data class DragState(
    val sectionId: String,
    val isStartHandle: Boolean,
    val initialPosition: Long
)

/**
 * Format milliseconds to mm:ss or mm:ss.ms format
 */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
