package com.gamerx.downloader.ui.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo
import com.gamerx.downloader.data.model.PlaylistInfo
import com.gamerx.downloader.data.model.VideoInfo
import com.gamerx.downloader.ui.components.ShimmerCard
import com.gamerx.downloader.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Handle shared URL
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.processSharedUrl(sharedUrl)
            onSharedUrlConsumed()
        }
    }

    // Handle download started toast
    LaunchedEffect(state.downloadStarted) {
        if (state.downloadStarted) {
            Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
            viewModel.consumeDownloadStarted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header / Logo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Column {
                Text(
                    text = "GamerX",
                    style = MaterialTheme.typography.displaySmall.copy(
                        brush = Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                    ),
                )
                Text(
                    text = "Downloader",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // URL Input Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(
                width = 1.dp,
                brush = if (state.isLoading) Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) else Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant)
                ),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Paste a video or playlist URL...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingIcon = {
                        Row {
                            // Paste button
                            IconButton(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    if (text.isNotBlank()) {
                                        viewModel.onUrlChanged(text)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Clear button
                            if (state.url.isNotBlank()) {
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Download button
                Button(
                    onClick = { viewModel.fetchVideoInfo() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = state.url.isNotBlank() && !state.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                    ),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching info...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        // Error message
        AnimatedVisibility(visible = state.error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = NeonRed,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonRed,
                    )
                }
            }
        }

        // Loading shimmer
        if (state.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            repeat(3) { ShimmerCard() }
        }

        // Recent Downloads Section
        if (state.recentDownloads.isNotEmpty() && !state.isLoading) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Recent Downloads",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                state.recentDownloads.forEach { download ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = download.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = download.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (download.completedAt != null) "Completed" else "In Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (download.completedAt != null) NeonCyan else NeonOrange,
                                )
                            }
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Supported sites section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Supported Platforms",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val sites = listOf(
                    "YouTube", "Twitter/X", "TikTok", "Instagram",
                    "Facebook", "Reddit", "Twitch", "SoundCloud",
                    "Vimeo", "Dailymotion", "Pinterest", "1000+ more"
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sites.forEach { site ->
                        SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    site,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Download Bottom Sheet (single video)
    if (state.showDownloadSheet && state.videoInfo != null) {
        DownloadBottomSheet(
            videoInfo = state.videoInfo!!,
            selectedType = state.selectedType,
            selectedFormat = state.selectedFormat,
            selectedContainer = state.selectedContainer,
            extraCommands = state.extraCommands,
            showAdvanced = state.showAdvanced,
            scheduledAt = state.scheduledAt,
            onTypeSelected = viewModel::setDownloadType,
            onFormatSelected = viewModel::setSelectedFormat,
            onContainerSelected = viewModel::setContainer,
            onExtraCommandsChanged = viewModel::setExtraCommands,
            onToggleAdvanced = viewModel::toggleAdvanced,
            onScheduleSelected = viewModel::setScheduledAt,
            onClearSchedule = viewModel::clearSchedule,
            onDownload = viewModel::startDownload,
            onDismiss = viewModel::dismissDownloadSheet,
        )
    }

    // Playlist Bottom Sheet
    if (state.showPlaylistSheet && state.playlistInfo != null) {
        PlaylistBottomSheet(
            playlist = state.playlistInfo!!,
            selectedIndices = state.selectedPlaylistIndices,
            selectedType = state.selectedType,
            selectedContainer = state.selectedContainer,
            onTypeSelected = viewModel::setDownloadType,
            onContainerSelected = viewModel::setContainer,
            onToggleEntry = viewModel::togglePlaylistEntry,
            onSelectAll = viewModel::selectAllPlaylistEntries,
            onDeselectAll = viewModel::deselectAllPlaylistEntries,
            onDownload = viewModel::startBatchDownload,
            onDismiss = viewModel::dismissPlaylistSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadBottomSheet(
    videoInfo: VideoInfo,
    selectedType: DownloadType,
    selectedFormat: FormatInfo?,
    selectedContainer: String,
    extraCommands: String,
    showAdvanced: Boolean,
    scheduledAt: Long,
    onTypeSelected: (DownloadType) -> Unit,
    onFormatSelected: (FormatInfo) -> Unit,
    onContainerSelected: (String) -> Unit,
    onExtraCommandsChanged: (String) -> Unit,
    onToggleAdvanced: () -> Unit,
    onScheduleSelected: (Long) -> Unit,
    onClearSchedule: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = ScrimDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Video info header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (videoInfo.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = videoInfo.thumbnail,
                            contentDescription = videoInfo.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (videoInfo.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = videoInfo.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (videoInfo.durationText.isNotBlank()) {
                        Text(
                            text = videoInfo.durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Download type tabs
            Text(
                text = "Download As",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    DownloadType.Video to Icons.Filled.VideoFile,
                    DownloadType.Audio to Icons.Filled.MusicNote,
                ).forEach { (type, icon) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        label = { Text(type.name) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.outline,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == type,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Container selection  
            Text(
                text = "Container",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val containers = when (selectedType) {
                DownloadType.Video -> listOf("mp4", "mkv", "webm")
                DownloadType.Audio -> listOf("mp3", "m4a", "opus", "flac", "wav")
                DownloadType.Command -> listOf("")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                containers.forEach { container ->
                    FilterChip(
                        selected = selectedContainer == container,
                        onClick = { onContainerSelected(container) },
                        label = { Text(container.uppercase()) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricPurple.copy(alpha = 0.15f),
                            selectedLabelColor = ElectricPurple,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedContainer == container,
                            selectedBorderColor = ElectricPurple.copy(alpha = 0.3f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Format selection
            val formats = when (selectedType) {
                DownloadType.Audio -> videoInfo.audioFormats
                DownloadType.Video -> videoInfo.videoFormats
                DownloadType.Command -> emptyList()
            }

            if (formats.isNotEmpty()) {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                formats.take(10).forEach { format ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedFormat?.formatId == format.formatId)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = if (selectedFormat?.formatId == format.formatId)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null,
                        onClick = { onFormatSelected(format) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = format.displayQuality,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selectedFormat?.formatId == format.formatId)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = format.ext.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    if (format.fps > 0) {
                                        Text(
                                            text = "${format.fps.toInt()}fps",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = format.displaySize,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = selectedFormat?.formatId == format.formatId,
                                onClick = { onFormatSelected(format) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outlineVariant,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced section (collapsible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = onToggleAdvanced,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = NeonOrange,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Advanced Options",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Extra yt-dlp commands
                    OutlinedTextField(
                        value = extraCommands,
                        onValueChange = onExtraCommandsChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. --embed-subs --sub-lang en", color = MaterialTheme.colorScheme.outline) },
                        label = { Text("Extra yt-dlp flags") },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonOrange.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = NeonOrange,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = NeonOrange,
                            unfocusedLabelColor = MaterialTheme.colorScheme.outline,
                        ),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Schedule download
                    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, tint = ElectricPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Schedule", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (scheduledAt > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    dateFormat.format(Date(scheduledAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricPurple,
                                )
                                IconButton(onClick = onClearSchedule, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    val cal = Calendar.getInstance()
                                                    cal.set(year, month, day, hour, minute, 0)
                                                    onScheduleSelected(cal.timeInMillis)
                                                },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                true,
                                            ).show()
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH),
                                    ).show()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = ElectricPurple),
                            ) {
                                Text("Set Time")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Download button
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    if (scheduledAt > 0) Icons.Filled.Schedule else Icons.Filled.Download,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (scheduledAt > 0) "Schedule Download" else "Start Download",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

// ── Playlist Bottom Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistBottomSheet(
    playlist: PlaylistInfo,
    selectedIndices: Set<Int>,
    selectedType: DownloadType,
    selectedContainer: String,
    onTypeSelected: (DownloadType) -> Unit,
    onContainerSelected: (String) -> Unit,
    onToggleEntry: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allSelected = selectedIndices.size == playlist.entries.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = ScrimDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // Playlist header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = ElectricPurple,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${playlist.totalCount} videos" + if (playlist.author.isNotBlank()) " • ${playlist.author}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Type + Container row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DownloadType.Video to "Video",
                    DownloadType.Audio to "Audio",
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == type,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val containers = when (selectedType) {
                    DownloadType.Video -> listOf("mp4", "mkv", "webm")
                    DownloadType.Audio -> listOf("mp3", "m4a", "opus")
                    DownloadType.Command -> listOf("")
                }
                containers.take(3).forEach { c ->
                    FilterChip(
                        selected = selectedContainer == c,
                        onClick = { onContainerSelected(c) },
                        label = { Text(c.uppercase(), style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricPurple.copy(alpha = 0.15f),
                            selectedLabelColor = ElectricPurple,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.outline,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedContainer == c,
                            selectedBorderColor = ElectricPurple.copy(alpha = 0.3f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Select all / Deselect all row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedIndices.size} of ${playlist.entries.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { if (allSelected) onDeselectAll() else onSelectAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(if (allSelected) "Deselect All" else "Select All")
                }
            }

            // Playlist entries
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
            ) {
                itemsIndexed(playlist.entries) { index, entry ->
                    val isSelected = index in selectedIndices
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                        ),
                        onClick = { onToggleEntry(index) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleEntry(index) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outlineVariant,
                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.size(20.dp),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(24.dp),
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title.ifBlank { "Track ${index + 1}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                )
                                if (entry.durationText.isNotBlank()) {
                                    Text(
                                        text = entry.durationText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download button
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedIndices.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                ),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Download ${selectedIndices.size} items",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
