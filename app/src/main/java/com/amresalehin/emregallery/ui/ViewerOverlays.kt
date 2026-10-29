package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Top and bottom edge gradient shading behind the overlay controls.
 */
@Composable
fun BoxScope.ViewerEdgeShading(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(260)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val edgeShadeHeight = maxHeight * 0.22f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(edgeShadeHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.Black.copy(alpha = 0.38f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(edgeShadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.68f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Top action bar with back, share, favorite, and more options.
 */
@Composable
fun ViewerTopAppBar(
    isFavourite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Spacer(Modifier.weight(1f))
        ViewerButton(Icons.Default.Share, "Share", onShare)
        ViewerButton(
            icon = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = "Favourite",
            onClick = onToggleFavourite
        )
        ViewerButton(Icons.Default.MoreVert, "More", onMore)
    }
}

/**
 * Bottom action bar with Edit and Delete/Restore buttons.
 */
@Composable
fun ViewerBottomActionBar(
    isBin: Boolean,
    onEdit: () -> Unit,
    onToggleBin: () -> Unit,
    onDeletePermanently: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBin) {
            ViewerAction(
                icon = Icons.Default.Restore,
                label = "Restore",
                onClick = onToggleBin
            )
            if (onDeletePermanently != null) {
                ViewerAction(
                    icon = Icons.Default.DeleteForever,
                    label = "Delete permanently",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onDeletePermanently
                )
            }
        } else {
            ViewerAction(Icons.Default.Tune, "Edit", onEdit)
            ViewerAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onToggleBin
            )
        }
    }
}

/**
 * Bottom sheet for extra media actions (Archive, Lock, Bin, Info).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerMoreBottomSheet(
    isBin: Boolean,
    isHidden: Boolean,
    isArchived: Boolean,
    onToggleBin: () -> Unit,
    onToggleHide: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeletePermanently: (() -> Unit)? = null,
    onShowDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (isBin) {
            ListItem(
                headlineContent = { Text("Restore to gallery") },
                leadingContent = { Icon(Icons.Default.Restore, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleBin() }
            )
            if (onDeletePermanently != null) {
                ListItem(
                    headlineContent = { Text("Delete permanently from device") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.clickable { onDismiss(); onDeletePermanently() }
                )
            }
        } else {
            ListItem(
                headlineContent = { Text(if (isHidden) "Unhide (return to gallery)" else "Move to Locked folder") },
                leadingContent = { Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.Lock, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleHide() }
            )
            ListItem(
                headlineContent = { Text(if (isArchived) "Unarchive" else "Archive") },
                leadingContent = { Icon(Icons.Default.Archive, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleArchive() }
            )
            ListItem(
                headlineContent = { Text("Move to Bin") },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                modifier = Modifier.clickable { onDismiss(); onToggleBin() }
            )
        }
        ListItem(
            headlineContent = { Text("Media information") },
            leadingContent = { Icon(Icons.Default.Info, null) },
            modifier = Modifier.clickable { onDismiss(); onShowDetails() }
        )
        ListItem(
            headlineContent = { Text("Cancel") },
            leadingContent = { Icon(Icons.Default.Close, null) },
            modifier = Modifier.clickable { onDismiss() }
        )
        Spacer(Modifier.height(24.dp))
    }
}
