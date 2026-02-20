package com.gamerx.downloader.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo
import com.gamerx.downloader.data.model.VideoInfo
import com.gamerx.downloader.data.repository.DownloadRepository
import com.gamerx.downloader.data.repository.SettingsRepository
import com.gamerx.downloader.download.DownloadWorker
import com.gamerx.downloader.download.YtDlpManager
import com.gamerx.downloader.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── State ──

data class ShareUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val videoInfo: VideoInfo? = null,
    val downloadStarted: Boolean = false,
    val selectedType: DownloadType = DownloadType.Video,
    val selectedFormat: FormatInfo? = null,  // null = "Best" auto-selection
    val selectedContainer: String = "",      // mp4, mkv, webm, mp3, m4a, opus
)

// ── ViewModel ──

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val ytDlpManager: YtDlpManager,
    private val repository: DownloadRepository,
    private val settingsRepo: SettingsRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private var currentUrl: String? = null

    fun handleIntent(intent: Intent) {
        if (currentUrl != null) return

        val rawText = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString?.toString()
            else -> null
        }
        
        val url = rawText?.let { text ->
            Regex("(https?://\\S+)").find(text)?.value
        }

        if (url.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "No URL found")
            return
        }

        currentUrl = url
        fetchVideoInfo(url)
    }

    private fun fetchVideoInfo(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = ytDlpManager.getVideoInfo(url)
            result.fold(
                onSuccess = { info ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videoInfo = info,
                        selectedContainer = "mp4",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Failed to fetch info")
                }
            )
        }
    }

    fun setSelectedType(type: DownloadType) {
        _uiState.value = _uiState.value.copy(
            selectedType = type,
            selectedFormat = null,
            selectedContainer = if (type == DownloadType.Audio) "mp3" else "mp4",
        )
    }

    fun setSelectedFormat(format: FormatInfo?) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    fun setSelectedContainer(container: String) {
        _uiState.value = _uiState.value.copy(selectedContainer = container)
    }

    fun startDownload() {
        val info = _uiState.value.videoInfo ?: return
        val url = currentUrl ?: return
        val type = _uiState.value.selectedType

        viewModelScope.launch {
            val downloadDir = if (type == DownloadType.Audio) {
                settingsRepo.audioDir.first()
            } else {
                settingsRepo.downloadDir.first()
            }
            val filenameTpl = settingsRepo.filenameTemplate.first()
            val saveThumb = settingsRepo.saveThumbnail.first()

            val targetFormat = _uiState.value.selectedFormat ?: FormatInfo()
            val container = _uiState.value.selectedContainer.ifBlank {
                if (type == DownloadType.Audio) "mp3" else "mp4"
            }

            val download = DownloadEntity(
                url = url,
                title = info.title,
                author = info.author,
                thumbnail = info.thumbnail,
                duration = info.duration,
                type = type,
                format = targetFormat,
                container = container,
                downloadPath = downloadDir,
                status = com.gamerx.downloader.data.model.DownloadStatus.Queued,
                website = info.website.ifBlank { "Web" },
                saveThumbnail = saveThumb,
            )

            val id = repository.insert(download)

            val inputData = Data.Builder()
                .putLong("download_id", id)
                .putString("filename_template", filenameTpl)
                .putBoolean("save_thumbnail", saveThumb)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .addTag("download_$id")
                .build()

            workManager.enqueue(workRequest)

            _uiState.value = _uiState.value.copy(downloadStarted = true)
        }
    }
}

// ── Activity ──

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ShareViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val themeMode by settingsRepo.themeMode.collectAsStateWithLifecycle(initialValue = "dark")

            LaunchedEffect(intent) {
                viewModel.handleIntent(intent)
            }

            LaunchedEffect(state.downloadStarted) {
                if (state.downloadStarted) {
                    Toast.makeText(this@ShareActivity, "Download started", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            GamerXTheme(themeMode = themeMode, transparentBars = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ShareContent(
                        state = state,
                        onClose = { finish() },
                        onTypeChange = { viewModel.setSelectedType(it) },
                        onFormatChange = { viewModel.setSelectedFormat(it) },
                        onContainerChange = { viewModel.setSelectedContainer(it) },
                        onDownload = { viewModel.startDownload() },
                    )
                }
            }
        }
    }
}

// ── Shimmer ──

