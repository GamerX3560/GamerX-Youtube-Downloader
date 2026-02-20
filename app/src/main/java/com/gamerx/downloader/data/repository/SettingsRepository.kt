package com.gamerx.downloader.data.repository

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gamerx_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    // Keys
    private object Keys {
        val DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val AUDIO_DIR = stringPreferencesKey("audio_dir")
        val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_UPDATE_YTDLP = booleanPreferencesKey("auto_update_ytdlp")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "dark", "amoled", "auto"
        val FILENAME_TEMPLATE = stringPreferencesKey("filename_template")
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val DEFAULT_AUDIO_QUALITY = stringPreferencesKey("default_audio_quality")
        val SAVE_THUMBNAIL = booleanPreferencesKey("save_thumbnail")
        val COOKIE_FILE_PATH = stringPreferencesKey("cookie_file_path")
        val SPONSORBLOCK_ENABLED = booleanPreferencesKey("sponsorblock_enabled")
        val SPONSORBLOCK_CATEGORIES = stringPreferencesKey("sponsorblock_categories")
    }

    // Defaults
    private val defaultDownloadDir: String
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "GamerX"
        ).absolutePath

    private val defaultAudioDir: String
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "GamerX"
        ).absolutePath

    // Download Directory
    val downloadDir: Flow<String> = dataStore.data.map { it[Keys.DOWNLOAD_DIR] ?: defaultDownloadDir }
    suspend fun setDownloadDir(path: String) = dataStore.edit { it[Keys.DOWNLOAD_DIR] = path }

    // Audio Directory
    val audioDir: Flow<String> = dataStore.data.map { it[Keys.AUDIO_DIR] ?: defaultAudioDir }
    suspend fun setAudioDir(path: String) = dataStore.edit { it[Keys.AUDIO_DIR] = path }

    // Concurrent Downloads
    val concurrentDownloads: Flow<Int> = dataStore.data.map { it[Keys.CONCURRENT_DOWNLOADS] ?: 3 }
    suspend fun setConcurrentDownloads(count: Int) = dataStore.edit { it[Keys.CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5) }

    // Wi-Fi Only
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }
    suspend fun setWifiOnly(enabled: Boolean) = dataStore.edit { it[Keys.WIFI_ONLY] = enabled }

    // Auto-update yt-dlp
    val autoUpdateYtDlp: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_UPDATE_YTDLP] ?: true }
    suspend fun setAutoUpdateYtDlp(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_UPDATE_YTDLP] = enabled }

    // Theme Mode
    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "dark" }
    suspend fun setThemeMode(mode: String) = dataStore.edit { it[Keys.THEME_MODE] = mode }

    // Filename Template
    val filenameTemplate: Flow<String> = dataStore.data.map { it[Keys.FILENAME_TEMPLATE] ?: "%(title)s.%(ext)s" }
    suspend fun setFilenameTemplate(template: String) = dataStore.edit { it[Keys.FILENAME_TEMPLATE] = template }

    // Default Video Quality
    val defaultVideoQuality: Flow<String> = dataStore.data.map { it[Keys.DEFAULT_VIDEO_QUALITY] ?: "best" }
    suspend fun setDefaultVideoQuality(quality: String) = dataStore.edit { it[Keys.DEFAULT_VIDEO_QUALITY] = quality }

    // Default Audio Quality
    val defaultAudioQuality: Flow<String> = dataStore.data.map { it[Keys.DEFAULT_AUDIO_QUALITY] ?: "best" }
    suspend fun setDefaultAudioQuality(quality: String) = dataStore.edit { it[Keys.DEFAULT_AUDIO_QUALITY] = quality }

    // Save Thumbnail
    val saveThumbnail: Flow<Boolean> = dataStore.data.map { it[Keys.SAVE_THUMBNAIL] ?: false }
    suspend fun setSaveThumbnail(enabled: Boolean) = dataStore.edit { it[Keys.SAVE_THUMBNAIL] = enabled }

    // Cookie file path
    val cookieFilePath: Flow<String> = dataStore.data.map { it[Keys.COOKIE_FILE_PATH] ?: "" }
    suspend fun setCookieFilePath(path: String) = dataStore.edit { it[Keys.COOKIE_FILE_PATH] = path }

    // SponsorBlock
    val sponsorBlockEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SPONSORBLOCK_ENABLED] ?: false }
    suspend fun setSponsorBlockEnabled(enabled: Boolean) = dataStore.edit { it[Keys.SPONSORBLOCK_ENABLED] = enabled }

    // SponsorBlock categories (comma-separated: sponsor,selfpromo,intro,outro,interaction,music_offtopic,preview,filler)
    val sponsorBlockCategories: Flow<String> = dataStore.data.map { it[Keys.SPONSORBLOCK_CATEGORIES] ?: "sponsor,selfpromo" }
    suspend fun setSponsorBlockCategories(categories: String) = dataStore.edit { it[Keys.SPONSORBLOCK_CATEGORIES] = categories }
}
