package com.amresalehin.emregallery.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt
import com.amresalehin.emregallery.data.ThumbnailPrewarmManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    media: List<MediaItem>,
    loading: Boolean,
    albumTitle: String? = null,
    sortOrder: String = "date_desc",
    groupBy: String = "day",
    gridColumns: Int = 3,
    gridStyle: String = "masonry",
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    onClearAlbumFilter: () -> Unit = {},
    onAddPhotosToAlbum: (() -> Unit)? = null,
    onPermissionGranted: () -> Unit = {},
    onMoveToBin: (Set<String>) -> Unit = {},
    onMoveToLocked: (Set<String>) -> Unit = {},
    onShareItems: (Set<String>) -> Unit = {},
    onAddToAlbum: ((Set<String>) -> Unit)? = null,
    selectedUris: Set<String> = emptySet(),
    onSelectionChange: (Set<String>) -> Unit = {},
    onOpen: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()
    var pinchGridColumns by remember(gridColumns) { mutableIntStateOf(gridColumns.coerceIn(1, 6)) }
    var pinchZoomRemainder by remember { mutableFloatStateOf(1f) }
    val selectionMode = selectedUris.isNotEmpty()

    BackHandler(enabled = selectionMode) {
        onSelectionChange(emptySet())
    }
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val hasPermission = hasMediaPermission(context)
        granted = hasPermission
        if (hasPermission) onPermissionGranted()
    }
    LaunchedEffect(Unit) { granted = hasMediaPermission(context) }
    if (!granted) {
        val permissionsToRequest = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        PermissionState { launcher.launch(permissionsToRequest) }
        return
    }

    val sortedMedia = remember(media, sortOrder) {
        when (sortOrder) {
            "date_asc", "date_taken_asc" -> media.sortedWith(compareBy({ if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_asc" -> media.sortedWith(compareBy({ it.dateAddedSeconds * 1000L }, { it.id }))
            "date_added_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.dateAddedSeconds * 1000L }.thenBy { it.id })
            "name_asc" -> media.sortedWith(compareBy({ it.name.lowercase(Locale.getDefault()) }, { it.id }))
            "name_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.id })
            "size_asc" -> media.sortedWith(compareBy<MediaItem> { it.sizeBytes }.thenBy { it.id })
            "size_desc" -> media.sortedWith(compareByDescending<MediaItem> { it.sizeBytes }.thenBy { it.id })
            else -> media.sortedWith(compareByDescending<MediaItem> { if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }.thenBy { it.id })
        }
    }

    val groups = remember(sortedMedia, groupBy) {
        when (groupBy) {
            "day" -> groupMediaByDay(sortedMedia)
            "month" -> groupMediaByMonth(sortedMedia)
            "year" -> groupMediaByYear(sortedMedia)
            "folder" -> groupMediaByFolder(sortedMedia)
            "initial" -> groupMediaByInitial(sortedMedia)
            "none" -> listOf(DayGroup(dayKey = 0L, dayLabel = "", items = sortedMedia))
            else -> listOf(DayGroup(dayKey = 0L, dayLabel = "", items = sortedMedia))
        }
    }

    val groupedRows = remember(groups, pinchGridColumns) {
        groups.map { group -> group to group.items.chunked(pinchGridColumns.coerceIn(1, 6)) }
    }

    // Keep a stable map from LazyColumn row keys to compact media rows. Scroll prediction uses
    // row indexes, not all photo cells, so fling planning stays cheap for very large libraries.
    val predictivePrefetchRows = remember(groupedRows) {
        groupedRows.flatMap { (group, rowChunks) ->
            rowChunks.map { row ->
                "day_${group.dayKey}_row_${row.first().id}" to row
            }
        }
    }
    var observedScrollDirection by remember(listState) { mutableIntStateOf(1) }

    val predictivePrefetchConnection = remember(listState, predictivePrefetchRows) {
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                val velocityY = available.y
                if (abs(velocityY) < 1400f || predictivePrefetchRows.isEmpty()) return Velocity.Zero

                val forward = observedScrollDirection > 0
                val anchor = listState.firstVisibleItemIndex
                val predictedRows = (abs(velocityY) * 0.25f / 240f).roundToInt().coerceIn(3, 24)
                val lastIndex = predictivePrefetchRows.lastIndex
                val targetIndices = if (forward) {
                    (anchor + 1..(anchor + predictedRows).coerceAtMost(lastIndex)).toList()
                } else {
                    (anchor - 1 downTo (anchor - predictedRows).coerceAtLeast(0)).toList()
                }
                ThumbnailPrewarmManager.prioritize(
                    targetIndices.flatMap { predictivePrefetchRows[it].second }.take(60)
                )
                return Velocity.Zero
            }
        }
    }

    // Immediately pause background prewarmer during active scroll/fling to dedicate 100% CPU/IO to visible viewport
    LaunchedEffect(listState.isScrollInProgress) {
        ThumbnailPrewarmManager.isScrollPaused = listState.isScrollInProgress
    }

    // Observe row index transitions only (never subpixel offsets) to eliminate per-frame allocations & thread locks
    LaunchedEffect(listState, predictivePrefetchRows) {
        var previousIndex = -1
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                if (predictivePrefetchRows.isEmpty()) return@collect
                if (previousIndex >= 0 && abs(firstIndex - previousIndex) < 2) return@collect
                val direction = if (firstIndex >= previousIndex) 1 else -1
                observedScrollDirection = direction
                previousIndex = firstIndex

                val forwardCount = 10
                val backwardCount = 3
                val lastIndex = predictivePrefetchRows.lastIndex
                val targetIndices = if (direction > 0) {
                    (firstIndex..(firstIndex + forwardCount).coerceAtMost(lastIndex)).toList() +
                        (firstIndex - 1 downTo (firstIndex - backwardCount).coerceAtLeast(0)).toList()
                } else {
                    (firstIndex downTo (firstIndex - forwardCount).coerceAtLeast(0)).toList() +
                        (firstIndex + 1..(firstIndex + backwardCount).coerceAtMost(lastIndex)).toList()
                }
                ThumbnailPrewarmManager.prioritize(
                    targetIndices.distinct().flatMap { predictivePrefetchRows[it].second }.take(60)
                )
            }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().nestedScroll(predictivePrefetchConnection)
    ) {
        val containerWidth = maxWidth - 24.dp
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            // Single-finger vertical scrolls exit immediately, preserving native 120 FPS
                            // spline fling physics and touch digitizer velocity tracking.
                            if (event.changes.size < 2) continue
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                pinchZoomRemainder *= zoomChange
                                if (pinchZoomRemainder < 0.82f) {
                                    pinchGridColumns = (pinchGridColumns + 1).coerceAtMost(6)
                                    pinchZoomRemainder = 1f
                                } else if (pinchZoomRemainder > 1.22f) {
                                    pinchGridColumns = (pinchGridColumns - 1).coerceAtLeast(1)
                                    pinchZoomRemainder = 1f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (albumTitle != null) {
                val isCollection = albumTitle in listOf("Locked folder", "Bin", "Archive", "Favourites", "Videos", "Recently added")
                val collectionIcon = when (albumTitle) {
                    "Favourites" -> Icons.Default.Favorite
                    "Videos" -> Icons.Default.PlayCircle
                    "Recently added" -> Icons.Default.Schedule
                    "Locked folder" -> Icons.Default.Lock
                    "Bin" -> Icons.Default.Delete
                    "Archive" -> Icons.Default.Archive
                    else -> Icons.Default.Folder
                }
                item {
                    val returnProgress = LocalViewerCoordinatedProgress.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .destinationTopBarEntrance(returnProgress),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(collectionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (isCollection) "Collection" else "Album", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                Text(albumTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (onAddPhotosToAlbum != null) {
                                FilledTonalButton(
                                    onClick = onAddPhotosToAlbum,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add photos", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            IconButton(onClick = onClearAlbumFilter, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Clear filter", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            if (loading && sortedMedia.isEmpty()) {
                item {
                    HomeScreenSkeleton(
                        gridColumns = pinchGridColumns,
                        gridStyle = gridStyle,
                        showMemories = albumTitle == null
                    )
                }
            } else if (sortedMedia.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumTitle != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("No photos in this album", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(6.dp))
                                Text("Add photos from your gallery to populate this album.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                if (onAddPhotosToAlbum != null) {
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = onAddPhotosToAlbum) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add photos")
                                    }
                                }
                            }
                        } else {
                            Text("No photos or videos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                if (loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
                }
                if (albumTitle == null) {
                    item { MemoriesStrip(sortedMedia, onOpen) }
                    item {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            thickness = 1.dp
                        )
                    }
                }

                groupedRows.forEach { (group, rowChunks) ->
                    if (group.dayLabel.isNotBlank()) {
                        item(key = "header_${group.dayKey}", contentType = "header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = group.dayLabel,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${group.items.size} ${if (group.items.size == 1) "item" else "items"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(
                        count = rowChunks.size,
                        key = { rIdx -> "day_${group.dayKey}_row_${rowChunks[rIdx].first().id}" },
                        contentType = { "photo_row" }
                    ) { rowIndex ->
                        JustifiedMosaicRow(
                            items = rowChunks[rowIndex],
                            containerWidth = containerWidth,
                            rowIndex = rowIndex,
                            isSquare = (gridStyle == "square"),
                            selectedUris = selectedUris,
                            onLongPress = { item -> onSelectionChange(selectedUris + item.uri) },
                            onToggleSelection = { item -> onSelectionChange(if (item.uri in selectedUris) selectedUris - item.uri else selectedUris + item.uri) },
                            onOpen = { item -> if (selectionMode) { onSelectionChange(if (item.uri in selectedUris) selectedUris - item.uri else selectedUris + item.uri) } else onOpen(item) }
                        )
                    }
                }
            }
        }

        HomeFastScrollbar(
            listState = listState,
            topContentPadding = topContentPadding,
            bottomContentPadding = bottomContentPadding,
            thumbColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


