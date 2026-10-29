package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun IndexingMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FaceScanOptionsDialog(
    onDismiss: () -> Unit,
    onRescanAll: () -> Unit,
    onRemainingOnly: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rescan faces") },
        text = { Text("Choose whether to scan only photos that have not been indexed, or clear the existing face index and scan every photo again.") },
        confirmButton = {
            TextButton(onClick = onRescanAll) { Text("Rescan all") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onRemainingOnly) { Text("Remaining only") }
            }
        }
    )
}

@Composable
fun ModelDownloadRequiredDialog(
    onDismiss: () -> Unit,
    onDownloadModel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buffalo_L Model Required") },
        text = { Text("Face recognition uses the on-device Buffalo_L model (InsightFace SCRFD + ArcFace 512-d). Please download the model pack to enable face detection and identity clustering.") },
        confirmButton = {
            Button(onClick = onDownloadModel) { Text("Download model") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
