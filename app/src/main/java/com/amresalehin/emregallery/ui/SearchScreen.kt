package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    stage: SearchStage,
    results: List<MediaItem>,
    recentSearches: List<String>,
    onQuery: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    videosOnly: Boolean,
    onVideosOnly: (Boolean) -> Unit
) {
    val returnProgress = LocalViewerCoordinatedProgress.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp)
                .destinationTopBarEntrance(returnProgress),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search photos, people, places…", maxLines = 1) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear query") }
                        }
                        IconButton(onClick = { onVideosOnly(!videosOnly) }) { Icon(Icons.Default.FilterList, "Toggle video filter") }
                    }
                },
                shape = CircleShape
            )
        }
        when (stage) {
            SearchStage.DISCOVERY -> LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Person recognition will appear when configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Text("Places", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Search location metadata when available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Favourites", "Camera", "Videos", "Recently added")) { label ->
                            AssistChip(onClick = { onQuery(label); onSubmit(label) }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.PhotoAlbum, null) })
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearRecent) { Text("Clear all") }
                    }
                    if (recentSearches.isEmpty()) {
                        Text("Your recent searches will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        recentSearches.forEach { item ->
                            ListItem(headlineContent = { Text(item) }, leadingContent = { Icon(Icons.Default.History, null) }, modifier = Modifier.clickable { onRecent(item) })
                        }
                    }
                }
            }
            SearchStage.TYPING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !videosOnly, onClick = { onVideosOnly(false) }, label = { Text("Photos & videos") })
                    FilterChip(selected = videosOnly, onClick = { onVideosOnly(true) }, label = { Text("Videos") })
                }
                LazyColumn {
                    val matches = results.filter { it.name.contains(query, true) || it.relativePath.contains(query, true) }.take(8)
                    items(matches) { item ->
                        ListItem(
                            headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(item.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(item.uri))
                                        .size(150, 150)
                                        .crossfade(true)
                                        .crossfade(150)
                                        .diskCacheKey("${item.uri}_search_icon")
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCacheKey("${item.uri}_search_icon")
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            },
                            trailingContent = { Icon(Icons.Default.NorthWest, null) },
                            modifier = Modifier.clickable { onOpen(item) }
                        )
                    }
                    item { TextButton(onClick = { onSubmit(query) }, modifier = Modifier.fillMaxWidth()) { Text("Search for ‘$query’") } }
                }
            }
            SearchStage.RESULTS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(query.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("${results.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onVideosOnly(!videosOnly) }) { Icon(Icons.Default.FilterList, "Filter results") }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    gridItems(results) { item ->
                        val context = LocalContext.current
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(.82f)
                                .trackThumbnailBounds(item.uri)
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { onOpen(item) }
                        ) {
                            AsyncImage(
                                model = remember(item.uri, context) {
                                    ThumbnailUtils.buildThumbnailRequest(context, item.uri)
                                },
                                item.name,
                                Modifier.fillMaxSize().graphicsLayer {
                                    alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                                },
                                contentScale = ContentScale.Crop
                            )
                            if (item.isVideo) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null,
                                    Modifier.align(Alignment.BottomEnd).padding(4.dp).graphicsLayer {
                                        alpha = if (ThumbnailBoundsTracker.activeViewerUri == item.uri) 0f else 1f
                                    },
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
