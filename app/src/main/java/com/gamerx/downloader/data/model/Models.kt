package com.gamerx.downloader.data.model

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    Queued,
    Active,
    Completed,
    Cancelled,
    Error,
    Paused,
    Processing
}

enum class DownloadType {
    Audio,
    Video,
    Command
}

@Serializable
data class FormatInfo(
    val formatId: String = "",
    val formatNote: String = "",
    val ext: String = "",
    val resolution: String = "",
    val fileSize: Long = 0L,
    val fileSizeApprox: Long = 0L,
    val tbr: Double = 0.0,
    val vcodec: String = "",
    val acodec: String = "",
    val asr: Int = 0,
    val fps: Double = 0.0,
    val language: String = "",
    val isAudioOnly: Boolean = false,
    val isVideoOnly: Boolean = false,
) {
    val displaySize: String
        get() {
            val size = if (fileSize > 0) fileSize else fileSizeApprox
            return when {
                size <= 0 -> "Unknown"
                size < 1024 -> "${size}B"
                size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)}KB"
                size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))}MB"
                else -> "${"%.2f".format(size / (1024.0 * 1024 * 1024))}GB"
            }
        }

    val displayQuality: String
        get() = when {
            formatNote.isNotBlank() -> formatNote
            resolution.isNotBlank() -> resolution
            isAudioOnly -> "${asr}Hz"
            else -> ext
        }
}

@Serializable
data class VideoInfo(
    val url: String = "",
    val title: String = "",
    val author: String = "",
    val thumbnail: String = "",
    val duration: Long = 0L, // seconds
    val website: String = "",
    val description: String = "",
    val formats: List<FormatInfo> = emptyList(),
    val playlistTitle: String = "",
    val playlistIndex: Int? = null,
) {
    val durationText: String
        get() {
            if (duration <= 0) return ""
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

    val audioFormats: List<FormatInfo>
        get() = formats.filter { it.isAudioOnly || (it.acodec != "none" && it.vcodec == "none") }

    val videoFormats: List<FormatInfo>
        get() = formats.filter { !it.isAudioOnly && it.vcodec != "none" }
}

@Serializable
data class PlaylistInfo(
    val title: String = "",
    val author: String = "",
    val url: String = "",
    val entries: List<VideoInfo> = emptyList(),
    val totalCount: Int = 0,
)
