package com.gamerx.downloader.ui.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gamerx.downloader.data.repository.SettingsRepository
import com.gamerx.downloader.download.YtDlpManager
import com.gamerx.downloader.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreUiState(
    val ytDlpVersion: String = "Loading...",
    val isUpdating: Boolean = false,
    val updateResult: String? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val ytDlpManager: YtDlpManager,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    // Settings flows
    val downloadDir = settingsRepo.downloadDir.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val audioDir = settingsRepo.audioDir.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val concurrentDownloads = settingsRepo.concurrentDownloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val wifiOnly = settingsRepo.wifiOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoUpdateYtDlp = settingsRepo.autoUpdateYtDlp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val themeMode = settingsRepo.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")
    val filenameTemplate = settingsRepo.filenameTemplate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "%(title)s.%(ext)s")
    val defaultVideoQuality = settingsRepo.defaultVideoQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "best")
    val defaultAudioQuality = settingsRepo.defaultAudioQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "best")
    val saveThumbnail = settingsRepo.saveThumbnail.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val cookieFilePath = settingsRepo.cookieFilePath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val sponsorBlockEnabled = settingsRepo.sponsorBlockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val sponsorBlockCategories = settingsRepo.sponsorBlockCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "sponsor,selfpromo")

    init {
        _uiState.value = _uiState.value.copy(ytDlpVersion = ytDlpManager.getVersion())
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true, updateResult = null)
            val result = ytDlpManager.updateYtDlp()
            result.fold(
                onSuccess = { status ->
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateResult = "Update: $status",
                        ytDlpVersion = ytDlpManager.getVersion(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateResult = "Failed: ${error.message}",
                    )
                },
            )
        }
    }

    fun setConcurrentDownloads(count: Int) { viewModelScope.launch { settingsRepo.setConcurrentDownloads(count) } }
    fun setWifiOnly(enabled: Boolean) { viewModelScope.launch { settingsRepo.setWifiOnly(enabled) } }
    fun setAutoUpdateYtDlp(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutoUpdateYtDlp(enabled) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { settingsRepo.setThemeMode(mode) } }
    fun setFilenameTemplate(tpl: String) { viewModelScope.launch { settingsRepo.setFilenameTemplate(tpl) } }
    fun setDefaultVideoQuality(q: String) { viewModelScope.launch { settingsRepo.setDefaultVideoQuality(q) } }
    fun setDefaultAudioQuality(q: String) { viewModelScope.launch { settingsRepo.setDefaultAudioQuality(q) } }
    fun setSaveThumbnail(enabled: Boolean) { viewModelScope.launch { settingsRepo.setSaveThumbnail(enabled) } }
    fun setCookieFilePath(path: String) { viewModelScope.launch { settingsRepo.setCookieFilePath(path) } }
    fun clearCookieFile() { viewModelScope.launch { settingsRepo.setCookieFilePath("") } }
    fun setSponsorBlockEnabled(enabled: Boolean) { viewModelScope.launch { settingsRepo.setSponsorBlockEnabled(enabled) } }
    fun setSponsorBlockCategories(categories: String) { viewModelScope.launch { settingsRepo.setSponsorBlockCategories(categories) } }
    fun setDownloadDir(path: String) { viewModelScope.launch { settingsRepo.setDownloadDir(path) } }
    fun setAudioDir(path: String) { viewModelScope.launch { settingsRepo.setAudioDir(path) } }
}

