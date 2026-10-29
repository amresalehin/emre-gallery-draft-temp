package com.amresalehin.emregallery.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.size.Size
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeFastScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    thumbColor: Color
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragRemainder by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val minThumbPx = remember(density) { with(density) { 36.dp.toPx() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topContentPadding, bottom = bottomContentPadding)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(listState) {
                    detectDragGestures(
                        onDragStart = { dragging = true; dragRemainder = 0f },
                        onDragEnd = { dragging = false; dragRemainder = 0f },
                        onDragCancel = { dragging = false; dragRemainder = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val currentLayout = listState.layoutInfo
                            val count = currentLayout.totalItemsCount
                            val height = size.height.toFloat()
                            if (count > 0 && height > 0f) {
                                dragRemainder += (dragAmount.y / height) * count
                                val itemDelta = dragRemainder.toInt()
                                if (itemDelta != 0) {
                                    dragRemainder -= itemDelta
                                    val target = (listState.firstVisibleItemIndex + itemDelta)
                                        .coerceIn(0, (count - 1).coerceAtLeast(0))
                                    scope.launch { listState.scrollToItem(target) }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.TopEnd
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val layout = listState.layoutInfo
                val totalItems = layout.totalItemsCount
                val visibleItems = layout.visibleItemsInfo
                val viewportPx = size.height

                if (totalItems <= 0 || visibleItems.isEmpty() || viewportPx <= 0f) return@Canvas
                if (visibleItems.first().index == 0 && visibleItems.last().index == totalItems - 1) return@Canvas

                val thumbHeightPx = (viewportPx * visibleItems.size / totalItems.toFloat())
                    .coerceIn(minThumbPx, viewportPx)
                val maxThumbTravel = (viewportPx - thumbHeightPx).coerceAtLeast(1f)

                // Sub-pixel continuous float position: combine index with scroll offset to eliminate integer snapping
                val firstItem = visibleItems.firstOrNull()
                val itemHeightPx = (firstItem?.size ?: 1).coerceAtLeast(1).toFloat()
                val subItemFraction = (listState.firstVisibleItemScrollOffset.toFloat() / itemHeightPx).coerceIn(0f, 1f)
                val continuousIndex = listState.firstVisibleItemIndex.toFloat() + subItemFraction
                val maxIndex = (totalItems - visibleItems.size).coerceAtLeast(1).toFloat()
                val scrollFraction = (continuousIndex / maxIndex).coerceIn(0f, 1f)
                val thumbTopPx = maxThumbTravel * scrollFraction

                val inMotion = dragging || listState.isScrollInProgress
                val thumbWidth = if (inMotion) 6.dp.toPx() else 2.5.dp.toPx()
                val height = thumbHeightPx.coerceAtMost(size.height)
                val top = thumbTopPx.coerceIn(0f, size.height - height)
                drawRoundRect(
                    color = thumbColor.copy(alpha = if (inMotion) 0.95f else 0.55f),
                    topLeft = Offset(size.width - thumbWidth, top),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f)
                )
            }
        }
    }
}

@Composable
fun HomeScreenSkeleton(
    gridColumns: Int = 3,
    gridStyle: String = "masonry",
    showMemories: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_screen_skeleton"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showMemories) {
            // Memories row skeleton
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(118.dp)
                            .height(174.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.7f)
                                )
                        )
                    }
                }
            }
        }

        // Section header skeleton
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                    )
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alphaAnim * 0.8f)
                    )
            )
        }

        // Multi-column grid skeleton
        val masonryHeights = listOf(140.dp, 190.dp, 130.dp, 170.dp, 120.dp, 180.dp, 150.dp, 130.dp, 160.dp, 140.dp, 200.dp, 135.dp)
        val validCols = gridColumns.coerceIn(1, 6)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (col in 0 until validCols) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(4) { rowIndex ->
                        val itemIndex = (col + rowIndex * validCols) % masonryHeights.size
                        val height = if (gridStyle == "square") 110.dp else masonryHeights[itemIndex]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alphaAnim)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoriesStrip(media: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    val today = remember { Calendar.getInstance() }
    val currentYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH)
    val todayDay = today.get(Calendar.DAY_OF_YEAR)

    val stories = remember(media, currentYear, todayMonth, todayDay) {
        if (media.isEmpty()) return@remember emptyList()
        val cal = Calendar.getInstance()
        val eligible = ArrayList<Pair<MediaItem, Long>>(media.size)
        val onThisDayMap = mutableMapOf<Int, MutableList<MediaItem>>()
        val tripsMap = LinkedHashMap<Int, MutableList<Pair<MediaItem, Long>>>()

        for (item in media) {
            val timestamp = if (item.dateTakenMillis > 0L) item.dateTakenMillis else item.dateAddedSeconds * 1000L
            if (timestamp <= 0L) continue
            eligible.add(item to timestamp)
            cal.timeInMillis = timestamp
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_YEAR)

            if (m == todayMonth && d == todayDay && y < currentYear) {
                onThisDayMap.getOrPut(y) { mutableListOf() }.add(item)
            }
            val tripKey = y * 1000 + d
            tripsMap.getOrPut(tripKey) { mutableListOf() }.add(item to timestamp)
        }

        val result = mutableListOf<Pair<String, List<MediaItem>>>()

        // Google Photos-style "On this day" stories: real photos from this calendar date in prior years.
        onThisDayMap.toSortedMap(compareByDescending { it }).forEach { (year, entries) ->
            result += "On this day · $year" to entries
        }

        // Real date-based trip/story groups from the library
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        tripsMap.entries
            .filter { it.value.size >= 4 }
            .sortedByDescending { it.key }
            .take(8)
            .forEach { (_, entries) ->
                val label = dateFormat.format(Date(entries.first().second))
                if (result.none { it.second.any { storyItem -> storyItem.uri == entries.first().first.uri } }) {
                    result += label to entries.map { it.first }
                }
            }

        result.filter { it.second.isNotEmpty() }.take(10)
    }

    if (stories.isEmpty()) return

    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(stories.size, key = { index -> stories[index].first }) { index ->
                val (title, storyMedia) = stories[index]
                val cover = storyMedia.first()
                val memInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isMemPressed by memInteractionSource.collectIsPressedAsState()
                val memScale by animateFloatAsState(
                    targetValue = if (isMemPressed) 0.94f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "mem_card_scale"
                )
                Box(
                    Modifier.width(132.dp).height(184.dp)
                        .trackThumbnailBounds(cover.uri)
                        .graphicsLayer {
                            scaleX = memScale
                            scaleY = memScale
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = memInteractionSource,
                            indication = ripple(bounded = true),
                            onClick = { onOpen(cover) }
                        )
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = remember(cover.uri, context) {
                            ThumbnailUtils.buildThumbnailRequest(context, cover.uri, 264, 368)
                        },
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            alpha = if (ThumbnailBoundsTracker.activeViewerUri == cover.uri) 0f else 1f
                        },
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxWidth().height(78.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${storyMedia.size} photos and videos",
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JustifiedMosaicRow(
    items: List<MediaItem>,
    containerWidth: Dp,
    rowIndex: Int,
    isSquare: Boolean,
    selectedUris: Set<String> = emptySet(),
    onLongPress: (MediaItem) -> Unit = {},
    onToggleSelection: (MediaItem) -> Unit = {},
    onOpen: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return

    val aspects = remember(items, isSquare) {
        items.mapIndexed { index, item ->
            if (isSquare) 1f else if (item.width > 0 && item.height > 0) {
                (item.width.toFloat() / item.height.toFloat()).coerceIn(0.55f, 1.8f)
            } else {
                when ((item.id.hashCode() + rowIndex + index).mod(4)) {
                    0 -> 0.72f
                    1 -> 0.9f
                    2 -> 1.0f
                    else -> 1.35f
                }
            }
        }
    }

    val gap = 4.dp
    val totalGap = gap * (items.size - 1)
    val aspectsSum = remember(aspects) { aspects.sum().coerceAtLeast(0.1f) }
    val rowHeight = remember(containerWidth, totalGap, aspectsSum) {
        ((containerWidth - totalGap) / aspectsSum).coerceIn(72.dp, 220.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        items.forEachIndexed { index, item ->
            MosaicMediaItem(
                item = item,
                modifier = Modifier
                    .weight(aspects[index])
                    .height(rowHeight),
                selected = item.uri in selectedUris,
                onLongPress = { onLongPress(item) },
                onToggleSelection = { onToggleSelection(item) },
                onOpen = onOpen
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MosaicMediaItem(
    item: MediaItem,
    modifier: Modifier,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelection: () -> Unit = {},
    onOpen: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri, context) {
        ThumbnailUtils.buildThumbnailRequest(context, item.uri)
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemScale = if (isPressed) 0.95f else 1f

    Box(
        modifier = modifier
            .trackThumbnailBounds(item.uri)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                onClick = { if (selected) onToggleSelection() else onOpen(item) },
                onLongClick = { onLongPress() }
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
            },
            contentScale = ContentScale.Crop
        )
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.padding(4.dp))
            }
        }
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .graphicsLayer {
                        alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                    }
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(3.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MediaGridItem(
    item: MediaItem,
    isSquare: Boolean,
    colIndex: Int,
    onOpen: (MediaItem) -> Unit
) {
    val aspect = if (isSquare) 1f else {
        if (item.width > 0 && item.height > 0) {
            (item.width.toFloat() / item.height.toFloat()).coerceIn(0.65f, 1.6f)
        } else {
            when ((item.id.hashCode() + colIndex).mod(4)) {
                0 -> 0.78f
                1 -> 1.0f
                2 -> 0.88f
                else -> 1.25f
            }
        }
    }

    val context = LocalContext.current
    val imageRequest = remember(item.uri, context) {
        ThumbnailUtils.buildThumbnailRequest(context, item.uri)
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val itemScale = if (isPressed) 0.95f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .trackThumbnailBounds(item.uri)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                onClick = { onOpen(item) }
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
            },
            contentScale = ContentScale.Crop
        )
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .graphicsLayer {
                        alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                    }
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(3.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}
