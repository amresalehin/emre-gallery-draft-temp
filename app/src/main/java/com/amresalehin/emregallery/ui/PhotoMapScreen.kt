package com.amresalehin.emregallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

const val WORLD_VIEW_LAT_NORTH = 85.0511
const val WORLD_VIEW_LAT_SOUTH = -85.0511
const val WORLD_VIEW_LON_WEST = -180.0
const val WORLD_VIEW_LON_EAST = 180.0

val WORLD_VIEW_CENTER = GeoPoint(0.0, 0.0)
val WORLD_VIEW_BOUNDS = BoundingBox(
    WORLD_VIEW_LAT_NORTH,
    WORLD_VIEW_LON_EAST,
    WORLD_VIEW_LAT_SOUTH,
    WORLD_VIEW_LON_WEST
)

/**
 * Calculates the zoom level required for a single Mercator world frame (256 * 2^z)
 * to cleanly cover the container without vertical or horizontal repeating.
 * Uses the nearest bounding constraint (whichever dimension—width or height—fits first without repeating).
 */
fun calculateWorldFitZoom(map: MapView): Double {
    if (map.width <= 0 || map.height <= 0) return 1.0
    val maxDim = maxOf(map.width, map.height).toDouble()
    return (kotlin.math.ln(maxDim / 256.0) / kotlin.math.ln(2.0)).coerceAtLeast(1.0)
}

/**
 * Fits the entire world view automatically, framing the viewport cleanly to fill
 * the container using the nearest bounding constraint without repeating.
 */
fun showWorldView(map: MapView, animated: Boolean = false) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { showWorldView(map, animated) }
        return
    }
    val targetZoom = calculateWorldFitZoom(map)
    map.minZoomLevel = targetZoom
    if (animated) {
        map.controller.animateTo(WORLD_VIEW_CENTER)
        map.controller.setZoom(targetZoom)
    } else {
        map.controller.setCenter(WORLD_VIEW_CENTER)
        map.controller.setZoom(targetZoom)
    }
    map.invalidate()
}

fun focusSinglePhoto(map: MapView, point: GeoPoint, animated: Boolean = true) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { focusSinglePhoto(map, point, animated) }
        return
    }
    if (animated) map.controller.animateTo(point) else map.controller.setCenter(point)
    map.controller.setZoom(15.0)
    map.invalidate()
}

/**
 * Computes the geographic bounding box of a list of coordinates with safety margins,
 * clamped strictly to valid Web Mercator world bounds.
 */
fun computeMediaBoundingBox(points: List<GeoPoint>): BoundingBox? {
    if (points.isEmpty()) return null
    if (points.size == 1) {
        val pt = points.first()
        return BoundingBox(
            (pt.latitude + 0.005).coerceAtMost(WORLD_VIEW_LAT_NORTH),
            (pt.longitude + 0.005).coerceAtMost(WORLD_VIEW_LON_EAST),
            (pt.latitude - 0.005).coerceAtLeast(WORLD_VIEW_LAT_SOUTH),
            (pt.longitude - 0.005).coerceAtLeast(WORLD_VIEW_LON_WEST)
        )
    }
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val latSpan = north - south
    val lonSpan = kotlin.math.abs(east - west)

    val latMargin = (latSpan * 0.02).coerceAtLeast(0.0001)
    val lonMargin = (lonSpan * 0.02).coerceAtLeast(0.0001)
    return BoundingBox(
        (north + latMargin).coerceAtMost(WORLD_VIEW_LAT_NORTH),
        (east + lonMargin).coerceAtMost(WORLD_VIEW_LON_EAST),
        (south - latMargin).coerceAtLeast(WORLD_VIEW_LAT_SOUTH),
        (west - lonMargin).coerceAtLeast(WORLD_VIEW_LON_WEST)
    )
}

/**
 * Fits the given bounding box on the map with standard edge padding.
 */
fun fitBounds(
    map: MapView,
    bounds: BoundingBox,
    animated: Boolean = true,
    paddingDp: Int = 48
) {
    if (map.width <= 0 || map.height <= 0) {
        map.post { fitBounds(map, bounds, animated, paddingDp) }
        return
    }
    val density = map.context.resources.displayMetrics.density
    val standardPaddingPx = (paddingDp * density).toInt()
    val maxPaddingX = (map.width * 0.2f).toInt()
    val maxPaddingY = (map.height * 0.2f).toInt()
    val paddingPx = minOf(standardPaddingPx, maxPaddingX, maxPaddingY).coerceAtLeast(0)

    runCatching {
        map.zoomToBoundingBox(bounds, animated, paddingPx)
        if (map.zoomLevelDouble > 15.0) {
            map.controller.setZoom(15.0)
        }
        val minZoom = calculateWorldFitZoom(map)
        if (map.zoomLevelDouble < minZoom) {
            map.controller.setZoom(minZoom)
        }
        map.invalidate()
    }
}

/**
 * Fits a list of coordinates on the map.
 * When empty, falls back gracefully to single world fit.
 */
fun fitPointsToMap(
    map: MapView,
    points: List<GeoPoint>,
    animated: Boolean = true,
    paddingDp: Int = 48
) {
    if (points.isEmpty()) {
        showWorldView(map, animated)
        return
    }
    if (points.size == 1) {
        focusSinglePhoto(map, points.first(), animated)
        return
    }
    val bounds = computeMediaBoundingBox(points)
    if (bounds != null) {
        fitBounds(map, bounds, animated, paddingDp)
    } else {
        showWorldView(map, animated)
    }
}

