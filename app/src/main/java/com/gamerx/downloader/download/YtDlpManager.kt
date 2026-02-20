package com.gamerx.downloader.download

import android.content.Context
import android.os.Environment
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.data.model.DownloadType
import com.gamerx.downloader.data.model.FormatInfo
import com.gamerx.downloader.data.model.PlaylistInfo
import com.gamerx.downloader.data.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class YtDlpManager(private val context: Context) {

    private val ytdl = YoutubeDL.getInstance()

    /**
     * Detect if URL is a playlist and extract entries
     */
    suspend fun getPlaylistInfo(url: String): Result<PlaylistInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--dump-json")
                addOption("--no-download")
            }

            val response = ytdl.execute(request)
            val output = response.out ?: return@withContext Result.success(null)
            val lines = output.trim().lines().filter { it.startsWith("{") }

            if (lines.size <= 1) {
                // Not a playlist (single video)
                return@withContext Result.success(null)
            }

            val entries = lines.mapNotNull { line ->
                try {
                    val json = JSONObject(line)
                    VideoInfo(
                        url = json.optString("url", json.optString("webpage_url", "")),
                        title = json.optString("title", "Unknown"),
                        author = json.optString("uploader", json.optString("channel", "")),
                        thumbnail = json.optString("thumbnail", json.optString("thumbnails", "")),
                        duration = json.optLong("duration", 0L),
                        website = json.optString("extractor_key", ""),
                    )
                } catch (_: Exception) { null }
            }

            if (entries.isEmpty()) return@withContext Result.success(null)

            // Try to get playlist title from first entry
            val firstJson = try { JSONObject(lines.first()) } catch (_: Exception) { null }
            val playlistTitle = firstJson?.optString("playlist_title", "") ?: ""
            val playlistUploader = firstJson?.optString("playlist_uploader", "") ?: ""

            Result.success(
                PlaylistInfo(
                    title = playlistTitle.ifBlank { "Playlist" },
                    author = playlistUploader,
                    url = url,
                    entries = entries,
                    totalCount = entries.size,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract video information and available formats from a URL
     */
    suspend fun getVideoInfo(url: String, cookieFile: String? = null): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--no-download")
                addOption("--no-playlist")
                addOption("--no-check-certificates")
                addOption("--socket-timeout", "10")
                addOption("--extractor-retries", "0")
                if (!cookieFile.isNullOrBlank()) {
                    addOption("--cookies", cookieFile)
                }
            }

            val response = ytdl.execute(request)
            val jsonStr = response.out

            if (jsonStr.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No response from yt-dlp"))
            }

            val json = JSONObject(jsonStr)
            val formats = parseFormats(json.optJSONArray("formats"))

            val info = VideoInfo(
                url = json.optString("webpage_url", url),
                title = json.optString("title", "Unknown"),
                author = json.optString("uploader", json.optString("channel", "")),
                thumbnail = json.optString("thumbnail", ""),
                duration = json.optLong("duration", 0L),
                website = json.optString("extractor_key", json.optString("extractor", "")),
                description = json.optString("description", ""),
                formats = formats,
                playlistTitle = json.optString("playlist_title", ""),
                playlistIndex = if (json.has("playlist_index")) json.optInt("playlist_index") else null,
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a video/audio with progress callback
     */
    suspend fun download(
        item: DownloadEntity,
        cookieFile: String? = null,
        sponsorBlockCategories: String? = null,
        progressCallback: (Int, String, String?) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = getDownloadDir(item)
            downloadDir.mkdirs()

            val request = YoutubeDLRequest(item.url).apply {
                addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")

                when (item.type) {
                    DownloadType.Audio -> {
                        addOption("-x") // extract audio
                        val container = item.container.ifBlank { "mp3" }
                        addOption("--audio-format", container)
                        if (item.format.formatId.isNotBlank()) {
                            addOption("-f", item.format.formatId)
                        } else {
                            addOption("-f", "bestaudio/best")
                        }
                        addOption("--audio-quality", "0") // best quality
                    }
                    DownloadType.Video -> {
                        if (item.format.formatId.isNotBlank()) {
                            addOption("-f", "${item.format.formatId}+bestaudio/best")
                        } else {
                            addOption("-f", "bestvideo+bestaudio/best")
                        }
                        val container = item.container.ifBlank { "mp4" }
                        addOption("--merge-output-format", container)
                    }
                    DownloadType.Command -> {
                        // Custom command — extra commands are already set
                    }
                }

                // Embed metadata
                addOption("--embed-metadata")

                // Write thumbnail
                if (item.saveThumbnail) {
                    addOption("--write-thumbnail")
                }

                // Extra commands from user
                if (item.extraCommands.isNotBlank()) {
                    item.extraCommands.split(" ").filter { it.isNotBlank() }.forEach { arg ->
                        addOption(arg)
                    }
                }

                // Cookie file
                if (!cookieFile.isNullOrBlank()) {
                    addOption("--cookies", cookieFile)
                }

                // SponsorBlock
                if (!sponsorBlockCategories.isNullOrBlank()) {
                    addOption("--sponsorblock-remove", sponsorBlockCategories)
                }

                // Custom filename
                if (item.customFileName.isNotBlank()) {
                    addOption("-o", "${downloadDir.absolutePath}/${item.customFileName}.%(ext)s")
                }

                // ── Network resilience flags ──
                addOption("--no-check-certificates")
                addOption("--socket-timeout", "30")
                addOption("--retries", "3")
                addOption("--fragment-retries", "3")
                addOption("--force-ipv4")
                addOption("--newline")  // one-line-per-update for clean parsing
                addOption("--no-part")  // atomic writes, avoids partial file issues
                addOption("--prefer-free-formats")  // better codec compatibility
            }

            var lastFilePath: String? = null

            val response = ytdl.execute(request) { progress, etaInSeconds, line ->
                val progressInt = progress.toInt().coerceIn(0, 100)
                val etaStr = if (etaInSeconds > 0) {
                    val mins = etaInSeconds / 60
                    val secs = etaInSeconds % 60
                    if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                } else ""
                val progressText = if (etaStr.isNotBlank()) "$progressInt% • ETA: $etaStr" else "$progressInt%"
                progressCallback(progressInt, progressText, line)

                // Try to extract file path from yt-dlp output
                if (line != null && line.contains("[download] Destination:")) {
                    lastFilePath = line.substringAfter("[download] Destination:").trim()
                }
                if (line != null && line.contains("[Merger]")) {
                    val match = Regex("Merging formats into \"(.+)\"").find(line)
                    if (match != null) lastFilePath = match.groupValues[1]
                }
            }

            // Find the actual downloaded file
            val filePath = lastFilePath ?: findLatestFile(downloadDir)?.absolutePath

            Result.success(filePath ?: downloadDir.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update yt-dlp to the latest version
     */
    suspend fun updateYtDlp(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = ytdl.updateYoutubeDL(context)
            Result.success(status?.name ?: "Unknown")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get yt-dlp version
     */
    fun getVersion(): String {
        return try {
            ytdl.version(context) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun parseFormats(formatsJson: JSONArray?): List<FormatInfo> {
        if (formatsJson == null) return emptyList()
        val formats = mutableListOf<FormatInfo>()

        for (i in 0 until formatsJson.length()) {
            val f = formatsJson.getJSONObject(i)
            val vcodec = f.optString("vcodec", "none")
            val acodec = f.optString("acodec", "none")

            formats.add(
                FormatInfo(
                    formatId = f.optString("format_id", ""),
                    formatNote = f.optString("format_note", ""),
                    ext = f.optString("ext", ""),
                    resolution = f.optString("resolution", ""),
                    fileSize = f.optLong("filesize", 0L),
                    fileSizeApprox = f.optLong("filesize_approx", 0L),
                    tbr = f.optDouble("tbr", 0.0),
                    vcodec = vcodec,
                    acodec = acodec,
                    asr = f.optInt("asr", 0),
                    fps = f.optDouble("fps", 0.0),
                    language = f.optString("language", ""),
                    isAudioOnly = vcodec == "none" && acodec != "none",
                    isVideoOnly = vcodec != "none" && acodec == "none",
                )
            )
        }

        return formats.sortedByDescending { it.tbr }
    }

    private fun getDownloadDir(item: DownloadEntity): File {
        if (item.downloadPath.isNotBlank()) {
            return File(item.downloadPath)
        }
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return when (item.type) {
            DownloadType.Audio -> File(base, "GamerX_Downloads/Music")
            DownloadType.Video -> File(base, "GamerX_Downloads/Video")
            DownloadType.Command -> File(base, "GamerX_Downloads/Video")
        }
    }

    private fun findLatestFile(dir: File): File? {
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
    }
}
