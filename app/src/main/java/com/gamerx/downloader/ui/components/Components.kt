package com.gamerx.downloader.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.model.DownloadStatus
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.ui.theme.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun DownloadCard(
    item: DownloadEntity,
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onOpen: () -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showLogs by remember { mutableStateOf(false) }
    var logsText by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (showLogs) {
        LaunchedEffect(showLogs) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val logFile = File(context.cacheDir, "logs/download_${item.id}.log")
                if (logFile.exists()) {
                    logsText = try { logFile.readText() } catch (e: Exception) { "Error reading logs" }
                } else {
                    logsText = "No logs available."
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("Download Logs") },
            text = {
                Box(modifier = Modifier.fillMaxHeight(0.6f)) {
                    Text(
                        text = logsText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogs = false }) { Text("Close") }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (item.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (item.type == DownloadType.Audio)
                            Icons.Filled.MusicNote else Icons.Filled.VideoFile,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Duration badge
                if (item.duration > 0) {
                    val hours = item.duration / 3600
                    val mins = (item.duration % 3600) / 60
                    val secs = item.duration % 60
                    val durationText = if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
                    else "%d:%02d".format(mins, secs)

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info section
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (item.author.isNotBlank()) {
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val (statusColor, statusText) = when (item.status) {
                        DownloadStatus.Active -> MaterialTheme.colorScheme.primary to (
                            if (item.stage.isNotBlank() && item.stage != "Starting") {
                                val stageIcon = when {
                                    item.stage.contains("Fetching") -> "🔍"
                                    item.stage.contains("Downloading video") -> "⬇️🎬"
                                    item.stage.contains("Downloading audio") -> "⬇️🎵"
                                    item.stage.contains("Downloading") -> "⬇️"
                                    item.stage.contains("Merging") -> "🔀"
                                    item.stage.contains("Post-processing") -> "⚙️"
                                    item.stage.contains("Saving") -> "💾"
                                    else -> "📥"
                                }
                                "$stageIcon ${item.stage} • ${item.progressText.ifBlank { "..." }}"
                            }
                            else item.progressText.ifBlank { "Downloading..." }
                        )
                        DownloadStatus.Queued -> NeonOrange to "Queued"
                        DownloadStatus.Completed -> NeonGreen to "Completed"
                        DownloadStatus.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant to "Cancelled"
                        DownloadStatus.Error -> MaterialTheme.colorScheme.error to (item.errorMessage ?: "Error")
                        DownloadStatus.Paused -> NeonYellow to "Paused"
                        DownloadStatus.Processing -> MaterialTheme.colorScheme.primary to "Processing..."
                    }

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor),
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    // Type badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (item.type) {
                            DownloadType.Audio -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            DownloadType.Video -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            DownloadType.Command -> NeonOrange.copy(alpha = 0.15f)
                        },
                    ) {
                        Text(
                            text = item.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.type) {
                                DownloadType.Audio -> MaterialTheme.colorScheme.secondary
                                DownloadType.Video -> MaterialTheme.colorScheme.primary
                                DownloadType.Command -> NeonOrange
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Progress bar for active downloads
                if (item.status == DownloadStatus.Active && item.progress > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    // Speed + ETA row
                    if (item.speed.isNotBlank() || item.eta.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (item.speed.isNotBlank()) {
                                Text(
                                    text = item.speed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                )
                            }
                            if (item.eta.isNotBlank()) {
                                Text(
                                    text = "ETA: ${item.eta}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                } else if (item.status == DownloadStatus.Active) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (item.status) {
                    DownloadStatus.Active, DownloadStatus.Queued -> {
                        IconButton(onClick = { showLogs = true }) {
                            Icon(
                                Icons.Outlined.ReceiptLong,
                                contentDescription = "Logs",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = onCancel) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = NeonRed.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DownloadStatus.Error, DownloadStatus.Cancelled -> {
                        IconButton(onClick = { showLogs = true }) {
                            Icon(
                                Icons.Outlined.ReceiptLong,
                                contentDescription = "Logs",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Retry",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = NeonRed.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DownloadStatus.Completed -> {
                        IconButton(onClick = onOpen) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = "Open",
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = ElectricPurple,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBright = MaterialTheme.colorScheme.surface
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            surfaceVariant,
            surfaceBright,
            surfaceVariant,
        ),
        start = Offset(translateAnim - 200, translateAnim - 200),
        end = Offset(translateAnim, translateAnim),
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmerBrush),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush),
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
) {
    EmptyStateView(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp),
            )
        },
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    )
}
