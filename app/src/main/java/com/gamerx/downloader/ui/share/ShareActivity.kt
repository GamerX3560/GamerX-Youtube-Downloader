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
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val showFormatDialog: Boolean = false,
    val showContainerMenu: Boolean = false,
    val chaptersEnabled: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val thumbnailEnabled: Boolean = false,
    val sponsorBlockEnabled: Boolean = false,
    val saveDir: String = "",
    val freeSpace: String = "",
    val editedTitle: String = "",
    val editedAuthor: String = "",
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

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                _uiState.map { it.selectedType },
                settingsRepo.downloadDir,
                settingsRepo.audioDir
            ) { type, videoDir, audioDir ->
                val dir = if (type == DownloadType.Video) videoDir else audioDir
                val free = getFreeSpace(dir)
                dir to free
            }.collect { (dir, free) ->
                _uiState.value = _uiState.value.copy(saveDir = dir, freeSpace = free)
            }
        }
    }

    private fun getFreeSpace(path: String): String {
        return try {
            val stat = android.os.StatFs(path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            when {
                bytesAvailable >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytesAvailable / (1024f * 1024f * 1024f))
                else -> String.format("%.1f MB", bytesAvailable / (1024f * 1024f))
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

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
                        editedTitle = info.title,
                        editedAuthor = info.author,
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

    fun toggleFlag(flag: String) {
        val s = _uiState.value
        _uiState.value = when(flag) {
            "formatDialog" -> s.copy(showFormatDialog = !s.showFormatDialog)
            "containerMenu" -> s.copy(showContainerMenu = !s.showContainerMenu)
            "chapters" -> s.copy(chaptersEnabled = !s.chaptersEnabled)
            "subtitles" -> s.copy(subtitlesEnabled = !s.subtitlesEnabled)
            "thumbnail" -> s.copy(thumbnailEnabled = !s.thumbnailEnabled)
            "sponsorblock" -> s.copy(sponsorBlockEnabled = !s.sponsorBlockEnabled)
            else -> s
        }
    }

    fun setEditedTitle(t: String) {
        _uiState.value = _uiState.value.copy(editedTitle = t)
    }

    fun setEditedAuthor(a: String) {
        _uiState.value = _uiState.value.copy(editedAuthor = a)
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

            var targetFormat = _uiState.value.selectedFormat ?: FormatInfo()
            val container = _uiState.value.selectedContainer.ifBlank {
                if (type == DownloadType.Audio) "mp3" else "mp4"
            }

            var extraCommands = ""
            if (type == DownloadType.Video) {
                // Determine best audio formats for all languages available
                val bestAudioIds = info.audioFormats
                    .groupBy { it.language.ifBlank { "und" } }
                    .map { (_, formats) -> formats.maxByOrNull { it.tbr }?.formatId }
                    .filterNotNull()
                
                if (bestAudioIds.isNotEmpty()) {
                    val audioIdsStr = bestAudioIds.joinToString("+")
                    val videoId = if (targetFormat.formatId.isNotBlank()) targetFormat.formatId else "bestvideo*"
                    
                    targetFormat = targetFormat.copy(formatId = "$videoId+$audioIdsStr/best")
                    extraCommands = "--audio-multistreams"
                }
            }
            
            val flags = mutableListOf<String>()
            if (_uiState.value.thumbnailEnabled) flags.add("--write-thumbnail")
            if (_uiState.value.chaptersEnabled) flags.add("--embed-chapters")
            if (_uiState.value.subtitlesEnabled) flags.add("--write-subs --embed-subs")
            if (_uiState.value.sponsorBlockEnabled) flags.add("--sponsorblock-mark all")
            
            if (flags.isNotEmpty()) {
                extraCommands = (extraCommands + " " + flags.joinToString(" ")).trim()
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
                extraCommands = extraCommands,
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
                        onToggleFlag = { viewModel.toggleFlag(it) },
                        onTitleChange = { viewModel.setEditedTitle(it) },
                        onAuthorChange = { viewModel.setEditedAuthor(it) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContent(
    state: ShareUiState,
    onClose: () -> Unit,
    onTypeChange: (DownloadType) -> Unit,
    onFormatChange: (FormatInfo?) -> Unit,
    onContainerChange: (String) -> Unit,
    onToggleFlag: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
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
            modifier = Modifier.padding(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            when {
                state.isLoading -> {
                    Column(modifier = Modifier.padding(20.dp)) { SkeletonLoading() }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Failed to resolve URL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("Close")
                        }
                    }
                }

                state.videoInfo != null -> {
                    // ════════════════ HEADER ════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Download", style = MaterialTheme.typography.headlineSmall, fontSize = 20.sp)
                            Text("Configure download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ElevatedButton(
                            onClick = onDownload,
                            colors = ButtonDefaults.elevatedButtonColors(),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OK")
                        }
                    }

                    // ════════════════ TAB ROW ════════════════
                    val tabTitles = listOf("Audio", "Video", "Command")
                    val selectedTabIndex = when (state.selectedType) {
                        DownloadType.Audio -> 0
                        DownloadType.Video -> 1
                        else -> 1
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        ) {
                            tabTitles.forEachIndexed { index, title ->
                                val selected = selectedTabIndex == index
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = index != 2) {
                                            when (index) {
                                                0 -> onTypeChange(DownloadType.Audio)
                                                1 -> onTypeChange(DownloadType.Video)
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        title,
                                        color = when {
                                            selected -> MaterialTheme.colorScheme.primary
                                            index == 2 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (selected) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(40.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }

                    // ════════════════ SCROLLABLE CONTENT ════════════════
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Title ──
                        TextField(
                            value = state.editedTitle,
                            onValueChange = onTitleChange,
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Author + Container ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextField(
                                value = state.editedAuthor,
                                onValueChange = onAuthorChange,
                                label = { Text("Author") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            )

                            // Container ExposedDropdown
                            val containers = if (state.selectedType == DownloadType.Video)
                                listOf("mp4", "mkv", "webm") else listOf("mp3", "m4a", "opus", "wav")
                            ExposedDropdownMenuBox(
                                expanded = state.showContainerMenu,
                                onExpandedChange = { onToggleFlag("containerMenu") },
                                modifier = Modifier.weight(1f),
                            ) {
                                TextField(
                                    value = state.selectedContainer.uppercase().ifBlank { "Default" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Container") },
                                    singleLine = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.showContainerMenu) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                )
                                ExposedDropdownMenu(
                                    expanded = state.showContainerMenu,
                                    onDismissRequest = { onToggleFlag("containerMenu") },
                                ) {
                                    containers.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c.uppercase()) },
                                            onClick = {
                                                onContainerChange(c)
                                                onToggleFlag("containerMenu")
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Video Quality Label ──
                        Text(
                            text = if (state.selectedType == DownloadType.Video) "Video quality" else "Audio quality",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // ── Format Card (clickable, opens dialog) ──
                        FormatCard(
                            format = state.selectedFormat,
                            onClick = { onToggleFlag("formatDialog") }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Save Dir ──
                        TextField(
                            value = state.saveDir,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Save dir") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        )
                        Text(
                            text = "Free space: ${state.freeSpace}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // ════════════════ ADJUST VIDEO ════════════════
                        Text("Adjust video", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))

                        // Chip Row 1: Thumbnail, Chapters, Subtitles
                        AdjustChipRow {
                            AdjustChip("Thumbnail", Icons.Outlined.Image, state.thumbnailEnabled) { onToggleFlag("thumbnail") }
                            AdjustChip("Chapters", Icons.Outlined.Book, state.chaptersEnabled) { onToggleFlag("chapters") }
                            AdjustChip("Subtitles", Icons.Outlined.Subtitles, state.subtitlesEnabled) { onToggleFlag("subtitles") }
                        }
                        // Chip Row 2: Audio, Recode video, Live stream
                        AdjustChipRow {
                            AdjustChip("Audio", Icons.Outlined.MusicNote, false) { }
                            AdjustChip("Recode video", Icons.Outlined.VideoSettings, false) { }
                            AdjustChip("Live stream", Icons.Outlined.LiveTv, false) { }
                        }
                        // Chip Row 3: SponsorBlock, Filename template
                        AdjustChipRow {
                            AdjustChip("SponsorBlock", Icons.Outlined.AttachMoney, state.sponsorBlockEnabled) { onToggleFlag("sponsorblock") }
                            AdjustChip("Filename template", Icons.Outlined.Edit, false) { }
                        }
                        // Chip Row 4: Extra commands, Cut
                        AdjustChipRow {
                            AdjustChip("Extra commands", Icons.Outlined.Terminal, false) { }
                            AdjustChip("Cut", Icons.Outlined.ContentCut, false) { }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ════════════════ BOTTOM BAR ════════════════
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.videoInfo.website.ifBlank { "Link" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ════════════════ FORMAT SELECTION DIALOG ════════════════
            if (state.showFormatDialog && state.videoInfo != null) {
                val formats = if (state.selectedType == DownloadType.Video) {
                    state.videoInfo.videoFormats
                        .groupBy { "${it.resolution}-${it.fps.toInt()}-${it.ext}" }
                        .map { (_, g) -> g.maxByOrNull { it.tbr } ?: g.first() }
                        .sortedByDescending { it.resolution.substringAfter("x", "0").toIntOrNull() ?: it.tbr.toInt() }
                } else {
                    state.videoInfo.audioFormats.sortedByDescending { it.tbr }
                }

                AlertDialog(
                    onDismissRequest = { onToggleFlag("formatDialog") },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Format", style = MaterialTheme.typography.titleMedium)
                                Text("Select format", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                IconButton(onClick = {}) {
                                    Icon(Icons.Outlined.Sort, contentDescription = "Sort")
                                }
                                TextButton(onClick = { onToggleFlag("formatDialog") }) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("OK")
                                }
                            }
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = if (state.selectedType == DownloadType.Video) "Video" else "Audio",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                                item {
                                    FormatCard(format = null, isSelected = state.selectedFormat == null, onClick = {
                                        onFormatChange(null)
                                        onToggleFlag("formatDialog")
                                    })
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                items(formats) { format ->
                                    FormatCard(format = format, isSelected = format == state.selectedFormat, onClick = {
                                        onFormatChange(format)
                                        onToggleFlag("formatDialog")
                                    })
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════
// ── Adjust Chip Composables (horizontal scroll) ──
// ════════════════════════════════════════════════

@Composable
fun AdjustChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun AdjustChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isEnabled,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
    )
}

// ════════════════════════════════════════════════
// ── Format Card (YTDLnis format_item exact clone) ──
// ════════════════════════════════════════════════

@Composable
fun FormatCard(
    format: FormatInfo?,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (format == null) {
                // ── Auto Best ──
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("BEST", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Best Quality (Auto)", style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FormatPill("auto", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                // ── Ext Badge (60dp, like YTDLnis) ──
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(format.ext.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Format note: "1080P60 (1920X1080)"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val note = buildString {
                            val res = format.resolution
                            val height = res.substringAfter("x", "").ifBlank { res.filter { it.isDigit() } }
                            if (height.isNotBlank()) {
                                append("${height}P")
                                if (format.fps > 0) append("${format.fps.toInt()}")
                                append(" (${res.uppercase()})")
                            } else {
                                append(format.formatNote.ifBlank { "Audio" })
                            }
                        }
                        Text(
                            text = note,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "id: ${format.formatId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Pills row (horizontally scrollable)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        // Audio codec pill
                        if (!format.isAudioOnly && !format.isVideoOnly && format.acodec != "none") {
                            FormatPill("♪ ${format.acodec}", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        }
                        // Video codec pill
                        val codec = if (format.isAudioOnly || format.vcodec == "none") format.acodec else format.vcodec
                        if (codec.isNotBlank() && codec != "none") {
                            FormatPill(codec, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                        }
                        // File size pill
                        if (format.displaySize.isNotBlank() && format.displaySize != "Unknown") {
                            FormatPill(format.displaySize, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormatPill(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}