@Composable
fun MoreScreen(
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadDir by viewModel.downloadDir.collectAsStateWithLifecycle()
    val audioDir by viewModel.audioDir.collectAsStateWithLifecycle()
    val concurrentDl by viewModel.concurrentDownloads.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdateYtDlp.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val filenameTemplate by viewModel.filenameTemplate.collectAsStateWithLifecycle()
    val videoQuality by viewModel.defaultVideoQuality.collectAsStateWithLifecycle()
    val audioQuality by viewModel.defaultAudioQuality.collectAsStateWithLifecycle()
    val saveThumbnail by viewModel.saveThumbnail.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showFilenameDialog by remember { mutableStateOf(false) }
    var showConcurrentDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val cookiePath by viewModel.cookieFilePath.collectAsStateWithLifecycle()
    val sbEnabled by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sbCategories by viewModel.sponsorBlockCategories.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val cookieFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val cookieFile = java.io.File(context.filesDir, "cookies.txt")
                inputStream?.use { input ->
                    cookieFile.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.setCookieFilePath(cookieFile.absolutePath)
            } catch (_: Exception) { }
        }
    }

    val videoDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setDownloadDir(it.toString()) }
    }

    val audioDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setAudioDir(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        // App info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            onClick = { showAboutDialog = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NeonCyan, ElectricPurple),
                                start = Offset.Zero,
                                end = Offset(100f, 100f),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "GX",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "GamerX Downloader",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // yt-dlp Engine
        SectionHeader(title = "yt-dlp Engine")

        SettingsItem(
            icon = Icons.Filled.Terminal,
            title = "yt-dlp Version",
            subtitle = state.ytDlpVersion,
            iconTint = NeonCyan,
        )
        SettingsItem(
            icon = Icons.Filled.SystemUpdate,
            title = "Update yt-dlp",
            subtitle = if (state.isUpdating) "Updating..." else (state.updateResult ?: "Check for updates"),
            iconTint = NeonGreen,
            onClick = { if (!state.isUpdating) viewModel.updateYtDlp() },
            showProgress = state.isUpdating,
        )
        SettingsToggleItem(
            icon = Icons.Outlined.AutoMode,
            title = "Auto-update on Launch",
            subtitle = "Update yt-dlp when app starts",
            iconTint = NeonGreen,
            checked = autoUpdate,
            onCheckedChange = { viewModel.setAutoUpdateYtDlp(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Downloads
        SectionHeader(title = "Downloads")

        SettingsItem(
            icon = Icons.Outlined.FolderOpen,
            title = "Video Download Location",
            subtitle = downloadDir.ifBlank { "GamerX_Downloads/Video" },
            iconTint = ElectricPurple,
            onClick = { videoDirLauncher.launch(null) },
        )
        SettingsItem(
            icon = Icons.Outlined.MusicNote,
            title = "Audio Download Location",
            subtitle = audioDir.ifBlank { "GamerX_Downloads/Music" },
            iconTint = ElectricPurple,
            onClick = { audioDirLauncher.launch(null) },
        )
        SettingsItem(
            icon = Icons.Outlined.Speed,
            title = "Concurrent Downloads",
            subtitle = "$concurrentDl simultaneous",
            iconTint = NeonOrange,
            onClick = { showConcurrentDialog = true },
        )
        SettingsToggleItem(
            icon = Icons.Outlined.Wifi,
            title = "Wi-Fi Only",
            subtitle = "Download only on Wi-Fi",
            iconTint = NeonCyan,
            checked = wifiOnly,
            onCheckedChange = { viewModel.setWifiOnly(it) },
        )
        SettingsToggleItem(
            icon = Icons.Outlined.Image,
            title = "Save Thumbnails",
            subtitle = "Save video thumbnails alongside downloads",
            iconTint = ElectricPurple,
            checked = saveThumbnail,
            onCheckedChange = { viewModel.setSaveThumbnail(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quality & Format
        SectionHeader(title = "Quality & Format")

        SettingsItem(
            icon = Icons.Outlined.HighQuality,
            title = "Default Video Quality",
            subtitle = videoQuality.replaceFirstChar { it.uppercase() },
            iconTint = NeonCyan,
            onClick = { showVideoQualityDialog = true },
        )
        SettingsItem(
            icon = Icons.Outlined.Headphones,
            title = "Default Audio Quality",
            subtitle = audioQuality.replaceFirstChar { it.uppercase() },
            iconTint = ElectricPurple,
            onClick = { showAudioQualityDialog = true },
        )
        SettingsItem(
            icon = Icons.Outlined.TextFields,
            title = "Filename Template",
            subtitle = filenameTemplate,
            iconTint = NeonOrange,
            onClick = { showFilenameDialog = true },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Appearance
        SectionHeader(title = "Appearance")

        SettingsItem(
            icon = Icons.Outlined.DarkMode,
            title = "Theme",
            subtitle = when (themeMode) {
                "amoled" -> "AMOLED Black"
                "light" -> "Light"
                "midnight" -> "Midnight Blue"
                "ocean" -> "Ocean"
                "system" -> "Follow System"
                else -> "Dark"
            },
            iconTint = ElectricPurple,
            onClick = { showThemeDialog = true },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // SponsorBlock
        SectionHeader(title = "SponsorBlock")

        SettingsToggleItem(
            icon = Icons.Outlined.Block,
            title = "Enable SponsorBlock",
            subtitle = "Auto-remove sponsored segments (YouTube)",
            iconTint = NeonGreen,
            checked = sbEnabled,
            onCheckedChange = { viewModel.setSponsorBlockEnabled(it) },
        )

        if (sbEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Categories to remove", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    val allCats = listOf(
                        "sponsor" to "Sponsor",
                        "selfpromo" to "Self Promo",
                        "intro" to "Intro",
                        "outro" to "Outro",
                        "interaction" to "Interaction",
                        "music_offtopic" to "Music (Off-topic)",
                        "preview" to "Preview",
                        "filler" to "Filler",
                    )
                    val enabledCats = sbCategories.split(",").map { it.trim() }.toSet()

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        allCats.forEach { (key, label) ->
                            val isOn = key in enabledCats
                            FilterChip(
                                selected = isOn,
                                onClick = {
                                    val newCats = if (isOn) enabledCats - key else enabledCats + key
                                    viewModel.setSponsorBlockCategories(newCats.joinToString(","))
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreen.copy(alpha = 0.15f),
                                    selectedLabelColor = NeonGreen,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isOn,
                                    selectedBorderColor = NeonGreen.copy(alpha = 0.3f),
                                    borderColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cookies
        SectionHeader(title = "Authentication")

        if (cookiePath.isNotBlank()) {
            SettingsItem(
                icon = Icons.Outlined.Cookie,
                title = "Cookie File",
                subtitle = "cookies.txt imported ✓",
                iconTint = NeonOrange,
                onClick = { viewModel.clearCookieFile() },
            )
            SettingsItem(
                icon = Icons.Outlined.DeleteOutline,
                title = "Clear Cookies",
                subtitle = "Remove imported cookie file",
                iconTint = NeonRed,
                onClick = { viewModel.clearCookieFile() },
            )
        } else {
            SettingsItem(
                icon = Icons.Outlined.Cookie,
                title = "Import Cookies",
                subtitle = "Import browser cookies for authenticated downloads",
                iconTint = NeonOrange,
                onClick = { cookieFileLauncher.launch("*/*") },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // About
        SectionHeader(title = "About")

        SettingsItem(
            icon = Icons.Outlined.Code,
            title = "Source Code",
            subtitle = "Built with ❤️",
            iconTint = NeonCyan,
            onClick = { showAboutDialog = true },
        )
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "Licenses",
            subtitle = "Open source licenses",
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Dialogs
    if (showThemeDialog) {
        SelectionDialog(
            title = "Theme",
            options = listOf(
                "dark" to "Dark",
                "amoled" to "AMOLED Black",
                "light" to "Light",
                "midnight" to "Midnight Blue",
                "ocean" to "Ocean",
                "system" to "Follow System"
            ),
            selected = themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showVideoQualityDialog) {
        SelectionDialog(
            title = "Default Video Quality",
            options = listOf(
                "best" to "Best Available",
                "2160" to "4K (2160p)",
                "1080" to "Full HD (1080p)",
                "720" to "HD (720p)",
                "480" to "SD (480p)",
                "360" to "Low (360p)",
            ),
            selected = videoQuality,
            onSelect = { viewModel.setDefaultVideoQuality(it); showVideoQualityDialog = false },
            onDismiss = { showVideoQualityDialog = false },
        )
    }

    if (showAudioQualityDialog) {
        SelectionDialog(
            title = "Default Audio Quality",
            options = listOf(
                "best" to "Best Available",
                "320" to "320 kbps",
                "256" to "256 kbps",
                "192" to "192 kbps",
                "128" to "128 kbps",
            ),
            selected = audioQuality,
            onSelect = { viewModel.setDefaultAudioQuality(it); showAudioQualityDialog = false },
            onDismiss = { showAudioQualityDialog = false },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NeonCyan, ElectricPurple),
                                start = Offset.Zero,
                                end = Offset(100f, 100f),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "GX",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GamerX Downloader", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Powerful media downloader powered by yt-dlp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "GamerX",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Made by Mangesh Choudhary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Close") }
            },
        )
    }

    if (showFilenameDialog) {
        var editTemplate by remember { mutableStateOf(filenameTemplate) }
        AlertDialog(
            onDismissRequest = { showFilenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Filename Template") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTemplate,
                        onValueChange = { editTemplate = it },
                        label = { Text("Template") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Available tokens: %(title)s, %(id)s, %(ext)s, %(uploader)s, %(upload_date)s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.setFilenameTemplate(editTemplate); showFilenameDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFilenameDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline),
                ) { Text("Cancel") }
            },
        )
    }

    if (showConcurrentDialog) {
        var sliderValue by remember { mutableStateOf(concurrentDl.toFloat()) }
        AlertDialog(
            onDismissRequest = { showConcurrentDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Concurrent Downloads") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${sliderValue.toInt()} simultaneous downloads",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text("5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.setConcurrentDownloads(sliderValue.toInt()); showConcurrentDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConcurrentDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (value == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        fontSize = 13.sp,
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    showProgress: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    }
}
