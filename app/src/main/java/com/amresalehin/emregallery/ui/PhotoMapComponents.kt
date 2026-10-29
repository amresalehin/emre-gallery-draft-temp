package com.amresalehin.emregallery.ui

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.amresalehin.emregallery.R
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

@Composable
fun PhotoMapTopBar(
    mapLayer: String,
    showMapLayerMenu: Boolean,
    onBack: () -> Unit,
    onOpenLayerMenu: () -> Unit,
    onDismissLayerMenu: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onFitAllPhotos: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.weight(1f))
        Box {
            Surface(
                onClick = onOpenLayerMenu,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (mapLayer == "Satellite") Icons.Default.SatelliteAlt else Icons.Default.Layers,
                        contentDescription = "Map layer: $mapLayer",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            DropdownMenu(expanded = showMapLayerMenu, onDismissRequest = onDismissLayerMenu) {
                DropdownMenuItem(
                    text = { Text("Standard map") },
                    leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                    trailingIcon = { if (mapLayer == "Standard") Icon(Icons.Default.Check, contentDescription = "Selected") },
                    onClick = { onSelectLayer("Standard") }
                )
                DropdownMenuItem(
                    text = { Text("Satellite") },
                    leadingIcon = { Icon(Icons.Default.SatelliteAlt, contentDescription = null) },
                    trailingIcon = { if (mapLayer == "Satellite") Icon(Icons.Default.Check, contentDescription = "Selected") },
                    onClick = { onSelectLayer("Satellite") }
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onFitAllPhotos,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FilterCenterFocus, contentDescription = "Fit all media", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }

    }
}

/** Container for the interactive map and its heatmap overlay. */
@Composable
fun PhotoMapViewContainer(
    locatedMedia: List<Pair<MediaItem, GeoPoint>>,
    mapLayer: String,
    loading: Boolean,
    scanProgress: Int,
    scanTotal: Int,
    selectedMedia: Pair<MediaItem, GeoPoint>? = null,
    onOpenSelected: () -> Unit = {},
    onMapReady: (MapView) -> Unit,
    onUserMapGesture: () -> Unit = {},
    onBoundsChanged: (BoundingBox?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heatmapPoints = remember(locatedMedia) { locatedMedia.map { it.second } }
    val heatmapOverlay = remember { PhotoHeatmapOverlay() }
    val selectedMarker = remember { mutableStateOf<Marker?>(null) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("photo_map_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        clipToOutline = true
                        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                        setTileSource(if (mapLayer == "Satellite") PhotoMapTiles.satelliteTileSource() else TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        setVerticalMapRepetitionEnabled(false)
                        setHorizontalMapRepetitionEnabled(false)
                        setScrollableAreaLimitDouble(BoundingBox(85.0511, 180.0, -85.0511, -180.0))
                        val initialFitZoom = calculateWorldFitZoom(this)
                        minZoomLevel = initialFitZoom
                        controller.setZoom(initialFitZoom)
                        controller.setCenter(GeoPoint(0.0, 0.0))
                        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val newW = right - left
                            val newH = bottom - top
                            val oldW = oldRight - oldLeft
                            val oldH = oldBottom - oldTop
                            if (newW > 0 && newH > 0 && (newW != oldW || newH != oldH)) {
                                val fitZoom = calculateWorldFitZoom(this)
                                minZoomLevel = fitZoom
                                if (zoomLevelDouble < fitZoom) {
                                    controller.setZoom(fitZoom)
                                }
                            }
                        }
                        setOnTouchListener { _, event ->
                            if (event.actionMasked == MotionEvent.ACTION_MOVE ||
                                event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
                            ) {
                                onUserMapGesture()
                            }
                            // Observe gestures only; let MapView retain normal touch handling.
                            false
                        }
                        onMapReady(this)
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                onBoundsChanged(calculateMapBounds(this@apply, ctx))
                                return true
                            }
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                val fitZoom = calculateWorldFitZoom(this@apply)
                                if (this@apply.zoomLevelDouble < fitZoom) {
                                    this@apply.controller.setZoom(fitZoom)
                                }
                                onBoundsChanged(calculateMapBounds(this@apply, ctx))
                                return true
                            }
                        })
                    }
                },
                update = { map ->
                    onMapReady(map)
                    val fitZoom = calculateWorldFitZoom(map)
                    map.minZoomLevel = fitZoom
                    heatmapOverlay.updatePoints(heatmapPoints)
                    if (!map.overlays.contains(heatmapOverlay)) {
                        map.overlays.add(heatmapOverlay)
                    }

                    val selected = selectedMedia
                    if (selected == null) {
                        selectedMarker.value?.let { map.overlays.remove(it) }
                        selectedMarker.value = null
                    } else {
                        val marker = selectedMarker.value ?: Marker(map).apply {
                            icon = ContextCompat.getDrawable(map.context, R.drawable.ic_map_selection_pin)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }.also { selectedMarker.value = it }
                        marker.position = selected.second
                        marker.title = selected.first.name
                        if (!map.overlays.contains(marker)) map.overlays.add(marker)
                    }

                    map.post { onBoundsChanged(calculateMapBounds(map, context)) }
                    map.invalidate()
                }
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp)
            ) {
                Text("© OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }

            if (selectedMedia != null) {
                Surface(
                    onClick = onOpenSelected,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open selected media",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (loading && scanTotal > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text("Indexing coordinates ($scanProgress/$scanTotal)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

fun calculateMapBounds(map: MapView, context: Context): BoundingBox? {
    if (map.width <= 0 || map.height <= 0) return null
    val density = context.resources.displayMetrics.density
    val topPx = (8f * density).toInt().coerceIn(0, map.height - 1)
    val bottomPx = (map.height - 8f * density).toInt().coerceIn(topPx + 1, map.height)
    val northWest = map.projection.fromPixels(0, topPx)
    val southEast = map.projection.fromPixels(map.width, bottomPx)
    val latNorth = maxOf(northWest.latitude, southEast.latitude).coerceIn(-85.0511, 85.0511)
    val latSouth = minOf(northWest.latitude, southEast.latitude).coerceIn(-85.0511, 85.0511)
    val lonWest = northWest.longitude.coerceIn(-180.0, 180.0)
    val lonEast = southEast.longitude.coerceIn(-180.0, 180.0)
    return BoundingBox(latNorth, lonEast, latSouth, lonWest)
}

/**
 * Bottom card displaying photos within the visible viewport, grouped by day.
 */
@Composable
fun PhotoMapDetailsCard(
    viewportCount: Int,
    locatedTotal: Int,
    loading: Boolean,
    scanProgress: Int = 0,
    scanTotal: Int = 0,
    dayGroups: List<Pair<String, List<Pair<MediaItem, GeoPoint>>>>,
    onPhotoClick: (MediaItem, GeoPoint) -> Unit,
    onFitAllPhotos: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp).testTag("photo_map_details_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        loading && locatedTotal == 0 -> "Scanning media for locations…"
                        !loading && locatedTotal == 0 -> "No geotagged media"
                        else -> "%,d items in this area".format(Locale.getDefault(), viewportCount)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (locatedTotal > 0 && viewportCount != locatedTotal) {
                    Text("of $locatedTotal located", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (loading && locatedTotal == 0) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning media locations…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (scanTotal > 0) "Reading GPS metadata ($scanProgress of $scanTotal scanned)" else "Scanning your gallery for GPS metadata…",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else if (!loading && locatedTotal == 0) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No media with GPS data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("None of the images or videos in this gallery contain accessible GPS coordinates.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else if (viewportCount == 0) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No media in this area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Pan or zoom out to see media, or tap below to frame all $locatedTotal items.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onFitAllPhotos, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fit all media", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dayGroups.forEach { (day, entries) ->
                        item(key = "heading_$day", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                Text("${entries.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(entries, key = { it.first.uri }) { (photo, point) ->
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPhotoClick(photo, point) }
                            ) {
                                AsyncImage(
                                    model = remember(photo.uri, context) {
                                        ThumbnailUtils.buildThumbnailRequest(context, photo.uri)
                                    },
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (photo.isVideo) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.58f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
