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
            modifier = Modifier
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
                .padding(20.dp),
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ── Top Header (YTDLnis exactly) ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Download",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 22.sp
                                )
                                Text(
                                    text = "Adjust download",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.AccessTime, 
                                        contentDescription = "History",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = onDownload,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription=null, modifier = Modifier.size(16.dp), tint=MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Tabs ──
                        val types = listOf(DownloadType.Audio, DownloadType.Video)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            types.forEach { type ->
                                val selected = type == state.selectedType
                                Column(
                                    modifier = Modifier
                                        .clickable { onTypeChange(type) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = type.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (selected) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(20.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                            // Stub for Command tab
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Command",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f),
                                )
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Title & Author Boxes ──
                        OutlinedTextField(
                            value = state.editedTitle,
                            onValueChange = onTitleChange,
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.editedAuthor,
                                onValueChange = onAuthorChange,
                                label = { Text("Author") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )

                            // Container Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                val containers = if (state.selectedType == DownloadType.Video) {
                                    listOf("mp4", "mkv", "webm")
                                } else {
                                    listOf("mp3", "m4a", "opus", "wav")
                                }
                                OutlinedTextField(
                                    value = state.selectedContainer.ifBlank { "Default" },
                                    onValueChange = {},
                                    label = { Text("Container") },
                                    readOnly = true,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().clickable { onToggleFlag("containerMenu") },
                                    trailingIcon = {
                                        Icon(Icons.Outlined.ArrowDropDown, contentDescription=null, modifier = Modifier.clickable { onToggleFlag("containerMenu") })
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                DropdownMenu(
                                    expanded = state.showContainerMenu,
                                    onDismissRequest = { onToggleFlag("containerMenu") },
                                ) {
                                    containers.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c.uppercase()) },
                                            onClick = {
                                                onContainerChange(c)
                                                onToggleFlag("containerMenu")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Video Quality ──
                        Text(
                            text = if (state.selectedType == DownloadType.Video) "Video quality" else "Audio quality",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,

                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Format display button (opens dialog)
                        FormatRow(
                            format = state.selectedFormat,
                            isSelected = true,
                            onClick = { onToggleFlag("formatDialog") }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Save Dir ──
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Save dir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.saveDir, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Free space: ${state.freeSpace}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Adjust Video Feature Grid ──
                        Text(
                            text = "Adjust video",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,

                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 1
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridToggleButton("Thumbnail", Icons.Outlined.Image, state.thumbnailEnabled, onClick = { onToggleFlag("thumbnail") }, modifier = Modifier.weight(1f))
                            GridToggleButton("Chapters", Icons.Outlined.MenuBook, state.chaptersEnabled, onClick = { onToggleFlag("chapters") }, badge = "1", modifier = Modifier.weight(1f))
                            GridToggleButton("Subtitles", Icons.Outlined.Subtitles, state.subtitlesEnabled, onClick = { onToggleFlag("subtitles") }, badge = "1", modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Row 2
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridToggleButton("Audio", Icons.Outlined.MusicNote, false, onClick = { }, modifier = Modifier.weight(1f))
                            GridToggleButton("Recode video", Icons.Outlined.SettingsApplications, false, onClick = { }, modifier = Modifier.weight(1f))
                            GridToggleButton("Live stream", Icons.Outlined.LiveTv, false, onClick = { }, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Row 3
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridToggleButton("SponsorBlock", Icons.Outlined.AttachMoney, state.sponsorBlockEnabled, onClick = { onToggleFlag("sponsorblock") }, badge = "1", modifier = Modifier.weight(1.2f))
                            GridToggleButton("Filename template", Icons.Outlined.Edit, false, onClick = { }, modifier = Modifier.weight(2f))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // ── Interactive Format Selection Dialog ──
            if (state.showFormatDialog && state.videoInfo != null) {
                val formats = if (state.selectedType == DownloadType.Video) {
                    state.videoInfo.videoFormats
                        .groupBy { "${it.resolution}-${it.fps.toInt()}-${it.ext}" }
                        .map { (_, formatsInGroup) -> formatsInGroup.maxByOrNull { it.tbr } ?: formatsInGroup.first() }
                        .sortedByDescending { it.resolution.substringBefore("x", "0").toIntOrNull() ?: it.tbr.toInt() }
                } else {
                    state.videoInfo.audioFormats.sortedByDescending { it.tbr }
                }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { onToggleFlag("formatDialog") },
                    title = {
                        Text("Format Selection", style = MaterialTheme.typography.titleMedium)
                    },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            item {
                                FormatRow(
                                    format = null,
                                    isSelected = state.selectedFormat == null,
                                    onClick = {
                                        onFormatChange(null)
                                        onToggleFlag("formatDialog")
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                            items(formats) { format ->
                                FormatRow(
                                    format = format,
                                    isSelected = format == state.selectedFormat,
                                    onClick = {
                                        onFormatChange(format)
                                        onToggleFlag("formatDialog")
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { onToggleFlag("formatDialog") }) {
                            Text("OK")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    textContentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Shared UI Grid Items ──

@Composable
fun GridToggleButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 12.sp
                )
            }
            if (badge != null) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-8).dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Format Row ──

@Composable
fun FormatRow(
    format: FormatInfo?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            if (format == null) {
                // Auto format
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Best Quality (Auto)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "yt-dlp selects optimal format",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Codec/Ext badge on the left (like YTDLnis solid blue badge)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = format.ext.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    // Title row — YTDLnis style: "1080P60 (1920X1080)"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val title = buildString {
                            // Convert "1920x1080" -> height "1080", append P and fps
                            val res = format.resolution
                            val height = res.substringAfter("x", "").ifBlank { res.filter { it.isDigit() } }
                            if (height.isNotBlank()) {
                                append("${height}P")
                                if (format.fps > 0) append("${format.fps.toInt()}")
                                append(" (${res.uppercase()})")
                            } else if (format.formatNote.isNotBlank()) {
                                append(format.formatNote)
                            } else {
                                append("Audio")
                            }
                        }
                        
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "id: ${format.formatId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Detail pills row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Audio pill
                        if (!format.isAudioOnly && !format.isVideoOnly) {
                             Surface(
                                 shape = RoundedCornerShape(4.dp),
                                 color = MaterialTheme.colorScheme.primary.copy(alpha=0.15f)
                             ) {
                                 Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                     Icon(Icons.Outlined.MusicNote, contentDescription=null, modifier = Modifier.size(12.dp), tint=MaterialTheme.colorScheme.primary)
                                     Spacer(modifier = Modifier.width(2.dp))
                                     Text(if (format.acodec != "none") format.acodec else "audio", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                 }
                             }
                        }
                        
                        // Video codec pill
                        val codec = if (format.isAudioOnly || format.vcodec == "none") format.acodec else format.vcodec
                        if (codec.isNotBlank() && codec != "none") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = codec,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Size pill
                        if (format.displaySize.isNotBlank() && format.displaySize != "Unknown") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha=0.2f)
                            ) {
                                Text(
                                    text = format.displaySize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