@Composable
fun ShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    return Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim),
    )
}

@Composable
fun SkeletonLoading() {
    val shimmer = ShimmerBrush()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(shimmer)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) {
                Box(modifier = Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(shimmer))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
    }
}

// ── Main Content ──

@Composable
fun ShareContent(
    state: ShareUiState,
    onClose: () -> Unit,
    onTypeChange: (DownloadType) -> Unit,
    onFormatChange: (FormatInfo?) -> Unit,
    onContainerChange: (String) -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(enabled = false) {},
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.isLoading -> SkeletonLoading()

                state.error != null -> {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Failed to resolve URL",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                state.videoInfo != null -> {
                    val info = state.videoInfo

                    // ── Thumbnail + Title + Duration ──
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Box {
                            AsyncImage(
                                model = info.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                            )
                            if (info.durationText.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                ) {
                                    Text(
                                        text = info.durationText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (info.website.isNotBlank()) {
                                Text(
                                    text = info.website,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Type Tabs (Video / Audio) ──
                    val types = listOf(DownloadType.Video, DownloadType.Audio)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        types.forEach { type ->
                            val selected = type == state.selectedType
                            val formatCount = if (type == DownloadType.Video) info.videoFormats.size else info.audioFormats.size
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { onTypeChange(type) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (type == DownloadType.Video) Icons.Outlined.Videocam
                                        else Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = type.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (formatCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Text(
                                                text = "$formatCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Container Selector ──
                    val containers = if (state.selectedType == DownloadType.Video) {
                        listOf("mp4", "mkv", "webm")
                    } else {
                        listOf("mp3", "m4a", "opus", "wav")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Format:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        containers.forEach { container ->
                            val selected = container == state.selectedContainer
                            FilterChip(
                                selected = selected,
                                onClick = { onContainerChange(container) },
                                label = {
                                    Text(
                                        text = container.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    enabled = true,
                                    selected = selected,
                                ),
                                modifier = Modifier.height(30.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Format Selection List ──
                    val formats = if (state.selectedType == DownloadType.Video) {
                        info.videoFormats.sortedByDescending { 
                            it.resolution.substringBefore("x", "0").toIntOrNull() ?: it.tbr.toInt()
                        }
                    } else {
                        info.audioFormats.sortedByDescending { it.tbr }
                    }

                    // "Best" auto option + real formats
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        // Best auto option
                        FormatRow(
                            label = "Best Quality (Auto)",
                            detail = "yt-dlp selects optimal format",
                            size = "",
                            isSelected = state.selectedFormat == null,
                            onClick = { onFormatChange(null) },
                        )

                        if (formats.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )

                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(formats) { format ->
                                    val label = if (state.selectedType == DownloadType.Video) {
                                        buildString {
                                            if (format.resolution.isNotBlank()) append(format.resolution)
                                            else if (format.formatNote.isNotBlank()) append(format.formatNote)
                                            if (format.fps > 0) append(" • ${format.fps.toInt()}fps")
                                        }
                                    } else {
                                        buildString {
                                            if (format.formatNote.isNotBlank()) append(format.formatNote)
                                            else if (format.tbr > 0) append("${format.tbr.toInt()}kbps")
                                            if (format.asr > 0) append(" • ${format.asr}Hz")
                                        }
                                    }
                                    val detail = buildString {
                                        if (format.ext.isNotBlank()) append(format.ext.uppercase())
                                        val codec = if (state.selectedType == DownloadType.Video) format.vcodec else format.acodec
                                        if (codec.isNotBlank() && codec != "none") append(" • $codec")
                                        if (format.language.isNotBlank() && format.language != "und") append(" • ${format.language}")
                                    }
                                    FormatRow(
                                        label = label.ifBlank { format.formatId },
                                        detail = detail,
                                        size = format.displaySize,
                                        isSelected = format == state.selectedFormat,
                                        onClick = { onFormatChange(format) },
                                    )
                                }
                            }
                        } else {
                            // No formats detected — show fallback message
                            Text(
                                text = "No individual formats detected. \"Best\" will be used.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Download Button ──
                    Button(
                        onClick = onDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.selectedType == DownloadType.Video) "Download Video" else "Download Audio",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── Format Row ──

@Composable
fun FormatRow(
    label: String,
    detail: String,
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection indicator
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (size.isNotBlank() && size != "Unknown") {
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
