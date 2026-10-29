package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.ocr.TesseractModel
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
fun OcrIndexingCard(
    totalPhotos: Int,
    ocrState: OcrState,
    ocrLanguages: Set<String>,
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onToggleOcr: () -> Unit,
    onStopOcr: () -> Unit,
    onReindexOcr: () -> Unit,
    onModelChange: (String) -> Unit,
    onToggleLanguage: (String) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrProgress = if (totalPhotos > 0) (ocrState.processed.toFloat() / totalPhotos).coerceIn(0f, 1f) else 0f
    val ocrPercent = (ocrProgress * 100f).roundToInt()
    val isBestModel = ocrState.model.equals("best", ignoreCase = true)
    val activeModel = if (isBestModel) TesseractModel.BEST else TesseractModel.FAST

    var languageMenuExpanded by remember { mutableStateOf(false) }
    var downloadingLanguageCode by remember { mutableStateOf<String?>(null) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    fun downloadAndSelectLanguage(code: String) {
        if (downloadingLanguageCode != null) return
        downloadingLanguageCode = code
        scope.launch(Dispatchers.IO) {
            val success = runCatching {
                TesseractOcr.prepareLanguages(context, listOf(code), activeModel)
                true
            }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                downloadingLanguageCode = null
                if (success) {
                    onToggleLanguage(code)
                }
            }
        }
    }

    val displayLanguageLabel = remember(ocrLanguages) {
        val selectedEntries = TesseractOcr.supportedLanguages.entries.filter { it.value in ocrLanguages }
        when {
            selectedEntries.isEmpty() -> "English"
            selectedEntries.size == 1 -> selectedEntries.first().key
            else -> "${selectedEntries.first().key} (+${selectedEntries.size - 1})"
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon, OCR Title, Start/Pause Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "OCR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Action Start / Pause / Resume button
                Button(
                    onClick = onToggleOcr,
                    enabled = countsReady && totalPhotos > 0 && (ocrState.isRunning || ocrState.processed < totalPhotos),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        when {
                            ocrState.isRunning && !ocrState.isPaused -> Icons.Default.Pause
                            ocrState.processed >= totalPhotos && totalPhotos > 0 -> Icons.Default.Check
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            ocrState.isRunning && !ocrState.isPaused -> "Pause"
                            ocrState.isRunning && ocrState.isPaused -> "Resume"
                            ocrState.processed >= totalPhotos && totalPhotos > 0 -> "Complete"
                            else -> "Start"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (ocrState.isRunning) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onStopOcr,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop OCR",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Progress row & linear progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${numberFormat.format(ocrState.processed)} / ${numberFormat.format(totalPhotos)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$ocrPercent%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { ocrProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Row 1: Model
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.width(180.dp).height(38.dp)
                ) {
                    SegmentedButton(
                        selected = !isBestModel,
                        onClick = { onModelChange("fast") },
                        enabled = !ocrState.isRunning,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) {
                        Text("Fast", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = isBestModel,
                        onClick = { onModelChange("best") },
                        enabled = !ocrState.isRunning,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) {
                        Text("Best", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Row 2: Language
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Language",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    Surface(
                        onClick = { languageMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                displayLanguageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select language",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                    ) {
                        TesseractOcr.supportedLanguages.forEach { (name, code) ->
                            val isSelected = code in ocrLanguages
                            val isBundled = remember(code, activeModel) {
                                TesseractOcr.isLanguageBundledInAssets(context, code, activeModel)
                            }
                            val isInstalled = remember(code, activeModel, downloadingLanguageCode) {
                                TesseractOcr.isLanguageInstalled(context, code, activeModel)
                            }
                            val isDownloadingThis = downloadingLanguageCode == code

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                trailingIcon = {
                                    when {
                                        isDownloadingThis -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        isSelected -> {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        !isBundled && !isInstalled -> {
                                            Icon(
                                                Icons.Default.CloudDownload,
                                                contentDescription = "Download language",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (!isBundled && !isInstalled) {
                                        downloadAndSelectLanguage(code)
                                    } else {
                                        onToggleLanguage(code)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Row 3: Cache
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cache",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${numberFormat.format(ocrState.cachedCount)} cached photos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ocrState.cachedCount > 0) {
                        OutlinedButton(
                            onClick = { showClearCacheConfirm = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            Text("Clear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    OutlinedButton(
                        onClick = onReindexOcr,
                        enabled = countsReady && totalPhotos > 0 && !ocrState.isRunning,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Re-index", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear OCR Cache?") },
            text = { Text("This will remove all cached text recognized from photos. You can re-index photos at any time.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        onClearCache()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
