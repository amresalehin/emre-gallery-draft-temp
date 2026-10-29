package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceProcessingCoordinator
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.StageState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FaceRecognitionCard(
    totalPhotos: Int,
    faceFindingState: FaceFindingState,
    engineState: EngineState,
    modelDownloadState: ModelDownloadState,
    clusteringState: FaceClusteringState,
    enteFaceFindingState: FaceFindingState = FaceFindingState(),
    enteModelDownloadState: ModelDownloadState = ModelDownloadState(),
    enteClusteringState: FaceClusteringState = FaceClusteringState(),
    countsReady: Boolean,
    cardBackground: Color,
    cardBorder: BorderStroke,
    numberFormat: NumberFormat,
    onConfidenceChange: (Float, String) -> Unit,
    onMinGroupFacesChange: (Int, String) -> Unit,
    onClusterDistanceChange: (Float, String) -> Unit,
    onToggleFaceFinding: (String) -> Unit,
    onStopFaceFinding: (String) -> Unit,
    onRescanRequested: (String) -> Unit,
    onDownloadModel: (force: Boolean, engineId: String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onClusterFaces: (Float, String) -> Unit,
    onResetClusters: (String) -> Unit,
    onComparePipelines: (() -> Unit)? = null,
    onRetryFailedFaces: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeEngineInSettings = FaceRecognitionSettings.getActiveEngine(context)

    var selectedEngineId by remember {
        mutableStateOf(
            when {
                engineState.isBuffaloLInstalled -> BuffaloLFaceEngine.ENGINE_ID
                engineState.isEnteInstalled -> EnteFaceConstants.ENGINE_ID
                activeEngineInSettings.isNotBlank() -> activeEngineInSettings
                else -> ""
            }
        )
    }

    val isEnte = selectedEngineId == EnteFaceConstants.ENGINE_ID
    val currentFindingState = if (isEnte) enteFaceFindingState else faceFindingState
    val currentDownloadState = if (isEnte) enteModelDownloadState else modelDownloadState
    val currentClusteringState = if (isEnte) enteClusteringState else clusteringState

    val isModelInstalled = when (selectedEngineId) {
        EnteFaceConstants.ENGINE_ID -> engineState.isEnteInstalled
        BuffaloLFaceEngine.ENGINE_ID -> engineState.isBuffaloLInstalled
        else -> false
    }

    val downloadPercent = if (currentDownloadState.totalBytes > 0) {
        ((currentDownloadState.bytesReceived.toFloat() / currentDownloadState.totalBytes) * 100f).roundToInt().coerceIn(0, 100)
    } else if (currentDownloadState.bytesReceived > 0) {
        val estimatedTotal = if (isEnte) 25L * 1024 * 1024 else 288L * 1024 * 1024
        ((currentDownloadState.bytesReceived.toFloat() / estimatedTotal) * 100f).roundToInt().coerceIn(0, 99)
    } else 0

    var isDropdownExpanded by remember { mutableStateOf(false) }
    var pendingEngineSwitch by remember { mutableStateOf<String?>(null) }
    var showErrorLogDialog by remember { mutableStateOf(false) }
    var isAdvancedSettingsExpanded by remember { mutableStateOf(false) }

    val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
    val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)

    val isModelSwitchingDisabled = isBusy ||
        currentFindingState.isRunning ||
        currentFindingState.isPaused ||
        currentClusteringState.isClusteringRunning ||
        currentDownloadState.isDownloading ||
        currentFindingState.clusteringStage.state == StageState.RUNNING ||
        modelDownloadState.isDownloading ||
        enteModelDownloadState.isDownloading ||
        faceFindingState.isRunning ||
        enteFaceFindingState.isRunning

    // Mutex confirmation dialog when attempting to switch engines during active download
    if (pendingEngineSwitch != null) {
        val targetEngine = pendingEngineSwitch!!
        val targetEngineName = if (targetEngine == EnteFaceConstants.ENGINE_ID) "YOLO5Face + MobileFaceNet" else "Buffalo - L"
        val activeEngineName = if (isEnte) "YOLO5Face + MobileFaceNet" else "Buffalo - L"
        AlertDialog(
            onDismissRequest = { pendingEngineSwitch = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Cancel Active Download?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "A model download is currently active for $activeEngineName. Switching to $targetEngineName requires cancelling the current download. Do you want to cancel and switch?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelDownload(selectedEngineId)
                        selectedEngineId = targetEngine
                        FaceRecognitionSettings.setActiveEngine(context, targetEngine)
                        pendingEngineSwitch = null
                    }
                ) {
                    Text("Cancel Download & Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEngineSwitch = null }) {
                    Text("Keep Downloading")
                }
            }
        )
    }

    // Error log dialog for viewing failed items
    if (showErrorLogDialog) {
        AlertDialog(
            onDismissRequest = { showErrorLogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Face Indexing Error Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    val failures = currentFindingState.failedItems
                    if (failures.isEmpty()) {
                        Text(
                            "${currentFindingState.failedCount} items encountered errors during indexing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(failures) { (uri, reason) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = uri.substringAfterLast('/'),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrorLogDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .testTag("face_recognition_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 1. HEADER ROW: Title & Operational Lifecycle Status Icon ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Face Recognition",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Top-Right Status Icon (Operational Lifecycle Indicator)
                val isDownloading = currentDownloadState.isDownloading
                val isClusteringActive = currentClusteringState.isClusteringRunning || currentFindingState.clusteringStage.state == StageState.RUNNING
                val isScanningActive = currentFindingState.isRunning && !currentFindingState.isPaused && !isClusteringActive
                val isAllCompleted = isModelInstalled && !currentFindingState.isRunning && !isClusteringActive && totalPhotos > 0 && currentFindingState.processed >= totalPhotos

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            // 1. Model Downloading: Circular progress spinner with real-time percentage text
                            isDownloading -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { (downloadPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$downloadPercent%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // 2. Pipeline Active: Clustering faces
                            isClusteringActive -> {
                                val infiniteTransition = rememberInfiniteTransition(label = "clustering_anim")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.85f,
                                    targetValue = 1.15f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "cluster_pulse"
                                )
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = "Clustering faces active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer(scaleX = scale, scaleY = scale)
                                )
                            }

                            // 3. Pipeline Active: Detection / Embedding (Scanning radar + mini phase badge)
                            isScanningActive -> {
                                val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
                                val angle by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "radar_rotation"
                                )
                                val isEmbedding = currentFindingState.embeddingStage.state == StageState.RUNNING
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radar,
                                        contentDescription = "Face scanning active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .rotate(angle)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (isEmbedding) "2" else "1",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Completed: Solid green checkmark (tick)
                            isAllCompleted -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "All faces processed",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // 5. Idle / Model Ready: Static cloud-check / ready badge
                            isModelInstalled -> {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Model ready",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // 6. Model Not Downloaded: Cloud download action button
                            else -> {
                                IconButton(
                                    onClick = {
                                        if (selectedEngineId.isBlank()) {
                                            isDropdownExpanded = true
                                        } else {
                                            onDownloadModel(true, selectedEngineId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Download model",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 2. RECOGNITION ENGINE DROPDOWN MENU ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Recognition Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentLabel = when (selectedEngineId) {
                        EnteFaceConstants.ENGINE_ID -> "YOLO5Face + MobileFaceNet (~25 MB)"
                        BuffaloLFaceEngine.ENGINE_ID -> "Buffalo - L (~300 MB)"
                        else -> "Select Engine"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isModelSwitchingDisabled) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isModelSwitchingDisabled) 0.35f else 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isModelSwitchingDisabled) { isDropdownExpanded = !isDropdownExpanded }
                            .testTag("engine_dropdown_selector")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Normal,
                                color = if (isModelSwitchingDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = if (isDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Dropdown chevron",
                                tint = if (isModelSwitchingDisabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isModelSwitchingDisabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Model switching is disabled while face processing is in progress.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded && !isModelSwitchingDisabled,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        // Option 1: Buffalo - L (~300 MB)
                        DropdownMenuItem(
                            enabled = !isModelSwitchingDisabled,
                            text = {
                                Column {
                                    Text(
                                        text = "Buffalo - L",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (engineState.isBuffaloLInstalled) "Installed · ~300 MB" else "~300 MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                if (engineState.isBuffaloLInstalled) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Installed",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    IconButton(
                                        enabled = !isModelSwitchingDisabled,
                                        onClick = {
                                            isDropdownExpanded = false
                                            if (FaceProcessingCoordinator.isBusy(context)) return@IconButton
                                            if (isEnte && currentDownloadState.isDownloading) {
                                                pendingEngineSwitch = BuffaloLFaceEngine.ENGINE_ID
                                            } else {
                                                selectedEngineId = BuffaloLFaceEngine.ENGINE_ID
                                                FaceRecognitionSettings.setActiveEngine(context, BuffaloLFaceEngine.ENGINE_ID)
                                                onDownloadModel(true, BuffaloLFaceEngine.ENGINE_ID)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Download Buffalo - L",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                isDropdownExpanded = false
                                if (FaceProcessingCoordinator.isBusy(context)) return@DropdownMenuItem
                                if (isEnte && currentDownloadState.isDownloading) {
                                    pendingEngineSwitch = BuffaloLFaceEngine.ENGINE_ID
                                } else {
                                    selectedEngineId = BuffaloLFaceEngine.ENGINE_ID
                                    FaceRecognitionSettings.setActiveEngine(context, BuffaloLFaceEngine.ENGINE_ID)
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Option 2: YOLO5Face + MobileFaceNet (~25 MB)
                        DropdownMenuItem(
                            enabled = !isModelSwitchingDisabled,
                            text = {
                                Column {
                                    Text(
                                        text = "YOLO5Face + MobileFaceNet",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (engineState.isEnteInstalled) "Installed · ~25 MB" else "~25 MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                if (engineState.isEnteInstalled) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Installed",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    IconButton(
                                        enabled = !isModelSwitchingDisabled,
                                        onClick = {
                                            isDropdownExpanded = false
                                            if (FaceProcessingCoordinator.isBusy(context)) return@IconButton
                                            if (!isEnte && currentDownloadState.isDownloading) {
                                                pendingEngineSwitch = EnteFaceConstants.ENGINE_ID
                                            } else {
                                                selectedEngineId = EnteFaceConstants.ENGINE_ID
                                                FaceRecognitionSettings.setActiveEngine(context, EnteFaceConstants.ENGINE_ID)
                                                onDownloadModel(true, EnteFaceConstants.ENGINE_ID)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Download YOLO5Face + MobileFaceNet",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                isDropdownExpanded = false
                                if (FaceProcessingCoordinator.isBusy(context)) return@DropdownMenuItem
                                if (!isEnte && currentDownloadState.isDownloading) {
                                    pendingEngineSwitch = EnteFaceConstants.ENGINE_ID
                                } else {
                                    selectedEngineId = EnteFaceConstants.ENGINE_ID
                                    FaceRecognitionSettings.setActiveEngine(context, EnteFaceConstants.ENGINE_ID)
                                }
                            }
                        )
                    }
                }
            }

            // --- 3. METRICS GRID & LOCKED GATING OVERLAY ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("metrics_content_area"),
                contentAlignment = Alignment.Center
            ) {
                // Background Metrics Grid (dimmed when locked)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (!isModelInstalled) 0.18f else 1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Column 1: Photos Indexed (scanned vs total)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${numberFormat.format(currentFindingState.processed)} / ${numberFormat.format(totalPhotos)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Photos Indexed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Vertical divider
                            VerticalDivider(
                                modifier = Modifier.height(34.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Column 2: Faces Found (cumulative instances)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = numberFormat.format(currentFindingState.totalDetected),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Faces Found",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Vertical divider
                            VerticalDivider(
                                modifier = Modifier.height(34.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Column 3: Photos with Faces (scanned with faces vs total scanned)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${numberFormat.format(currentFindingState.successfulCount)} / ${numberFormat.format(currentFindingState.processed)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Photos with Faces",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Gating Lock Overlay when model is missing/downloading
                if (!isModelInstalled) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .testTag("model_locked_overlay")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (currentDownloadState.isDownloading) "Downloading model..." else "Download a model to unlock",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Download Failure / Verification Error Banner with Retry
            val modelFailure = if (isEnte) engineState.enteModelFailure else engineState.primaryModelFailure
            val downloadError = currentDownloadState.error
            val hasActualModelError = !modelFailure.isNullOrBlank() && !modelFailure.contains("not installed", ignoreCase = true) && !modelFailure.contains("missing", ignoreCase = true)
            val displayError = downloadError ?: if (hasActualModelError) modelFailure else null

            if (displayError != null && !currentDownloadState.isDownloading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = displayError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onDownloadModel(true, selectedEngineId) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Failed scans post-run notice
            if (currentFindingState.failedCount > 0 && !currentFindingState.isRunning) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${currentFindingState.failedCount} items failed to scan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showErrorLogDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("View Log", style = MaterialTheme.typography.labelSmall)
                        }
                        if (onRetryFailedFaces != null) {
                            FilledTonalButton(
                                onClick = { onRetryFailedFaces(selectedEngineId) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Retry", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // --- 4. GATING & DYNAMIC RUN BUTTON LIFECYCLE ---
            val isPipelineRunning = currentFindingState.isRunning || currentClusteringState.isClusteringRunning
            val isClusteringStage = currentClusteringState.isClusteringRunning || currentFindingState.clusteringStage.state == StageState.RUNNING

            when {
                // State A: Locked State (Model Not Downloaded / Download in progress)
                !isModelInstalled -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("run_button_disabled"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Run",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // State B: Active / Running State (Adaptive Feedback Action Bar)
                isPipelineRunning -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_pipeline_action_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!currentFindingState.isPaused) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PauseCircle,
                                        contentDescription = "Paused",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    val statusLabel = when {
                                        currentFindingState.isPaused -> "Paused"
                                        isClusteringStage -> {
                                            val cStage = currentFindingState.clusteringStage
                                            val cProcessed = numberFormat.format(cStage.processed)
                                            val cTotal = numberFormat.format(if (cStage.total > 0) cStage.total else currentFindingState.totalDetected)
                                            "Clustering faces: $cProcessed / $cTotal faces..."
                                        }
                                        else -> {
                                            val pProcessed = numberFormat.format(currentFindingState.processed)
                                            val pTotal = numberFormat.format(totalPhotos)
                                            "Scanning: $pProcessed / $pTotal photos..."
                                        }
                                    }
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!currentFindingState.isPaused && currentFindingState.currentItemName.isNotBlank() && !isClusteringStage) {
                                        Text(
                                            text = currentFindingState.currentItemName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Inline Action Controls: Pause/Resume and Cancel
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (currentFindingState.isPaused) {
                                    FilledTonalButton(
                                        onClick = { onToggleFaceFinding(selectedEngineId) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("resume_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Resume", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { onToggleFaceFinding(selectedEngineId) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("pause_button")
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pause", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                IconButton(
                                    onClick = { onStopFaceFinding(selectedEngineId) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("cancel_button")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancel face processing",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // State C: Idle / Ready State (Full-width "Run" action button)
                else -> {
                    val isDone = totalPhotos > 0 && currentFindingState.processed >= totalPhotos
                    Button(
                        onClick = {
                            if (isDone) {
                                onRescanRequested(selectedEngineId)
                            } else {
                                onToggleFaceFinding(selectedEngineId)
                            }
                        },
                        enabled = countsReady && totalPhotos > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("run_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isDone) Icons.Default.Replay else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isDone) "Re-run" else "Run",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // --- 5. ADVANCED SETTINGS (HIDDEN BY DEFAULT) ---
            // Subtle, collapsed toggle to adjust threshold settings if user explicitly needs them
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = { isAdvancedSettingsExpanded = !isAdvancedSettingsExpanded },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isAdvancedSettingsExpanded) "Hide Advanced Settings" else "Advanced Settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isAdvancedSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isAdvancedSettingsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Threshold 1: Detection confidence
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Detection Confidence", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${(currentFindingState.detectionConfidence * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                                }
                                Slider(
                                    value = currentFindingState.detectionConfidence,
                                    onValueChange = { onConfidenceChange(it, selectedEngineId) },
                                    valueRange = 0.30f..0.90f,
                                    steps = 11
                                )
                            }

                            // Threshold 2: Minimum faces per person
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Min Faces Per Person", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${currentClusteringState.minGroupFaces}", style = MaterialTheme.typography.bodySmall)
                                }
                                Slider(
                                    value = currentClusteringState.minGroupFaces.toFloat(),
                                    onValueChange = { onMinGroupFacesChange(it.roundToInt(), selectedEngineId) },
                                    valueRange = 1f..10f,
                                    steps = 8
                                )
                            }

                            // Threshold 3: Clustering distance
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Clustering Distance", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text(String.format(Locale.US, "%.2f", currentClusteringState.clusterDistance), style = MaterialTheme.typography.bodySmall)
                                }
                                Slider(
                                    value = currentClusteringState.clusterDistance,
                                    onValueChange = { onClusterDistanceChange(it, selectedEngineId) },
                                    valueRange = 0.30f..0.85f,
                                    steps = 10
                                )
                            }

                            // Processor & Thermal Throttling Control
                            var currentThrottleMode by remember { mutableStateOf(FaceRecognitionSettings.cpuThrottleMode(context)) }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("CPU & Thermal Throttling", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(
                                        when (currentThrottleMode) {
                                            FaceRecognitionSettings.THROTTLE_ECO -> "Saver (40ms)"
                                            FaceRecognitionSettings.THROTTLE_BALANCED -> "Balanced (10ms)"
                                            FaceRecognitionSettings.THROTTLE_TURBO -> "Turbo (0ms)"
                                            else -> "Continuous (0ms)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    "Continuous operation without artificial processing cooldowns when Android is not limiting the app, preserving fluid 120Hz/120Fps responsiveness.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        FaceRecognitionSettings.THROTTLE_UNCONSTRAINED to "Continuous",
                                        FaceRecognitionSettings.THROTTLE_TURBO to "Turbo",
                                        FaceRecognitionSettings.THROTTLE_BALANCED to "Balanced",
                                        FaceRecognitionSettings.THROTTLE_ECO to "Saver"
                                    ).forEach { (mode, label) ->
                                        val isSelected = currentThrottleMode == mode
                                        Surface(
                                            onClick = {
                                                currentThrottleMode = mode
                                                FaceRecognitionSettings.setCpuThrottleMode(context, mode)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = label,
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Clustering Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onClusterFaces(currentClusteringState.clusterDistance, selectedEngineId) },
                                    enabled = !isPipelineRunning && isModelInstalled && currentFindingState.totalDetected > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Re-cluster", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { onResetClusters(selectedEngineId) },
                                    enabled = !isPipelineRunning && isModelInstalled,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reset Clusters", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