@Composable
fun PhotoMapScreen(
    media: List<MediaItem>,
    focusItem: MediaItem? = null,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    viewModel: PhotoMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.rescanMedia(media)
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            !hasMediaLocationPermission(context)
        ) {
            locationPermLauncher.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var initialWorldFramed by remember(media) { mutableStateOf(false) }
    var allMediaFramed by remember(media) { mutableStateOf(false) }
    var selectedMapMedia by remember { mutableStateOf<Pair<MediaItem, GeoPoint>?>(null) }
    var userHasMovedMap by remember(media) { mutableStateOf(false) }
    var lastFocusedUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(media) {
        viewModel.loadAndScanMedia(media)
    }

    // Direct camera focus for requested item
    LaunchedEffect(focusItem?.uri, mapRef) {
        val requestedItem = focusItem ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        if (lastFocusedUri == requestedItem.uri) return@LaunchedEffect

        val point = viewModel.getLocationForMedia(context, requestedItem.uri)
        if (point != null) {
            selectedMapMedia = requestedItem to point
            focusSinglePhoto(map, point, animated = true)
            lastFocusedUri = requestedItem.uri
        } else {
            lastFocusedUri = requestedItem.uri
            snackbarHostState.showSnackbar(
                message = "Cannot focus on \"${requestedItem.name}\": no accessible GPS coordinates found.",
                duration = SnackbarDuration.Short
            )
            val points = uiState.locatedMedia.map { it.second }
            if (!userHasMovedMap && points.isNotEmpty()) {
                fitPointsToMap(map, points, animated = true)
            } else if (!userHasMovedMap) {
                showWorldView(map, animated = true)
            }
        }
    }

    // Simplified Initial Fit Logic: Two-state rule based on indexing status
    LaunchedEffect(mapRef, uiState.loading, uiState.locatedMedia.size) {
        if (focusItem != null) return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect

        if (uiState.loading) {
            // 1. While Indexing is in Progress:
            // Fit the entire world view automatically.
            // Frame the world viewport to fill the container cleanly using the nearest bounding constraint.
            if (!initialWorldFramed && !userHasMovedMap) {
                showWorldView(map, animated = false)
                initialWorldFramed = true
            }
        } else {
            // 2. When All Media / Geotags are Indexed & Cached:
            // Compute the geographic bounding box of all available geotagged items.
            // If valid coordinates exist, call fitBounds(allMediaBounds, padding) with standard edge padding.
            // If no items contain geotags, gracefully fall back to the single world fit.
            if (!allMediaFramed && !userHasMovedMap) {
                val geotaggedPoints = uiState.locatedMedia.map { it.second }
                if (geotaggedPoints.isNotEmpty()) {
                    val allMediaBounds = computeMediaBoundingBox(geotaggedPoints)
                    if (allMediaBounds != null) {
                        if (geotaggedPoints.size == 1) {
                            focusSinglePhoto(map, geotaggedPoints.first(), animated = initialWorldFramed)
                        } else {
                            fitBounds(map, allMediaBounds, animated = initialWorldFramed)
                        }
                    } else {
                        showWorldView(map, animated = initialWorldFramed)
                    }
                } else {
                    showWorldView(map, animated = initialWorldFramed)
                }
                allMediaFramed = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapRef?.onPause()
            mapRef?.onDetach()
            mapRef = null
        }
    }

    val onFitAllPhotos: () -> Unit = {
        selectedMapMedia = null
        val map = mapRef
        if (map != null) {
            val geotaggedPoints = uiState.locatedMedia.map { it.second }
            if (geotaggedPoints.isNotEmpty()) {
                val allMediaBounds = computeMediaBoundingBox(geotaggedPoints)
                if (allMediaBounds != null) {
                    if (geotaggedPoints.size == 1) {
                        focusSinglePhoto(map, geotaggedPoints.first(), animated = true)
                    } else {
                        fitBounds(map, allMediaBounds, animated = true)
                    }
                } else {
                    showWorldView(map, animated = true)
                }
            } else {
                showWorldView(map, animated = true)
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            PhotoMapTopBar(
                mapLayer = uiState.mapLayer,
                showMapLayerMenu = uiState.showMapLayerMenu,
                onBack = onBack,
                onOpenLayerMenu = { viewModel.setLayerMenuExpanded(true) },
                onDismissLayerMenu = { viewModel.setLayerMenuExpanded(false) },
                onSelectLayer = { layer ->
                    viewModel.setMapLayer(layer)
                    mapRef?.let { map ->
                        map.setTileSource(
                            if (layer == "Satellite") PhotoMapTiles.satelliteTileSource()
                            else TileSourceFactory.MAPNIK
                        )
                        map.invalidate()
                    }
                },
                onFitAllPhotos = onFitAllPhotos
            )

            PhotoMapViewContainer(
                locatedMedia = uiState.locatedMedia,
                mapLayer = uiState.mapLayer,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                selectedMedia = selectedMapMedia,
                onOpenSelected = { selectedMapMedia?.first?.let(onOpen) },
                onMapReady = { map -> mapRef = map },
                onUserMapGesture = { userHasMovedMap = true },
                onBoundsChanged = { bounds -> viewModel.updateVisibleMapBounds(bounds) },
                modifier = Modifier.weight(1f)
            )

            PhotoMapDetailsCard(
                viewportCount = uiState.viewportMedia.size,
                locatedTotal = uiState.locatedMedia.size,
                loading = uiState.loading,
                scanProgress = uiState.scanProgress,
                scanTotal = uiState.scanTotal,
                dayGroups = uiState.dayGroups,
                onPhotoClick = { photo, point ->
                    selectedMapMedia = photo to point
                    mapRef?.let { focusSinglePhoto(it, point, animated = true) }
                },
                onFitAllPhotos = onFitAllPhotos,
                modifier = Modifier.weight(1f)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}
